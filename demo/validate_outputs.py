"""Check the demo summary and all eight objects against explicit known answers.

Uses Python 3's standard library only; does not modify inputs or results.
    python demo/validate_outputs.py /path/to/demo-output
"""
import argparse
import csv
import json
import math
from pathlib import Path


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def compare(actual, expected, context):
    for column, value in expected.items():
        matches = actual[column] == value if isinstance(value, str) else math.isclose(float(actual[column]), value, rel_tol=0, abs_tol=0.001)
        if not matches:
            raise AssertionError(f"{context}: {column} = {actual[column]}, expected {value}")


def validate(output):
    expected = json.loads((Path(__file__).resolve().parent / "expected_result.json").read_text(encoding="utf-8"))
    tables = output / "CPC/Objects"
    summary = rows(tables / "CPC_Summary.csv")
    if len(summary) != 2:
        raise AssertionError(f"Expected two direction summaries, got {len(summary)}")
    seen = set()
    for row in summary:
        name = Path(row["Image"]).stem
        if name not in expected["summary"] or name in seen:
            raise AssertionError(f"Unexpected or repeated source map: {name}")
        partner = "Demo_B" if name == "Demo_A" else "Demo_A"
        if Path(row["vs"]).stem != partner:
            raise AssertionError(f"Unexpected comparison for {name}")
        seen.add(name)
        compare(row, expected["summary"][name], name)
    for name, expected_rows in expected["objects"].items():
        partner = "Demo_B" if name == "Demo_A" else "Demo_A"
        actual = rows(tables / f"CPC_{name}_vs_{partner}.csv")
        # Saved tables name the two directions explicitly in their headers.
        for row in actual:
            row["Colocalized"] = row[f"Colocalized ({name}.tif in {partner}.tif)"]
            row["Contains"] = row[f"Contains ({partner}.tif in {name}.tif)"]
        if len(actual) != 4:
            raise AssertionError(f"{name}: expected four objects, got {len(actual)}")
        by_label = {int(float(row["Label"])): row for row in actual}
        if set(by_label) != {1, 2, 3, 4}:
            raise AssertionError(f"{name}: labels must be exactly 1, 2, 3, 4")
        for expected_row in expected_rows:
            compare(by_label[expected_row["Label"]], expected_row, f"{name} label {expected_row['Label']}")
        print(f"{name}: PASS (four objects, volumes, centroids, partners and contains counts)")
    print("CPC demo passed: A in B = 75%; B in A = 25%; all eight objects match.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="Empty output folder selected when running the Fiji macro")
    validate(parser.parse_args().output.resolve())
