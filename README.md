# CPC - Centre-Particle Coincidence

An ImageJ/Fiji plugin for object-based colocalization analysis.

CPC determines colocalization by checking whether each object's centroid falls inside a segmented object in the other channel. It accepts any label image (StarDist, Cellpose, threshold, manual ROIs) and performs centre-particle coincidence as a standalone step, decoupling segmentation from colocalization.

---

## Features

- **2-5 channel support** - analyse any number of label images with all pairwise comparisons
- **Input flexibility** - accepts label images from any source, or ROI .zip files
- **Bidirectional analysis** - test both A-in-B and B-in-A for each pair
- **Intensity-weighted centroids** - optional center-of-mass weighting from raw images
- **Multi-target analysis** - combination patterns showing which targets each object colocalizes with
- **Centroid label maps** - visual overlay of centroids on label images
- **Batch processing** - regex-based grouping, recursive folder scanning, aggregated summaries
- **Auto-save** - organised output into `CPC/` subdirectory tree with per-object tables, summaries, and maps

---

## Installation

1. Close Fiji if it is open.
2. Copy the JAR file (`CPC-X.X.X.jar`) into your Fiji installation's plugins folder: `Fiji.app/plugins/`
3. Start Fiji.
4. Run via: **Plugins > CPC**

### Updating

When a new version appears, repeat steps 1-3. Delete the old JAR from `plugins/` before copying the new one.

---

## Usage

### Single Analysis

The main dialog has three sections:

**Input**
- **Label Images mode**: select 2-5 label/object maps from open images or browse for files.
- **ROI Sets mode**: select a reference image and 2-5 ROI .zip files.

**Analysis**
- **Bidirectional**: test both directions for each pair (A in B and B in A).
- **Intensity-weighted centroids**: use raw images to compute center-of-mass instead of geometric centroids. Each raw image must match the dimensions of its corresponding label image.

**Output**
- **Per-object tables (vs)**: one table per pair showing colocalized/contains status for every object.
- **Summary table**: counts and percentages for each pairwise comparison.
- **Extended data**: include volume and centroid coordinates in per-object tables.
- **Multi-target summary**: combination analysis showing which targets each object colocalizes with.
- **Centroid label maps**: label image with cross markers at other channels' centroid positions.
- **Auto-save results**: save all outputs to a `CPC/` subdirectory tree.

### Batch Processing

Click the **Batch...** button to process entire folders of label images.

- Define a filename regex with a capture group for the varying part (e.g. channel name).
- Preview groups before running to verify correct pairing.
- Optionally include subfolders for recursive processing.
- Batch produces aggregated summary tables across all groups and folders.

---

## Algorithm

1. Scan all voxels in each label image, accumulate centroid per label (geometric or intensity-weighted).
2. For each object A: look up the voxel value in image B at A's centroid position.
3. If non-zero, A is colocalized with that B object.
4. Repeat B to A if bidirectional; all pairwise comparisons for >2 images.

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
    CPC_Batch_Summary.csv           pivoted wide summary
    CPC_Batch_Objects_{channel}.csv  per-object wide format
    README.txt
  Folder/
    CPC_Batch_Folder_Summary.csv    aggregated per folder
    CPC_Batch_Folder_Summary_{channel}.csv
    README.txt
  Multi/
    CPC_Batch_Multi_Summary.csv     combination pattern counts
    CPC_Batch_Multi_Summary_{channel}.csv
    README.txt
```

---

## Building from Source

Requires Java 8+ (builds with pom-scijava parent 31.1.0).

```bash
export JAVA_HOME="/path/to/jdk"
bash mvnw clean package -Denforcer.skip=true
```

The built JAR will be at `target/CPC-1.0.0.jar`.
