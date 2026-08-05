# CPC — Centre-Particle Coincidence

[![Build](https://github.com/Jay2owe/CPC/actions/workflows/build-main.yml/badge.svg)](https://github.com/Jay2owe/CPC/actions/workflows/build-main.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![JitPack](https://jitpack.io/v/Jay2owe/CPC.svg)](https://jitpack.io/#Jay2owe/CPC)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.21633355.svg)](https://doi.org/10.5281/zenodo.21633355)

An ImageJ/Fiji plugin for object-based colocalization analysis. CPC determines colocalization by checking whether each object's centroid falls inside a segmented object in another channel — accepting any label image (StarDist, Cellpose, threshold, manual ROIs) or ROI set, so segmentation and colocalization are fully decoupled.

---

## Features

- **2–5 channel support** — analyse any number of label images with all pairwise comparisons. The dialog and the macro interface expose five channel slots; the Java API and folder batch processing are not limited to five.
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

## Macro And Java API

### Minimal Macro

Open two label images in Fiji, then run:

```javascript
run("Centre-Particle Coincidence", "image1=[Label A] image2=[Label B]");
```

### Batch-Friendly Macro

Use forward slashes in paths on Windows.

```javascript
run("Centre-Particle Coincidence",
    "image1_path=[C:/data/A_labels.tif] image2_path=[C:/data/B_labels.tif] " +
    "auto_save save_dir=[C:/data/output] hide_display extended multi_target centroid_maps");
```

### Macro Options

| Option | Default | Notes |
| --- | --- | --- |
| `mode=labels` or `mode=rois` | `labels` | Label-image mode or ROI-set mode. |
| `image1=[Title]` ... `image5=[Title]` | none | Open label image titles. Use 2-5 images. |
| `image1_path=[C:/path.tif]` ... `image5_path=[C:/path.tif]` | none | Label image files. Do not combine title and path for the same slot. |
| `reference=[Title]`, `reference_path=[C:/ref.tif]` | none | Required only for `mode=rois`. |
| `roi1=[C:/A.zip]` ... `roi5=[C:/E.zip]` | none | ROI sets for `mode=rois`; provide at least 2. |
| `bidirectional`, `unidirectional` | `bidirectional` | Controls pair direction testing. |
| `center_of_mass` | off | Uses raw images for intensity-weighted centroids. Label mode only. |
| `raw1=[Title]`, `raw1_path=[C:/raw.tif]` ... | none | Raw images parallel to selected label slots. |
| `objects`, `hide_objects` | `objects` | Per-object vs tables. |
| `summary`, `hide_summary` | `summary` | Pairwise summary table. |
| `extended` | off | Adds volume and centroid columns to per-object tables. |
| `multi_target` | off | Adds multi-target per-object and summary outputs. |
| `centroid_maps` | off | Creates centroid label maps. |
| `auto_save` | off | Saves selected outputs into a `CPC/` subfolder tree. |
| `save_dir=[C:/output]` | first image folder | Implies `auto_save`. |
| `hide_display` | off | Suppresses result windows while still computing/saving outputs. |

Bracketed values may contain spaces, but not brackets, quotes, backslashes, or line breaks. Use `/` path separators in macro strings.

### Java API

```java
List<ImagePlus> labels = Arrays.asList(labelA, labelB, labelC);
CPCResult result = CPC.run(CPCParameters.builder(labels)
        .bidirectional(true)
        .extendedData(true)
        .includeMultiTarget(true)
        .build());

ResultsTable summary = result.getSummaryTable();
ResultsTable objects = result.getConsolidatedTable();
```

The Java API does not open dialogs, show result windows, write files, or require active ImageJ windows. Pass `ImagePlus` instances directly; use raw images via `rawImages(...)` for intensity-weighted centroids. It accepts two or more label images, with no upper limit — `CPCParameters.MAX_IMAGES` describes the dialog and macro slots, not a cap on `CPC.run`.

Per-object rows come back ascending by label. The multi-target summary always carries a `None` row, including when its count is zero, so a script can read the non-colocalized count without checking whether the row exists.

### Batch Java API

```java
CPCBatchResult batch = CPCBatchRunner.run(CPCBatchParameters.builder(
        new File("C:/data/labels"),
        "(.+?)_objects_(.+)\\.tif",
        2)
        .recursive(true)
        .saveDir(new File("C:/data/output"))
        .extendedData(true)
        .build());
```

`CPCBatchRunner.preview(...)` returns the same group preview text shown in the UI. `CPCBatchRunner.run(...)` opens image files, closes them after processing, and writes the usual `CPC/` batch outputs when auto-save is enabled.

### ROI Label API

```java
ImagePlus labels = CPCLabelImages.fromRoiSetFile(referenceImage, "C:/data/cells.zip");
```

### API Surface Decisions

| Surface | Decision | Public API |
| --- | --- | --- |
| Single-image-set CPC analysis | Expose now | `CPC`, `CPCParameters`, `CPCResult` |
| Macro/headless plugin execution | Expose now | `run("Centre-Particle Coincidence", "...")` |
| Batch scanning, preview, and execution | Wrap now | `CPCBatchRunner`, `CPCBatchParameters`, `CPCBatchResult` |
| ROI-set to label-image conversion | Wrap now | `CPCLabelImages` |
| Swing dialogs and toggle controls | Defer | UI-only implementation detail |

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

CPC's analysis engine lives in two small modules that are **compiled into** the
jar rather than required as installs, so a user never has to find them. They are
published to no Maven repository on purpose: a core that could be downloaded
separately is a core two plugins can disagree about the version of. Build them
first — chassis before engine:

```bash
export JAVA_HOME="/path/to/jdk"

git clone --branch v0.1.0 https://github.com/Jay2owe/oc3d-core
mvn -f oc3d-core/pom.xml -DskipTests install

git clone --branch v0.1.0 https://github.com/Jay2owe/cpc-core
mvn -f cpc-core/pom.xml -DskipTests install

bash mvnw clean package
```

The built JAR will be at `target/CPC-<version>.jar`, with both cores shaded in
under `cpc.internal.*`. `.github/workflows/build-main.yml` does exactly this and
is the reference if the steps above drift.

The tags are pinned rather than tracking a branch: the same CPC commit should
always produce the same jar, which is the claim an archived release makes.

---

## Citation

If you use CPC in published work, please cite it. The **concept DOI** below
always resolves to the latest release:

> Malcolm, J. (2026). *CPC — Centre-Particle Coincidence* (Version 1.5.0) [Computer software]. Zenodo. https://doi.org/10.5281/zenodo.21633355

```bibtex
@software{malcolm_cpc_2026,
  author    = {Malcolm, Jamie},
  title     = {{CPC} --- {C}entre-{P}article {C}oincidence},
  version   = {1.5.0},
  year      = {2026},
  publisher = {Zenodo},
  doi       = {10.5281/zenodo.21633355},
  url       = {https://doi.org/10.5281/zenodo.21633355}
}
```

Where exact reproducibility matters, cite the DOI of the specific version you
ran instead. Zenodo mints one per archived release — v1.4.0 is
[`10.5281/zenodo.21633356`](https://doi.org/10.5281/zenodo.21633356).

GitHub's **Cite this repository** button (top right, generated from
[`CITATION.cff`](CITATION.cff)) produces both APA and BibTeX automatically.

## License

BSD 3-Clause License. See [`LICENSE`](LICENSE) for the full text.

(CPC versions v1.3.0 and earlier shipped under CC0 1.0 Universal; those releases remain under CC0. Versions from v1.4.0 onwards ship under BSD 3-Clause.)

---

## Acknowledgements

Developed by Jamie Malcolm in the [Brancaccio Lab](https://www.ukdri.ac.uk/labs/brancaccio-lab) at the [UK Dementia Research Institute](https://ukdri.ac.uk/centres/imperial), Imperial College London.

This work was supported by the UK Dementia Research Institute, which receives its core funding from the UK Medical Research Council, the Alzheimer's Society, and Alzheimer's Research UK.

Built on the [Fiji](https://fiji.sc/) / [ImageJ](https://imagej.net/) ecosystem; we thank the SciJava community for the platform. When citing upstream tools, use the Fiji paper (Schindelin et al., 2012), ImageJ paper (Schneider et al., 2012), and ImageJ2/SciJava paper (Rueden et al., 2017) as appropriate for the workflow.
