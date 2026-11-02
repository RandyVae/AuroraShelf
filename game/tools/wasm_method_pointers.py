#!/usr/bin/env python3
"""Scan the decrypted Unity WebGL wasm for IL2CPP method-pointer tables.

Hypothesis: in WebGL builds Il2CppMethodPointer is a wasm function-table index,
so each Il2CppCodeGenModule.methodPointers is an array of small increasing i32s
(< function count).  Finding such arrays lets us map metadata methods to wasm
functions without a name section.
"""
from __future__ import annotations

import struct
import sys
from pathlib import Path

WASM = Path("/Users/itou/Documents/project-hgame/runtime/build_2301/38b30196380eeb95a487104efb188fe4.wasm.unityweb.dec")


def leb(data: bytes, pos: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def sections(data: bytes):
    pos = 8
    while pos < len(data):
        sid = data[pos]
        pos += 1
        size, pos = leb(data, pos)
        yield sid, pos, size
        pos += size


def main() -> int:
    data = WASM.read_bytes()
    print("size", len(data))
    secs = list(sections(data))
    func_count = None
    for sid, off, size in secs:
        if sid == 3:  # function section
            body = data[off:off + size]
            n, p = leb(body, 0)
            func_count = n
    print("function section count:", func_count)

    # element section: table initializer with function indices
    elem_funcs = []
    for sid, off, size in secs:
        if sid == 9:
            body = data[off:off + size]
            p = 0
            count, p = leb(body, p)
            print("elem segments:", count)
            for _ in range(count):
                flags, p = leb(body, p)
                if flags == 0:
                    _, p = leb(body, p)  # offset expr? (0x41 + i32.const + 0x0b)
                    # active segment: offset expr begins with 0x41 (i32.const)
                    if body[p] == 0x41:
                        p += 1
                        _, p = leb(body, p)
                    if body[p] == 0x0B:
                        p += 1
                    n, p = leb(body, p)
                    for _ in range(n):
                        fi, p = leb(body, p)
                        elem_funcs.append(fi)
    print("elem entries:", len(elem_funcs))

    # Scan all data-section bytes for increasing small-int runs.
    best = []
    for sid, off, size in secs:
        if sid != 11:
            continue
        body = data[off:off + size]
        p = 0
        segs, p = leb(body, p)
        print("data segments:", segs)
        for si in range(segs):
            flags, p = leb(body, p)
            if flags == 0:
                # offset expr
                if body[p] == 0x41:
                    p += 1
                    addr, p = leb(body, p)
                if p < len(body) and body[p] == 0x0B:
                    p += 1
            elif flags == 2:
                _, p = leb(body, p)  # memory index
                if body[p] == 0x41:
                    p += 1
                    addr, p = leb(body, p)
                if p < len(body) and body[p] == 0x0B:
                    p += 1
            n, p = leb(body, p)
            blob = body[p:p + n]
            p += n
            # scan blob for runs of little-endian i32 values in [0, func_count*2)
            i = 0
            run_start = 0
            prev = None
            run_len = 0
            while i + 4 <= len(blob):
                v = struct.unpack_from("<I", blob, i)[0]
                if v < (func_count or 0) * 2:
                    if prev is not None and v >= prev and v - prev < 1000:
                        run_len += 1
                    else:
                        run_len = 1
                        run_start = i
                    prev = v
                    if run_len >= 500:
                        best.append((run_len, si, run_start, v))
                        # record the run and reset to find more
                        run_len = 0
                        prev = None
                    i += 4
                else:
                    prev = None
                    run_len = 0
                    i += 4
    best.sort(reverse=True)
    print("candidate runs:", len(best))
    for r in best[:20]:
        print("len=%d seg=%d off=%d last=%d" % r)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
