"""Recover absent proto sources from descriptors embedded in generated Java.

The recovered definitions preserve the schema of historical generated Java classes.
They are not evidence that an unresolved message is valid for a newer client.
After generated Java is removed from Git, pass --generated with a historical checkout.
"""

from __future__ import annotations

import argparse
import ast
import re
from pathlib import Path

from google.protobuf import descriptor_pb2


ROOT = Path(__file__).resolve().parents[1]
GENERATED = ROOT / "src/generated/main/java/emu/grasscutter/net/proto"
SOURCES = ROOT / "src/main/proto"
DESCRIPTOR_DATA = re.compile(r"java\.lang\.String\[\] descriptorData = \{(.*?)\};", re.S)
JAVA_STRING = re.compile(r'"(?:\\.|[^"\\])*"')
SCALARS = {
    descriptor_pb2.FieldDescriptorProto.TYPE_DOUBLE: "double",
    descriptor_pb2.FieldDescriptorProto.TYPE_FLOAT: "float",
    descriptor_pb2.FieldDescriptorProto.TYPE_INT64: "int64",
    descriptor_pb2.FieldDescriptorProto.TYPE_UINT64: "uint64",
    descriptor_pb2.FieldDescriptorProto.TYPE_INT32: "int32",
    descriptor_pb2.FieldDescriptorProto.TYPE_FIXED64: "fixed64",
    descriptor_pb2.FieldDescriptorProto.TYPE_FIXED32: "fixed32",
    descriptor_pb2.FieldDescriptorProto.TYPE_BOOL: "bool",
    descriptor_pb2.FieldDescriptorProto.TYPE_STRING: "string",
    descriptor_pb2.FieldDescriptorProto.TYPE_BYTES: "bytes",
    descriptor_pb2.FieldDescriptorProto.TYPE_UINT32: "uint32",
    descriptor_pb2.FieldDescriptorProto.TYPE_SFIXED32: "sfixed32",
    descriptor_pb2.FieldDescriptorProto.TYPE_SFIXED64: "sfixed64",
    descriptor_pb2.FieldDescriptorProto.TYPE_SINT32: "sint32",
    descriptor_pb2.FieldDescriptorProto.TYPE_SINT64: "sint64",
}


def descriptor_from_java(path: Path) -> descriptor_pb2.FileDescriptorProto:
    source = path.read_text(encoding="utf-8")
    block = DESCRIPTOR_DATA.search(source)
    if block is None:
        raise ValueError(f"{path.name}: no embedded descriptor")
    fragments = JAVA_STRING.findall(block.group(1))
    data = "".join(ast.literal_eval(fragment) for fragment in fragments).encode("latin1")
    return descriptor_pb2.FileDescriptorProto.FromString(data)


def proto_package(file_name: str) -> str:
    stem = Path(file_name).stem
    return "recovered.p_" + re.sub(r"[^A-Za-z0-9_]", "_", stem)


def defined_symbols(file: descriptor_pb2.FileDescriptorProto) -> set[str]:
    result: set[str] = set()

    def collect_message(message, prefix: str) -> None:
        name = prefix + message.name
        result.add(name)
        for nested in message.nested_type:
            collect_message(nested, name + ".")
        for enum in message.enum_type:
            result.add(name + "." + enum.name)

    for message in file.message_type:
        collect_message(message, "")
    for enum in file.enum_type:
        result.add(enum.name)
    return result


def field_type(field: descriptor_pb2.FieldDescriptorProto, resolve_type) -> str:
    return SCALARS.get(field.type) or resolve_type(field.type_name)


def render_field(field, indent: str, map_entries: dict, resolve_type) -> str:
    entry = map_entries.get(field.type_name.rsplit(".", 1)[-1])
    if entry and field.type == field.TYPE_MESSAGE and field.label == field.LABEL_REPEATED:
        key, value = entry.field
        typename = f"map<{field_type(key, resolve_type)}, {field_type(value, resolve_type)}>"
        label = ""
    else:
        typename = field_type(field, resolve_type)
        label = "repeated " if field.label == field.LABEL_REPEATED else ""
        if field.proto3_optional:
            label = "optional "
    return f"{indent}{label}{typename} {field.name} = {field.number};"


def render_enum(enum, indent: str) -> list[str]:
    lines = [f"{indent}enum {enum.name} {{"]
    lines.extend(f"{indent}  {value.name} = {value.number};" for value in enum.value)
    lines.append(f"{indent}}}")
    return lines


def render_message(message, indent: str, resolve_type) -> list[str]:
    lines = [f"{indent}message {message.name} {{"]
    nested_indent = indent + "  "
    map_entries = {nested.name: nested for nested in message.nested_type if nested.options.map_entry}
    oneofs = {index: [] for index in range(len(message.oneof_decl))}
    for field in message.field:
        if field.HasField("oneof_index") and not field.proto3_optional:
            oneofs[field.oneof_index].append(field)
        else:
            lines.append(render_field(field, nested_indent, map_entries, resolve_type))
    for index, fields in oneofs.items():
        if not fields:
            continue
        lines.append(f"{nested_indent}oneof {message.oneof_decl[index].name} {{")
        lines.extend(render_field(field, nested_indent + "  ", map_entries, resolve_type) for field in fields)
        lines.append(f"{nested_indent}}}")
    for enum in message.enum_type:
        lines.extend(render_enum(enum, nested_indent))
    for nested in message.nested_type:
        if not nested.options.map_entry:
            lines.extend(render_message(nested, nested_indent, resolve_type))
    lines.append(f"{indent}}}")
    return lines


def render_file(file: descriptor_pb2.FileDescriptorProto, java_class: str, known_symbols: dict) -> str:
    if file.syntax != "proto3" or file.service or file.extension:
        raise ValueError(f"unsupported descriptor features in {file.name}")
    lines = [
        f"// Recovered from {java_class}'s embedded descriptor; wire schema may predate 7.1.",
        'syntax = "proto3";',
        "",
    ]
    lines.append(f"package {proto_package(file.name)};")
    lines.append(f'option java_package = "{file.options.java_package}";')
    lines.append(f'option java_outer_classname = "{java_class.removesuffix(".java")}";')
    for dependency in file.dependency:
        lines.append(f'import "{dependency}";')

    def resolve_type(original: str) -> str:
        symbol = original.removeprefix(".")
        for name in [file.name, *file.dependency]:
            if symbol in known_symbols.get(name, ()):
                return f".{proto_package(name)}.{symbol}"
        return original

    for enum in file.enum_type:
        lines.extend(["", *render_enum(enum, "")])
    for message in file.message_type:
        lines.extend(["", *render_message(message, "", resolve_type)])
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=SOURCES)
    parser.add_argument("--generated", type=Path, default=GENERATED)
    args = parser.parse_args()
    output: Path = args.output.resolve()
    generated: Path = args.generated.resolve()
    if not generated.exists():
        parser.error(f"Generated Java source directory does not exist: {generated}")
    output.mkdir(parents=True, exist_ok=True)

    by_name: dict[str, tuple[descriptor_pb2.FileDescriptorProto, Path]] = {}
    for path in sorted(generated.glob("*.java")):
        try:
            file = descriptor_from_java(path)
        except Exception:
            if path.name != "SceneEntityUpdateNotifyOuterClass.java":
                raise
            continue  # Its embedded dependency name was hand-edited without updating the length.
        source_path = SOURCES / file.name
        if source_path.exists() and not source_path.read_text(encoding="utf-8").startswith("// Recovered from "):
            continue
        previous = by_name.get(file.name)
        preferred = Path(file.name).stem + "OuterClass.java"
        if previous is None or path.name == preferred:
            by_name[file.name] = (file, path)

    # This generated Java file has a hand-edited, invalid descriptorData dependency string.
    # Its field declarations still provide the complete three-field schema.
    special = descriptor_pb2.FileDescriptorProto()
    special.name = "SceneEntityUpdateNotify.proto"
    special.syntax = "proto3"
    special.options.java_package = "emu.grasscutter.net.proto"
    special.dependency.extend(["SceneEntityInfo.proto", "VisionType.proto"])
    message = special.message_type.add()
    message.name = "SceneEntityUpdateNotify"
    for name, number, kind, label, type_name in [
        ("param", 14, descriptor_pb2.FieldDescriptorProto.TYPE_UINT32, 1, ""),
        ("entity_list", 10, descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE, 3, ".SceneEntityInfo"),
        ("appear_type", 13, descriptor_pb2.FieldDescriptorProto.TYPE_ENUM, 1, ".VisionType"),
    ]:
        field = message.field.add()
        field.name, field.number, field.type, field.label = name, number, kind, label
        if type_name:
            field.type_name = type_name
    special_java = generated / "SceneEntityUpdateNotifyOuterClass.java"
    if special_java.exists():
        by_name[special.name] = (special, special_java)

    known_symbols = {name: defined_symbols(item[0]) for name, item in by_name.items()}
    for name, (file, path) in by_name.items():
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(render_file(file, path.name, known_symbols), encoding="utf-8", newline="\n")
    print(f"Recovered {len(by_name)} proto sources in {output}")


if __name__ == "__main__":
    main()
