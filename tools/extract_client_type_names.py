#!/usr/bin/env python3
"""Read type names and optional member tables from the supplied 7.1.0 client.

This profile is tied to the exact executable and metadata SHA-256 hashes below.
It recovers names, field type references, method addresses and optional literal
packet ID getters, not a complete protobuf schema or dump.cs. Message names and
wire fields still require verification.
The source client files are never modified.
"""

import argparse
import functools
import hashlib
import json
import struct
from pathlib import Path


BINARY_SHA256 = "7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e"
METADATA_SHA256 = "469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161"
STARTUP_SHA256 = "90848967d3f9432e7a13caffcec999bf457ac3be1f2ae86336443a63acbaadcf"
HEADER_VA = 0x1427D5CB0
BODY_OFFSET = 0x210
TYPE_STRIDE = 70
METADATA_REGISTRATION_VA = 0x142871B68
CODE_REGISTRATION_VA = 0x1422DF450
MASK64 = (1 << 64) - 1
TYPE_KINDS = {
    1: "void", 2: "bool", 3: "char", 4: "int8", 5: "uint8", 6: "int16",
    7: "uint16", 8: "int32", 9: "uint32", 10: "int64", 11: "uint64",
    12: "float", 13: "double", 14: "string", 15: "ptr", 16: "byref",
    17: "valuetype", 18: "class", 19: "var", 20: "array", 21: "genericinst",
    22: "typedbyref", 24: "intptr", 25: "uintptr", 27: "fnptr",
    28: "object", 29: "szarray", 30: "mvar",
}


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def read_verified(path: Path, expected: str) -> bytes:
    data = path.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise ValueError(f"Unsupported client file: {path.name}; SHA-256 {actual}")
    return data


def pe_file_offset(binary: bytes, address: int, size: int = 1) -> int:
    pe = u32(binary, 0x3C)
    if binary[:2] != b"MZ" or binary[pe:pe + 4] != b"PE\0\0":
        raise ValueError("Expected a Windows PE executable")
    section_count = struct.unpack_from("<H", binary, pe + 6)[0]
    optional_size = struct.unpack_from("<H", binary, pe + 20)[0]
    image_base = struct.unpack_from("<Q", binary, pe + 48)[0]
    for index in range(section_count):
        entry = pe + 24 + optional_size + index * 40
        _, rva, raw_size, raw_offset = struct.unpack_from("<IIII", binary, entry + 8)
        start = image_base + rva
        if start <= address and address + size <= start + raw_size:
            return raw_offset + address - start
    raise ValueError(f"Address {address:#x} is not backed by a PE section")


class MetadataNames:
    """The observed metadata name decoder at 0x140510060."""

    def __init__(self, metadata: bytes, strings_offset: int):
        self.metadata = metadata
        self.strings_offset = strings_offset

    @functools.lru_cache(maxsize=None)
    def decode(self, index: int) -> str:
        if index == 0xFFFFFFFF:
            return ""
        length, offset = index >> 24, index & 0xFFFFFF
        start = self.strings_offset + offset
        padded_length = (length + 7) // 8 * 8
        if start + padded_length > len(self.metadata):
            raise ValueError(f"Name {index:#x} exceeds the metadata file")
        key = ((offset * 0xB33E40427D5668C0 + 0x6D8C4AAB00FE8E27) & MASK64) ^ 0x30EE5B43130FE0CD
        plain = bytearray()
        for block in range(padded_length // 8):
            cipher = struct.unpack_from("<Q", self.metadata, start + block * 8)[0]
            plain.extend((cipher ^ ((key + block * 0x0889EEEA326AFB36) & MASK64)).to_bytes(8, "little"))
        value = plain[:length].decode("utf-8")
        if any(ord(char) < 32 for char in value):
            raise ValueError(f"Name {index:#x} contains a control character")
        return value


def recover_types(binary: bytes, metadata: bytes) -> tuple[list[dict], dict]:
    header = pe_file_offset(binary, HEADER_VA, BODY_OFFSET)
    if binary[header:header + 4] != b"MHY\0" or metadata[:4] != b"MHY\0":
        raise ValueError("Expected the observed MHY metadata headers")
    # Class::FromName at 0x140516680 reads these shuffled, encoded header fields.
    types_offset = BODY_OFFSET + (u32(binary, header + 0xDC) ^ 0x5278CA3B)
    types_size = u32(binary, header + 0x60) ^ 0x15335474
    strings_offset = BODY_OFFSET + ((u32(binary, header + 0x150) + 0xC8542DD2) & 0xFFFFFFFF)
    if not (types_size > 0 and types_size % TYPE_STRIDE == 0
            and 0 <= types_offset < types_offset + types_size <= len(metadata)
            and 0 <= strings_offset < len(metadata)):
        raise ValueError("Decoded metadata table bounds are invalid")
    names = MetadataNames(metadata, strings_offset)
    records = []
    for index in range(types_size // TYPE_STRIDE):
        entry = types_offset + index * TYPE_STRIDE
        name_index = (u32(metadata, entry + 0x14) + 0xE811F779) & 0xFFFFFFFF
        namespace_index = u32(metadata, entry + 0x18) ^ 0x666769B5
        records.append({
            "index": index,
            "name": names.decode(name_index),
            "namespace": names.decode(namespace_index),
            "nameIndex": hex(name_index),
            "namespaceIndex": hex(namespace_index),
        })
    # These independently named runtime and UI types validate the selected tables.
    names = {(item["namespace"], item["name"]) for item in records}
    required = {("", "<Module>"), ("System", "RuntimeType"),
                ("MoleMole", "MonoCrucibleEndPage")}
    if not required.issubset(names):
        raise ValueError("Known runtime and Crucible type names did not decode")
    return records, {"typeOffset": hex(types_offset), "typeBytes": types_size,
                     "typeCount": len(records), "stringDataBase": hex(strings_offset)}


class GenericTypes:
    """Resolve field generic arguments using the startup table read at 0x1405365C5.

    Unlike global metadata, the runtime uses startup metadata without skipping
    a header. Its eight-byte records become 32-byte cached GenericClass records.
    The arguments are registered GenericInst records, not metadata pointers.
    """

    def __init__(self, binary: bytes, startup: bytes, records: list[dict]):
        self.binary, self.startup, self.records = binary, startup, records
        header = pe_file_offset(binary, HEADER_VA, BODY_OFFSET)
        self.base = u32(binary, header + 0x120) ^ 0x1C3F68EA
        size = (u32(binary, header + 0xA0) + 0xE2AC71C1) & 0xFFFFFFFF
        if not (size > 0 and size % 8 == 0 and self.base + size <= len(startup)):
            raise ValueError("Invalid startup generic class table")
        self.count = size // 8
        registration = pe_file_offset(binary, METADATA_REGISTRATION_VA, 0x58)
        self.instances = struct.unpack_from("<Q", binary, registration + 0x28)[0]
        self.references = struct.unpack_from("<Q", binary, registration + 0x50)[0]
        self.reference_count = u32(binary, registration + 0x30) ^ 0x28A0FA2D
        self.cache = {}
        # Validate every startup record before using an index from a field.
        for definition, instance in struct.iter_unpack("<II", startup[self.base:self.base + size]):
            if definition >= len(records):
                raise ValueError("Invalid generic class definition")
            pe_file_offset(binary, self.instances + instance * 16, 16)

    def resolve(self, reference: dict, visiting: frozenset = frozenset()) -> dict:
        if reference["kind"] != "genericinst":
            return reference
        index = int(reference["data"], 16)
        if index not in self.cache:
            if not 0 <= index < self.count or index in visiting or len(visiting) >= 64:
                raise ValueError(f"Invalid or recursive generic class {index}")
            definition, instance = struct.unpack_from("<II", self.startup, self.base + index * 8)
            entry = pe_file_offset(self.binary, self.instances + instance * 16, 16)
            count, pointers = struct.unpack_from("<QQ", self.binary, entry)
            if not 0 < count <= 255:
                raise ValueError(f"Invalid generic argument count for class {index}")
            arguments = []
            entry = pe_file_offset(self.binary, pointers, count * 8)
            for pointer, in struct.iter_unpack("<Q", self.binary[entry:entry + count * 8]):
                if (not self.references <= pointer < self.references + self.reference_count * 16
                        or (pointer - self.references) % 16):
                    raise ValueError(f"Invalid generic argument reference for class {index}")
                data, bits = struct.unpack_from("<QI", self.binary, pe_file_offset(self.binary, pointer, 16))
                kind = (bits >> 16) & 0xFF
                if kind not in TYPE_KINDS:
                    raise ValueError(f"Unknown generic argument kind {kind:#x}")
                argument = {"kind": TYPE_KINDS[kind], "attributes": hex(bits & 0xFFFF), "data": hex(data)}
                if kind in (17, 18):
                    if data >= len(self.records):
                        raise ValueError(f"Invalid generic argument definition for class {index}")
                    argument.update(typeDefinition=data, name=self.records[data]["name"],
                                    namespace=self.records[data]["namespace"])
                arguments.append(self.resolve(argument, visiting | {index}))
            self.cache[index] = {"typeDefinition": definition, "name": self.records[definition]["name"],
                                 "namespace": self.records[definition]["namespace"], "arguments": arguments}
        return {**reference, **self.cache[index]}


def recover_members(binary: bytes, metadata: bytes, records: list[dict], report: dict,
                    *, fields: bool, methods: bool, packet_ids: bool = False,
                    startup: bytes | None = None) -> None:
    """Recover the tables read by Class::SetupFields and Class::SetupMethods.

    Generic arguments can optionally be resolved from startup metadata; array
    payloads remain raw indexes. An optional packet ID comes only
    from the observed literal-return getter; it does not recover a message name
    or protobuf fields. Duplicate IDs across types are preserved.
    """
    if packet_ids and not methods:
        raise ValueError("Packet ID extraction requires method metadata")
    if startup is not None and not fields:
        raise ValueError("Generic type extraction requires field metadata")
    generics = GenericTypes(binary, startup, records) if startup is not None else None
    header = pe_file_offset(binary, HEADER_VA, BODY_OFFSET)
    type_base = int(report["typeOffset"], 16)
    names = MetadataNames(metadata, int(report["stringDataBase"], 16))
    field_total = method_total = 0
    if fields:
        field_base = BODY_OFFSET + ((u32(binary, header + 0x4C) + 0xE5D249BF) & 0xFFFFFFFF)
        registration = pe_file_offset(binary, METADATA_REGISTRATION_VA, 0x58)
        reference_count = u32(binary, registration + 0x30) ^ 0x28A0FA2D
        reference_va = struct.unpack_from("<Q", binary, registration + 0x50)[0]
        reference_base = pe_file_offset(binary, reference_va, reference_count * 16)
        reference_cache = {}
    if methods:
        method_base = BODY_OFFSET + (u32(binary, header + 0x1E0) ^ 0x2EE480FB)
        method_count = sum(struct.unpack_from("<H", metadata, type_base + i * TYPE_STRIDE + 0x3C)[0]
                           ^ 0x8811 for i in range(len(records)))
        registration = pe_file_offset(binary, CODE_REGISTRATION_VA, 0x30)
        pointer_va = struct.unpack_from("<Q", binary, registration + 0x28)[0]
        pointer_base = pe_file_offset(binary, pointer_va, method_count * 8)

    for item in records:
        type_entry = type_base + item["index"] * TYPE_STRIDE
        if fields:
            start = u32(metadata, type_entry) ^ 0x72628007
            count = struct.unpack_from("<H", metadata, type_entry + 0x30)[0] ^ 0x9A6B
            if count and (start != field_total or field_base + (start + count) * 8 > len(metadata)):
                raise ValueError(f"Invalid field span for type {item['index']}")
            item.update(fieldStart=start, fieldCount=count, fields=[])
            for index in range(start, start + count):
                entry = field_base + index * 8
                key = (((index * 0x23CF) ^ 0x3D27C42E) * 0x7F76A166 + 0xF06651FC0B8A) & MASK64
                key = ((key >> 16) + 0x49ACDCBC) & 0xFFFFFFFF
                name_index = u32(metadata, entry) ^ key ^ 0x048E6373
                type_index = ((u32(metadata, entry + 4) + 0xB36B852D) & 0xFFFFFFFF) ^ key ^ 0x4F6E2A1D
                if type_index not in reference_cache:
                    if not 0 <= type_index < reference_count:
                        raise ValueError(f"Invalid type reference for field {index}")
                    data, bits = struct.unpack_from("<QI", binary, reference_base + type_index * 16)
                    kind = (bits >> 16) & 0xFF
                    if kind not in TYPE_KINDS:
                        raise ValueError(f"Unknown type kind {kind:#x} for field {index}")
                    reference = {"kind": TYPE_KINDS[kind], "attributes": hex(bits & 0xFFFF),
                                 "data": hex(data)}
                    if kind in (17, 18):
                        if data >= len(records):
                            raise ValueError(f"Invalid type definition for field {index}")
                        definition = records[data]
                        reference.update(typeDefinition=data, name=definition["name"],
                                         namespace=definition["namespace"])
                    if generics:
                        reference = generics.resolve(reference)
                    reference_cache[type_index] = reference
                item["fields"].append({"index": index, "name": names.decode(name_index),
                                       "nameIndex": hex(name_index), "typeIndex": type_index,
                                       "type": reference_cache[type_index]})
            field_total += count
        if methods:
            start = u32(metadata, type_entry + 8) ^ 0x0478AF59
            count = struct.unpack_from("<H", metadata, type_entry + 0x3C)[0] ^ 0x8811
            if count and (start != method_total or method_base + (start + count) * 26 > len(metadata)):
                raise ValueError(f"Invalid method span for type {item['index']}")
            item.update(methodStart=start, methodCount=count, methods=[])
            for index in range(start, start + count):
                entry = method_base + index * 26
                key = ((((index * 0x4E94) ^ 0x62D8B7B8) * 0x7572748A + 0x1F4D9EEA)
                       ^ 0x6E8931D6) * 0x39036375 & MASK64
                name_index = ((u32(metadata, entry + 4) ^ 0x0613ACE3) - key) & 0xFFFFFFFF
                address = struct.unpack_from("<Q", binary, pointer_base + index * 8)[0]
                if address:
                    pe_file_offset(binary, address)
                item["methods"].append({"index": index, "name": names.decode(name_index),
                                        "nameIndex": hex(name_index), "address": hex(address),
                                        "parameterCount": (metadata[entry + 0x18] - key + 0x8C) & 0xFF})
            method_total += count
            if packet_ids:
                getters = [method for method in item["methods"]
                           if method["name"] == "AEGNNPENLNM"
                           and method["parameterCount"] == 0 and method["address"] != "0x0"]
                if len(getters) > 1:
                    raise ValueError(f"Ambiguous packet ID getter for type {item['index']}")
                if getters:
                    address = int(getters[0]["address"], 16)
                    offset = pe_file_offset(binary, address, 5)
                    code = binary[offset:offset + 5]
                    # mov ax, imm16; ret. Reject every other instruction shape.
                    if code[:2] == b"\x66\xb8" and code[4] == 0xC3:
                        item["packetId"] = struct.unpack_from("<H", code, 2)[0]
                        item["packetIdGetter"] = hex(address)
    if fields:
        report.update(fieldOffset=hex(field_base), fieldCount=field_total,
                      typeReferenceCount=reference_count)
    if methods:
        report.update(methodOffset=hex(method_base), methodCount=method_total)
    if packet_ids:
        ids = [item["packetId"] for item in records if "packetId" in item]
        report.update(packetIdCount=len(ids), uniquePacketIdCount=len(set(ids)))
    if generics:
        report.update(genericClassCount=generics.count, resolvedGenericClassCount=len(generics.cache))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("client_directory", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--include-fields", action="store_true",
                        help="Include field names, attributes and type reference records")
    parser.add_argument("--include-methods", action="store_true",
                        help="Include method names, native addresses and parameter counts")
    parser.add_argument("--include-packet-ids", action="store_true",
                        help="Include literal packet ID getters; requires --include-methods")
    parser.add_argument("--include-generic-types", action="store_true",
                        help="Resolve field generic arguments from startup metadata; requires --include-fields")
    args = parser.parse_args()
    if args.include_packet_ids and not args.include_methods:
        parser.error("--include-packet-ids requires --include-methods")
    if args.include_generic_types and not args.include_fields:
        parser.error("--include-generic-types requires --include-fields")
    default_name = ("client-type-metadata.json" if args.include_fields or args.include_methods
                    else "client-type-names.json")
    output = (args.output or Path("local/activity-research") / default_name).resolve()
    # Reject writes into the input directory, including paths through symlinks.
    if output.is_relative_to(args.client_directory.resolve()):
        raise ValueError("Write analysis results outside the client directory")
    binary = read_verified(args.client_directory / "YuanShen.exe", BINARY_SHA256)
    metadata = read_verified(args.client_directory / "YuanShen_Data/Native/Data/Metadata/global-metadata.dat",
                             METADATA_SHA256)
    startup = (read_verified(args.client_directory / "YuanShen_Data/Native/Data/Metadata/startup-metadata.dat",
                             STARTUP_SHA256) if args.include_generic_types else None)
    records, report = recover_types(binary, metadata)
    if args.include_fields or args.include_methods:
        recover_members(binary, metadata, records, report,
                        fields=args.include_fields, methods=args.include_methods,
                        packet_ids=args.include_packet_ids, startup=startup)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({**report, "output": str(output),
                      "scope": "Metadata and optional literal packet IDs; message names and protobuf fields require verification."}))


if __name__ == "__main__":
    main()
