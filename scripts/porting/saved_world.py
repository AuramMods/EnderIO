"""Read NBT metadata from the harness's closed Java Edition test worlds."""

import gzip
import io
import struct
import zlib


def read_nbt(data):
    stream = io.BytesIO(data)

    def number(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(">" + fmt)))[0]

    def name():
        return stream.read(number("H")).decode("utf-8", errors="replace")

    def value(kind):
        if 1 <= kind <= 6:
            return number("bhiqfd"[kind - 1])
        if kind == 8:
            return name()
        if kind in (7, 11, 12):
            size = number("i")
            return stream.read(size * {7: 1, 11: 4, 12: 8}[kind])
        if kind == 9:
            element = number("B")
            return [value(element) for _ in range(number("i"))]
        if kind == 10:
            fields = {}
            while (child := number("B")) != 0:
                key = name()
                fields[key] = value(child)
            return fields
        raise ValueError(f"Unknown NBT tag {kind}")

    root = number("B")
    name()
    return value(root)


def read_data_file(path):
    return read_nbt(gzip.decompress(path.read_bytes()))


def read_chunk(world, x, z):
    path = world / "region" / f"r.{x // 32}.{z // 32}.mca"
    with path.open("rb") as region:
        header_index = 4 * (x % 32 + z % 32 * 32)
        region.seek(header_index)
        sector = int.from_bytes(region.read(3), "big")
        if sector == 0:
            raise ValueError(f"No saved chunk at {x}, {z} in {path}")
        region.seek(sector * 4096)
        length, compression = struct.unpack(">IB", region.read(5))
        payload = region.read(length - 1)
    if compression == 1:
        payload = gzip.decompress(payload)
    elif compression == 2:
        payload = zlib.decompress(payload)
    elif compression != 3:
        raise ValueError(f"Unsupported region compression {compression} in {path}")
    return read_nbt(payload)
