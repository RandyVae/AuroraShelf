#!/usr/bin/env python3
"""Slice one function out of the Unity WebGL wasm so it can be decompiled alone.

The output module keeps every declaration (types, imports, function signatures,
table, exports, element segment) but replaces all bodies with `end`, except the
selected function whose original body is preserved. Data segments are dropped.
"""
from __future__ import annotations

import sys
from pathlib import Path

WASM = Path("/Users/itou/Documents/project-hgame/runtime/build_2301/38b30196380eeb95a487104efb188fe4.wasm.unityweb.dec")


def leb(data: bytes, pos: int) -> tuple[int, int]:
    r = 0
    s = 0
    while True:
        b = data[pos]
        pos += 1
        r |= (b & 0x7F) << s
        if not b & 0x80:
            return r, pos
        s += 7


def enc(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def sections(data: bytes):
    pos = 8
    while pos < len(data):
        sid = data[pos]
        pos += 1
        size, pos = leb(data, pos)
        yield sid, pos, size
        pos += size


def main() -> int:
    target = int(sys.argv[1], 0)
    out_path = Path(sys.argv[2] if len(sys.argv) > 2 else "/tmp/slice.wasm")
    data = WASM.read_bytes()
    secs = {sid: (off, size) for sid, off, size in sections(data)}
    # function section -> number of imported functions
    imp_off, imp_size = secs[2]
    body = data[imp_off:imp_off + imp_size]
    p = 0
    n_imports, p = leb(body, p)
    imported_funcs = 0
    for _ in range(n_imports):
        ln, p = leb(body, p)
        p += ln
        ln, p = leb(body, p)
        p += ln
        kind = body[p]
        p += 1
        if kind == 0:
            imported_funcs += 1
            _, p = leb(body, p)
        elif kind == 1:
            p += 1
            flags, p = leb(body, p)
            _, p = leb(body, p)
            if flags & 1:
                _, p = leb(body, p)
        elif kind == 2:
            flags, p = leb(body, p)
            _, p = leb(body, p)
            if flags & 1:
                _, p = leb(body, p)
        elif kind == 3:
            p += 2
    # function section count
    fn_off, fn_size = secs[3]
    fb = data[fn_off:fn_off + fn_size]
    count, p = leb(fb, 0)
    total_funcs = count
    # code section bodies
    code_off, code_size = secs[10]
    cb = data[code_off:code_off + code_size]
    c, p = leb(cb, 0)
    bodies = []
    for _ in range(c):
        size, p = leb(cb, p)
        bodies.append(cb[p:p + size])
        p += size
    if target < imported_funcs or target >= imported_funcs + total_funcs:
        print("target out of range", imported_funcs, total_funcs)
        return 1
    local_index = target - imported_funcs
    selected = bodies[local_index]
    print(f"imports={imported_funcs} funcs={total_funcs} target_local={local_index} body={len(selected)}")
    # build code section
    new_bodies = []
    empty = bytes([0x00, 0x0B])  # local decl count 0, end
    for i in range(total_funcs):
        new_bodies.append(selected if i == local_index else empty)
    code_payload = bytearray()
    code_payload += enc(total_funcs)
    for b in new_bodies:
        code_payload += enc(len(b)) + b
    # assemble module: keep sections 1,2,3,4,5,6,7,9; replace 10
    out = bytearray(b"\x00asm\x01\x00\x00\x00")
    for sid in (1, 2, 3, 4, 5, 6, 7, 9):
        if sid not in secs:
            continue
        off, size = secs[sid]
        out.append(sid)
        out += enc(size)
        out += data[off:off + size]
    out.append(10)
    out += enc(len(code_payload))
    out += code_payload
    out_path.write_bytes(out)
    print("wrote", out_path, len(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
