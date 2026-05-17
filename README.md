# CPC — Centre-Particle Coincidence

[![Build](https://github.com/Jay2owe/CPC/actions/workflows/build-main.yml/badge.svg)](https://github.com/Jay2owe/CPC/actions/workflows/build-main.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![JitPack](https://jitpack.io/v/Jay2owe/CPC.svg)](https://jitpack.io/#Jay2owe/CPC)

An ImageJ/Fiji plugin for object-based colocalization analysis. CPC determines colocalization by checking whether each object's centroid falls inside a segmented object in another channel — accepting any label image (StarDist, Cellpose, threshold, manual ROIs) or ROI set, so segmentation and colocalization are fully decoupled.

---

## Features

- **2–5 channel support** — analyse any number of label images with all pairwise comparisons.
- **Input flexibility** — accepts label images from any source, or ROI `.zip` files.
- **Bidirectional analysis** — test both A-in-B and B-in-A for each pair.
- **Intensity-weighted centroids** — optional centre-of-mass weighting from raw images.
- **Multi-target analysis** — combination patterns showing which targets each object colocalizes with.
- **Centroid label maps** — visual overlay of centroids on label images.
- **Batch processing** — regex-based grouping, recursive folder scanning, aggregated summaries.
- **Auto-save** — organised output into a `CPC/` subdirectory tree with per-object tables, summaries, and maps.

---

## Installation

### Update site (preferred)

In Fiji, open **Help → Update… → Manage update sites**, then enable **Centre-Particle Coincidence (CPC)**. If it is not listed, click **Add Unlisted Site** and use `https://sites.imagej.net/Center-Particle-Coincidence/`.

### Manual JAR

1. Close Fiji if it is open.
2. Download the latest `CPC-X.Y.Z.jar` from the [GitHub Releases](https://github.com/Jay2owe/CPC/releases) page.
3. Drop the JAR into Fiji's `plugins` folder.
4. Start Fiji and run **Plugins → CPC**.

To update, repeat the steps and delete the previous JAR from `plugins/` first.

---

## Usage

### Single Analysis

The main dialog has three sections.

**Input**
- **Label Images mode**: select 2–5 label/object maps from open images or browse for files.
- **ROI Sets mode**: select a reference image and 2–5 ROI `.zip` files.

**Analysis**
- **Bidirectional**: test both directions for each pair (A in B and B in A).
- **Intensity-weighted centroids**: use raw images to compute centre-of-mass instead of geometric centroids. Each raw image must match the dimensions of its corresponding label image.

**Output**
- **Per-object tables (vs)**: one table per pair showing colocalized/contains status for every object.
- **Summary table**: counts and percentages for each pairwise comparison.
- **Extended data**: include volume and centroid coordinates in per-object tables.
- **Multi-target summary**: combination analysis showing which targets each object colocalizes with.
- **Centroid label maps**: label image with cross markers at other channels' centroid positions.
- **Auto-save results**: save all outputs to a `CPC/` subdirectory tree.

### Batch Processing

Click the **Batch…** button to process entire folders of label images.

- Define a filename regex with a capture group for the varying part (e.g. channel name).
- Preview groups before running to verify correct pairing.
- Optionally include subfolders for recursive processing.
- Batch produces aggregated summary tables across all groups and folders.

---

## Algorithm

1. Scan all voxels in each label image, accumulating the centroid per label (geometric or intensity-weighted).
2. For each object A, look up the voxel value in image B at A's centroid position.
3. If non-zero, A is colocalized with that B object.
4. Repeat B → A if bidirectional; perform all pairwise comparisons for >2 images.

---

## Auto-save Output Structure

### Single Analysis (`CPC/`)

```
CPC/
  Objects/
    CPC_{ImageA}_vs_{ImageB}.csv    per-object table per pair
    CPC_Summary.csv                 pairwise summary
    README.txt
  Multi/
    CPC_Multi_{ImageA}.csv          multi-target per-object
    CPC_Multi-Target_Summary.csv    combination patterns
    README.txt
  Maps/
    CPC_Centroid_Map_{ImageA}.tif   label map with centroid overlays
    README.txt
```

Only directories with selected outputs are created.

### Batch (`CPC/`)

```
CPC/
  Objects/
    CPC_Batch_Summary.csv             pivoted wide summary
    CPC_Batch_Objects_{channel}.csv   per-object wide format
    README.txt
  Folder/
    CPC_Batch_Folder_Summary.csv      aggregated per folder
    CPC_Batch_Folder_Summary_{channel}.csv
    README.txt
  Multi/
    CPC_Batch_Multi_Summary.csv       combination pattern counts
    CPC_Batch_Multi_Summary_{channel}.csv
    README.txt
```

---

## Building from Source

Requires Java 8 or newer.

```bash
export JAVA_HOME="/path/to/jdk"
bash mvnw clean package
```

The built JAR will be at `target/CPC-<version>.jar`.

---

## Citing CPC

If you use CPC in published work, please cite it. A `CITATION.cff` file is provided at the repository root and is consumed by GitHub's "Cite this repository" widget. A Zenodo DOI will be added after the first clean BSD-licensed public release is archived.

```
Malcolm, J. (2026). CPC — Centre-Particle Coincidence (v1.4.0) [Software].
GitHub. https://github.com/Jay2owe/CPC
```

```bibtex
@software{malcolm_cpc_2026,
  author    = {Malcolm, Jamie},
  title     = {CPC --- Centre-Particle Coincidence},
  year      = {2026},
  version   = {1.4.0},
  publisher = {GitHub},
  url       = {https://github.com/Jay2owe/CPC}
}
```

---

## License

BSD 3-Clause License. See [`LICENSE`](LICENSE) for the full text.

(CPC versions v1.3.0 and earlier shipped under CC0 1.0 Universal; those releases remain under CC0. Versions from v1.4.0 onwards ship under BSD 3-Clause.)

---

## Acknowledgements

Developed by Jamie Malcolm in the [Brancaccio Lab](https://www.ukdri.ac.uk/labs/brancaccio-lab) at the [UK Dementia Research Institute](https://ukdri.ac.uk/centres/imperial), Imperial College London.

This work was supported by the UK Dementia Research Institute, which receives its core funding from the UK Medical Research Council, the Alzheimer's Society, and Alzheimer's Research UK.

Built on the [Fiji](https://fiji.sc/) / [ImageJ](https://imagej.net/) ecosystem; we thank the SciJava community for the platform. When citing upstream tools, use the Fiji paper (Schindelin et al., 2012), ImageJ paper (Schneider et al., 2012), and ImageJ2/SciJava paper (Rueden et al., 2017) as appropriate for the workflow.
