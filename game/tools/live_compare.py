#!/usr/bin/env python3
"""Compare retained decoded column tables, including changes to existing rows.

This reads local JSON only; original MessagePack hashes cannot be verified here.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KEYS = {"Constants": "name", "Chests": "chest_name", "Monsters_1": ("id", "inner_id"),
        "Ranks": "exp", "TutorialChapters": "id", "Bundles": "id", "MathFunctions": "id"}


def columns(path):
    obj = json.loads(path.read_text(encoding="utf-8"))
    data = obj.get("data")
    if not isinstance(data, dict) or not data:
        raise ValueError(f"Expected nonempty column table: {path}")
    if not all(isinstance(v, list) for v in data.values()):
        raise ValueError(f"Non-list column: {path}")
    lengths = {len(v) for v in data.values()}
    if len(lengths) != 1:
        raise ValueError(f"Unequal column lengths: {path}")
    return data


def keyed_rows(cols, key):
    keys = (key,) if isinstance(key, str) else key
    if any(name not in cols for name in keys):
        raise ValueError(f"Missing comparison key: {keys}")
    rows = {}
    for index in range(len(cols[keys[0]])):
        value = [cols[name][index] for name in keys]
        token = json.dumps(value, ensure_ascii=False, sort_keys=True)
        if token in rows:
            raise ValueError(f"Duplicate comparison key in {key}: {token}")
        rows[token] = {name: values[index] for name, values in cols.items()}
    return rows


def compare(local, live, key):
    before, after = keyed_rows(local, key), keyed_rows(live, key)
    added, removed = sorted(after.keys() - before.keys()), sorted(before.keys() - after.keys())
    changed = []
    for token in sorted(before.keys() & after.keys()):
        fields = {}
        for name in sorted(local.keys() | live.keys()):
            old, new = before[token], after[token]
            if name not in old or name not in new or old[name] != new[name]:
                fields[name] = {"before_present": name in old, "after_present": name in new}
                if name in old:
                    fields[name]["before"] = old[name]
                if name in new:
                    fields[name]["after"] = new[name]
        if fields:
            keys = (key,) if isinstance(key, str) else key
            changed.append({"key": {name: after[token][name] for name in keys}, "fields": fields})
    return {"key": key, "rows_live": len(after), "rows_build": len(before),
            "cols_live": len(live), "cols_build": len(local),
            "added_columns": sorted(live.keys() - local.keys()),
            "removed_columns": sorted(local.keys() - live.keys()),
            "added_count": len(added), "removed_count": len(removed),
            "changed_count": len(changed),
            "changed_field_count": sum(len(row["fields"]) for row in changed),
            "added_rows": [after[token] for token in added],
            "removed_rows": [before[token] for token in removed], "changed_rows": changed}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-dir", type=Path, default=ROOT / "data/configs")
    parser.add_argument("--live-dir", type=Path, default=ROOT / "data/live_configs")
    parser.add_argument("--output", type=Path, default=ROOT / "data/live_field_diff.json")
    parser.add_argument("--tables", nargs="+", choices=sorted(KEYS), default=sorted(KEYS))
    args = parser.parse_args()
    report = {"scope": "retained_decoded_json_only", "original_response_hash_verified": False, "tables": {}}
    for table in args.tables:
        result = compare(columns(args.build_dir / f"{table}.json"),
                         columns(args.live_dir / f"{table}.json"), KEYS[table])
        report["tables"][table] = result
        print(f"{table}: build={result['rows_build']} live={result['rows_live']} "
              f"added={result['added_count']} removed={result['removed_count']} "
              f"changed={result['changed_count']} fields={result['changed_field_count']}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
