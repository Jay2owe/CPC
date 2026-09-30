"""Recreate the tiny, fully artificial Centre-Particle Coincidence inputs.

Requires numpy and tifffile only for optional regeneration. Writes two 16-bit
ImageJ TIFF stacks, each 32 x 32 x 5 voxels, with four labelled cuboids per map.
Coordinates below are inclusive, zero-based (x, y, z). No randomness or
biological data is involved. Existing destination files are never overwritten.
"""
import argparse
from pathlib import Path

import numpy as np
import tifffile

SHAPE = (5, 32, 32)  # Z, Y, X
BOXES = {
    "Demo_A": [(4, 10, 4, 10, 1, 3), (20, 22, 5, 7, 1, 3),
               (4, 6, 20, 22, 1, 3), (22, 24, 22, 24, 1, 3)],
    "Demo_B": [(3, 11, 3, 11, 0, 4), (18, 28, 3, 9, 1, 3),
               (5, 13, 18, 26, 1, 3), (28, 30, 28, 30, 1, 3)],
}


def generate(output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise FileExistsError(f"Use an empty output directory: {output}")
    for name, boxes in BOXES.items():
        image = np.zeros(SHAPE, dtype=np.uint16)
        for label, (x0, x1, y0, y1, z0, z1) in enumerate(boxes, start=1):
            region = image[z0:z1 + 1, y0:y1 + 1, x0:x1 + 1]
            if region.any():
                raise ValueError(f"Overlapping labels in {name}")
            region[:] = label
        tifffile.imwrite(output / f"{name}.tif", image, imagej=True,
                         resolution=(1.0, 1.0),
                         metadata={"axes": "ZYX", "spacing": 1.0, "unit": "um"})
    print(f"Created two simulated label stacks in {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "data")
    generate(parser.parse_args().output)
