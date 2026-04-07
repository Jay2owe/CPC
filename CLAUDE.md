# CPC — Centre-Particle Coincidence

## What It Does
ImageJ/Fiji plugin for object-based colocalization. Takes 2–5 label images (or ROI sets) and determines colocalization by checking whether each object's centroid falls inside a segmented object in the other image. Supports geometric or intensity-weighted centroids.

## Why It Exists
Existing plugins (JACoP, DiAna, ComDet) couple segmentation and colocalization together. CPC decouples them — accepts label images from any source (StarDist, Cellpose, threshold, manual ROIs) and performs centre-particle coincidence as a standalone step.

## Algorithm
1. Scan all voxels in each label image, accumulate centroid per label (geometric or intensity-weighted from a raw image)
2. For each object A: look up voxel value in image B at A's centroid position
3. If non-zero → A is colocalized with that B object
4. Repeat B→A if bidirectional; all pairwise comparisons for >2 images

## Project Structure
```
src/main/java/cpc/
  CPC_.java            Entry point (PlugIn), dialog, orchestration
  CPCAnalysis.java     Core: extractObjects(), testCoincidence(), results, auto-save
  CPCBatch.java        Batch mode: regex grouping, recursive folder scan, aggregated summary
  LabelUtils.java      ROI .zip loading, ROI→label image conversion
  ui/CPCDialog.java    Swing dialog with groups, toggles, file/directory fields
  ui/ToggleSwitch.java Material toggle switch component
src/main/resources/
  plugins.config       Menu registration: Plugins>CPC
```

## Build & Deploy
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-25.0.2"
bash mvnw clean package -Denforcer.skip=true
cp target/CPC-1.0.0.jar "/c/Users/jamie/UK Dementia Research Institute Dropbox/Brancaccio Lab/Jamie/Fiji.app/plugins/"
cp target/CPC-1.0.0.jar "/c/Users/jamie/UK Dementia Research Institute Dropbox/Brancaccio Lab/LabAdmin/Software/ImageJ/Plugins/Jamie's Centre-Particle Coincidence (CPC) Plugin/"
"/c/Users/jamie/UK Dementia Research Institute Dropbox/Brancaccio Lab/Jamie/Fiji.app/ImageJ-win64.exe" &
```
- **After every successful build, always deploy the jar to both locations and launch Fiji.**
- Lab distribution folder: `LabAdmin/Software/ImageJ/Plugins/Jamie's Centre-Particle Coincidence (CPC) Plugin/`
  - Contains: JAR, `How to Install.txt`, `README.md` (hardlinked to project root)
- Java 8 compatible (pom-scijava parent 31.1.0), only dependency: `net.imagej:ij`

## Dialog Sections
- **Input**: Label Images (up to 5 dropdowns + file browse) or ROI Sets (reference image + up to 5 .zip files). Mode switch shows/hides groups.
- **Analysis**: Bidirectional toggle. Intensity-weighted centroids toggle → reveals per-slot raw image selection (dropdown + file browse, parallel to label image slots). Raw image dimensions must match label image.
- **Output**: Per-object tables, summary, centroid coordinates, multi-target summary, centroid label maps. Auto-save toggle → reveals directory field (defaults to active image's directory). Saves CSVs + TIFFs.

## Auto-save Output (single-analysis mode)
Creates a `CPC/` subdirectory tree in the save directory, mirroring batch structure:
- `CPC/Objects/` — `CPC_{ImageA}_vs_{ImageB}.csv` (per-object tables) + `CPC_Summary.csv` (pairwise summary)
- `CPC/Multi/` — `CPC_Multi_{ImageA}.csv` (multi-target per-object) + `CPC_Multi-Target_Summary.csv` (combination patterns)
- `CPC/Maps/` — `CPC_Centroid_Map_{ImageA}.tif` (label maps with centroid overlays)
- Each subdirectory includes a `README.txt`
- Titles sanitized: extension stripped, non-filename chars → underscore

## Batch Output (`CPC/` subdirectory tree)
- `CPC/Objects/` — `CPC_Batch_Summary.csv` (pivoted wide: one row per source image, targets as column groups) + `CPC_Batch_Objects_{channel}.csv` (per-object wide format)
- `CPC/Folder/` — `CPC_Batch_Folder_Summary.csv` (aggregated per folder) + per-channel splits
- `CPC/Multi/` — `CPC_Batch_Multi_Summary.csv` (combination pattern counts: Pattern, Coloc/Contains/Either Count + %) + per-channel splits
- Each subdirectory includes a `README.txt`
- Batch does NOT save individual per-group CSVs; only batch-level aggregated files

## Git & Versioning
- **No Co-Authored-By lines** in commits — do not add Claude co-author tags
- **Versioning**: `MAJOR.MINOR.PATCH` (no `-SNAPSHOT`). MAJOR = new major feature or architectural rework, MINOR = substantial change to existing feature, PATCH = bug fix or small tweak. Bump appropriately in `pom.xml` when making changes.

## Key Design Decisions
- `package-name` property set explicitly in pom.xml to avoid module name derivation issues
- ROI .zip loading reads entries manually via `RoiDecoder(byte[], String)` — no RoiManager dependency
- Overlapping ROIs: later ROI overwrites earlier (acceptable for non-overlapping segmentations)
- CPCDialog sequential retrieval (`getNextChoice/Boolean/String`) — order must match widget creation order
- Intensity-weighted path uses `double[8]` accumulator with geometric fallback for zero-intensity objects
- `ToggleSwitch.addChangeListener(Runnable)` — not javax ChangeListener
- CPCBatch uses direct field references (not sequential getters) since it builds its own dialog
- CPCAnalysis `displayResults` flag gates `rt.show()`/`map.show()` calls; `savePrefix` prepends to auto-saved filenames
- Batch groups via regex capture group: varying group replaced with `*` to form group key; raw images matched by channel + context key
- Folder summary aggregation: Objects must be counted once per unique source image, NOT once per target row in long format (see `docs/fixed_bugs/folder-summary-objects-multiplied-by-targets.md`)
