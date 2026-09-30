# Simulated Centre-Particle Coincidence demo

This demo tests geometric-centroid coincidence on two completely artificial
label images. Think of placing a pin at the centre of each object: an object
counts as colocalized when its pin lands inside an object in the other image.
The input images are already segmented; each positive integer identifies one
object, and zero is background.

## Run in Fiji

1. Install Centre-Particle Coincidence (CPC) using the [repository instructions](../README.md#installation).
2. Download or clone the repository. Keep this folder and its `data/` subfolder together.
3. In Fiji's Script Editor, open [`run_demo.ijm`](run_demo.ijm) and click **Run**.
4. Choose a new, empty output folder. The macro refuses a nonempty folder.

The macro loads [`Demo_A.tif`](data/Demo_A.tif) and [`Demo_B.tif`](data/Demo_B.tif),
uses geometric centroids, analyses both directions, and saves extended per-object
tables and a summary. Result windows are suppressed; the Fiji Log reports
completion, elapsed time and the output location. Allow less than one minute
after Fiji has started; the demo took **0.7 seconds** on the tested laptop.
The actual measured time is in [verification.json](verification.json).
That receipt identifies a locally built 1.5.0 binary; it does not certify the
checksum of the GitHub release binary.
The downloadable GitHub 1.5.0 binary separately passed the same numerical
validator through its plugin entry point in a standalone headless Java process;
see [the public-binary receipt](public-binary-verification.json). That check
does not exercise the Fiji graphical interface.

No Python installation is needed to run the supplied images and macro.

## Input and expected output

Each TIFF contains a 32 × 32 × 5, single-channel, unsigned 16-bit stack with four
labelled cuboids and a voxel size of 1 × 1 × 1 µm. Labels are 1–4. The combined
input size is approximately 22 KB. There is no randomness or biological data.

The output folder contains:

```text
CPC/
  Objects/
    CPC_Summary.csv
    CPC_Demo_A_vs_Demo_B.csv
    CPC_Demo_B_vs_Demo_A.csv
    README.txt
```

The summary must contain two rows:

- **A in B:** 4 objects; 3 colocalized (75%); 1 contains a B centre (25%);
  3 colocalized or contains (75%).
- **B in A:** 4 objects; 1 colocalized (25%); 3 contain A centres (75%);
  3 colocalized or contains (75%).

“Contains” means that the source object contains at least one *other-image
centroid*, rather than requiring the entire other object to be enclosed.
The bidirectional result differs because the cuboids have different sizes
and centre positions. A label 4 and B label 4 are separate negative controls.

The complete known answers for all eight object rows, including volumes,
centroid coordinates, partner labels and contains counts, are provided in
[`expected_result.json`](expected_result.json). X and Y coordinates in that
file are zero-based pixels; Z is also zero-based despite the CSV column name
`Centroid Z (slice)`. The object volumes are in voxels.

## Optional automatic check

If Python 3 is available, run the following from the repository root, replacing
the example output path with the folder you selected in Fiji. Only Python's
standard library is used.

```powershell
cd C:/path/to/CPC
python demo/validate_outputs.py C:/path/to/demo-output
```

A successful check ends with:

```text
CPC demo passed: A in B = 75%; B in A = 25%; all eight objects match.
```

## Optional regeneration

The TIFFs are supplied, so this step is unnecessary for running the demo.
To reproduce them, install NumPy and tifffile, then choose an empty destination:

```powershell
cd C:/path/to/CPC
python -m pip install numpy tifffile
python demo/generate_data.py --output C:/path/to/new-empty-data
```

[`generate_data.py`](generate_data.py) declares every cuboid's coordinates and
refuses to overwrite a nonempty destination. The tested dependency versions
and input SHA-256 hashes are in [verification.json](verification.json).

This is a numerical demonstration of labelled-object coincidence. It does not
benchmark segmentation, raw-image preprocessing, intensity-weighted centres,
batch processing or performance on large experimental images.
