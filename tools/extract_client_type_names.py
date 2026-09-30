#!/usr/bin/env python3
"""Read the type-name table from the locally supplied 7.1.0 Windows client.

This profile is tied to the exact executable and metadata SHA-256 hashes below.
It recovers names and namespaces, not protobuf fields, packet IDs or a dump.cs.
The source client files are never modified.
"""

import argparse
import hashlib
import json
import struct
from pathlib import Path


BINARY_SHA256 = "7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e"
METADATA_SHA256 = "469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161"
HEADER_VA = 0x1427D5CB0
BODY_OFFSET = 0x210
TYPE_STRIDE = 70
MASK64 = (1 << 64) - 1


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def read_verified(path: Path, expected: str) -> bytes:
    data = path.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise ValueError(f"Unsupported client file: {path.name}; SHA-256 {actual}")
    return data


def pe_file_offset(binary: bytes, address: int) -> int:
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
        if start <= address < start + raw_size:
            return raw_offset + address - start
    raise ValueError(f"Address {address:#x} is not backed by a PE section")


def recover_types(binary: bytes, metadata: bytes) -> tuple[list[dict], dict]:
    header = pe_file_offset(binary, HEADER_VA)
    if binary[header:header + 4] != b"MHY\0" or metadata[:4] != b"MHY\0":
        raise ValueError("Expected the observed MHY metadata headers")
    # Class::FromName at 0x140516680 reads these shuffled, encoded header fields.
    types_offset = BODY_OFFSET + (u32(binary, header + 0xDC) ^ 0x5278CA3B)
    types_size = u32(binary, header + 0x60) ^ 0x15335474
    # Metadata name reader at 0x140510060; this is separate from string literals.
    strings_offset = BODY_OFFSET + ((u32(binary, header + 0x150) + 0xC8542DD2) & 0xFFFFFFFF)
    if not (types_size > 0 and types_size % TYPE_STRIDE == 0
            and 0 <= types_offset < types_offset + types_size <= len(metadata)
            and 0 <= strings_offset < len(metadata)):
        raise ValueError("Decoded metadata table bounds are invalid")

    def decode_name(index: int) -> str:
        if index == 0xFFFFFFFF:
            return ""
        length, offset = index >> 24, index & 0xFFFFFF
        start = strings_offset + offset
        padded_length = (length + 7) // 8 * 8
        if start + padded_length > len(metadata):
            raise ValueError(f"Name {index:#x} exceeds the metadata file")
        key = ((offset * 0xB33E40427D5668C0 + 0x6D8C4AAB00FE8E27) & MASK64) ^ 0x30EE5B43130FE0CD
        plain = bytearray()
        for block in range(padded_length // 8):
            cipher = struct.unpack_from("<Q", metadata, start + block * 8)[0]
            plain.extend((cipher ^ ((key + block * 0x0889EEEA326AFB36) & MASK64)).to_bytes(8, "little"))
        value = plain[:length].decode("utf-8")
        if any(ord(char) < 32 for char in value):
            raise ValueError(f"Name {index:#x} contains a control character")
        return value

    records = []
    for index in range(types_size // TYPE_STRIDE):
        entry = types_offset + index * TYPE_STRIDE
        name_index = (u32(metadata, entry + 0x14) + 0xE811F779) & 0xFFFFFFFF
        namespace_index = u32(metadata, entry + 0x18) ^ 0x666769B5
        records.append({
            "index": index,
            "name": decode_name(name_index),
            "namespace": decode_name(namespace_index),
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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("client_directory", type=Path)
    parser.add_argument("--output", type=Path,
                        default=Path("local/activity-research/client-type-names.json"))
    args = parser.parse_args()
    binary = read_verified(args.client_directory / "YuanShen.exe", BINARY_SHA256)
    metadata = read_verified(args.client_directory / "YuanShen_Data/Native/Data/Metadata/global-metadata.dat",
                             METADATA_SHA256)
    records, report = recover_types(binary, metadata)
    # Reject writes into the input directory, including paths through symlinks.
    output = args.output.resolve()
    if output.is_relative_to(args.client_directory.resolve()):
        raise ValueError("Write analysis results outside the client directory")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({**report, "output": str(output),
                      "scope": "Type names and namespaces only; protobuf schemas remain unverified."}))


if __name__ == "__main__":
    main()
