# CPC Integration into IHF Analysis Pipeline — Implementation Plan

## Overview

Add CPC (Centre-Particle Coincidence) centroid-based colocalization to the IHF Analysis Pipeline as a complement to the existing volumetric overlap method. CPC determines colocalization by checking whether each object's centroid falls inside an object in the partner channel's label image.

## Project Locations

- **CPC Plugin**: `C:\Users\jamie\UK Dementia Research Institute Dropbox\Brancaccio Lab\Jamie\Experiments\CPC\`
  - Maven: `io.github.jay2owe:CPC:1.1.0` (JitPack: `com.github.Jay2owe:CPC:v1.1.0`)
  - GitHub: `https://github.com/Jay2owe/CPC` (public)
  - Key class: `cpc.CPCAnalysis` — constructors, `run()`, `extractObjects()`, `testCoincidence()`, `copyObjects()`

- **IHF Pipeline**: `C:\Users\jamie\UK Dementia Research Institute Dropbox\Brancaccio Lab\Jamie\Experiments\IHF Pipeline\IHF Analysis Pipeline Plugin\IHF Analysis Pipeline\`
  - Maven: `io.github.jay2owe:IHF-Analysis-Pipeline:3.0.0`
  - Key files:
    - `src/main/java/if_analysis/pipeline/analyses/ThreeDObjectAnalysis.java` (3,038 lines)
    - `src/main/java/if_analysis/pipeline/analyses/SpatialAnalysis.java` (771 lines)
    - `src/main/java/if_analysis/pipeline/objects/ObjectsCounter3DWrapper.java`
    - `src/main/java/if_analysis/pipeline/ui/PipelineDialog.java`
    - `pom.xml`

## How CPC Works (for the implementing agent)

CPC's core operation is simple:

1. `extractObjects(ImagePlus labelImage)` — scans a label image, finds all unique non-zero pixel values, computes each object's centroid (cx, cy, cz) and voxel count. Returns `List<ObjectInfo>`.
2. `testCoincidence(List<ObjectInfo> objects, ImagePlus targetLabelImage)` — for each object, rounds its centroid to the nearest pixel, looks up the pixel value at that position in the target label image. Sets `obj.partnerLabel` to that value (0 = background = not colocalized, >0 = colocalized with that partner object).
3. `copyObjects(List<ObjectInfo> originals)` — deep copy so each pairwise test gets independent state.

`ObjectInfo` is a public static inner class: `CPCAnalysis.ObjectInfo` with fields `label`, `cx`, `cy`, `cz`, `voxelCount`, `partnerLabel`, and method `isColocalized()`.

## How IHF's Existing Colocalization Works

### 3D Object Analysis (ThreeDObjectAnalysis.java)

- Produces per-channel label images via 3D Objects Counter or StarDist
- Label images stored in an in-memory registry as `{channelName}_objects`
- Label images saved to disk at: `Image Analysis/{AnimalName}/{Channel}_objects_{Hemisphere}_{Region}`
- Per-channel object CSVs written to `Data Analysis/Objects/{Channel}.csv`
- Existing colocalization: `computeColocFromLabelImages()` (lines 2892-2982) computes volumetric pixel overlap percentage per object pair
- Results written via `writeColocValuesForThisImage()` which matches rows by metadata (SCN, Animal Name, Hemisphere, Region, ROI)
- Column name pattern: `Colocalisation with {Channel}` (0-100% float)
- Columns pre-created by `ensureAllColocColumns()`

### Object CSV Structure

**Existing columns**: Volume (micron^3), Surface (micron^2), IntDen, Mean, XM, YM, ZM, SCN, Animal Name, Hemisphere, Region, ROI, Colocalisation with {Channel}

**IMPORTANT**: There is NO Label column currently. Objects are ordered by mcib3d population iteration order. The Label column will be added as part of this plan.

### Spatial Analysis (SpatialAnalysis.java)

- Runs AFTER 3D Object Analysis, reads object CSVs from `Data Analysis/Objects/`
- Adds calibrated centroid columns (XM_um, YM_um, ZM_um)
- Computes pairwise nearest-neighbor distances in 3D Euclidean space
- Groups objects by SCN column for per-section processing
- Output: distance columns appended to object CSVs + spatial statistics CSVs

### Label Image Naming Convention

Saved to disk as: `Image Analysis/{AnimalName}/{Channel}_objects_{Hemisphere}_{Region}`
Example: `Image Analysis/Mouse1/mCherry_objects_LH_SCN`

---

## Implementation Phases

### Phase 0 — CPC Library Cleanup

**File**: `CPC/src/main/java/cpc/CPCAnalysis.java`

Make three methods `public static` (they use no instance state):

1. `public static void testCoincidence(List<ObjectInfo> objects, ImagePlus targetImage)` — currently `public void`
2. `public static List<ObjectInfo> copyObjects(List<ObjectInfo> originals)` — currently `public`
3. `public static List<ObjectInfo> extractObjects(ImagePlus img)` — currently `public`
4. `public static List<ObjectInfo> extractObjects(ImagePlus img, ImagePlus rawImg)` — currently `public`

**Note**: These methods are also called internally by `run()`, `runMultiTarget()`, etc. Making them static is safe — instance methods can call static methods. But verify that no internal call uses `this` implicitly.

**Also**: The `getOrExtractObjects()` private method calls `extractObjects()` — this will still work since instance methods can call static methods.

Bump version to **1.2.0** in `pom.xml`. Update `CLAUDE.md` jar filename references. Tag `v1.2.0`, push.

### Phase 1 — Add CPC Dependency to IHF Pipeline

**File**: `IHF Analysis Pipeline/pom.xml`

Add JitPack repository:
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
```

Add dependency:
```xml
<dependency>
    <groupId>com.github.Jay2owe</groupId>
    <artifactId>CPC</artifactId>
    <version>v1.2.0</version>
</dependency>
```

Verify it compiles before proceeding.

### Phase 2 — Dialog Changes in ThreeDObjectAnalysis

**File**: `ThreeDObjectAnalysis.java`

In the options dialog section (~line 362), add two independent toggles BEFORE the existing "Colocalisation Thresholds" header:

```java
gdOpts.addHeader("Colocalization Method");
ToggleSwitch volumetricToggle = gdOpts.addToggle("Volumetric overlap (%)", true);
gdOpts.addHelpText("Percentage of object voxels overlapping with partner channel");
ToggleSwitch cpcToggle = gdOpts.addToggle("Centroid coincidence (CPC)", true);
gdOpts.addHelpText("Whether each object's centroid falls inside a partner object");
```

Read both in the sequential retrieval block:
```java
final boolean doVolumetric = gdOpts.getNextBoolean();
final boolean doCpc = gdOpts.getNextBoolean();
```

The existing coloc threshold section remains — it applies to volumetric mode only.

**Conditional execution**: Gate `appendColocColumns()` with `if (doVolumetric)`. Gate the new CPC method with `if (doCpc)`.

### Phase 3 — Label Column + CPC Colocalization in ThreeDObjectAnalysis

**File**: `ThreeDObjectAnalysis.java`

#### 3a. Add Label Column

In `appendStatsToChannelTable()` or wherever rows are written from the mcib3d population stats table, add a `Label` column containing the object's pixel value from the label image:

```java
// When iterating population to build stats:
int label = (int) objects.get(i).getLabel();
rt.addValue("Label", label);
```

This column is written for ALL runs going forward, regardless of CPC toggle.

#### 3b. New Method: `appendCpcColocColumns()`

Called after all label images exist, similar to `appendColocColumns()`:

```java
private void appendCpcColocColumns(
    BinConfig cfg,
    boolean[] channelHasObjects,
    Map<String, ResultsTable> channelTables,
    int scnIndex, String animalName, String hemisphere,
    String region, String roiLabel)
```

Logic for each unique channel pair (a < b):

```
1. Get label images from registry:
   ImagePlus aObjImg = getRegisteredImage(aChannel + "_objects");
   ImagePlus bObjImg = getRegisteredImage(bChannel + "_objects");

2. Get ResultsTables:
   ResultsTable tableA = channelTables.get(aChannel);
   ResultsTable tableB = channelTables.get(bChannel);

3. Build ObjectInfo lists from existing centroids in the tables:
   - Iterate rows matching (scnIndex, animalName, hemisphere, region, roiLabel)
   - For each matching row:
     CPCAnalysis.ObjectInfo obj = new CPCAnalysis.ObjectInfo(labelValue);
     obj.cx = tableA.getValue("XM", row);
     obj.cy = tableA.getValue("YM", row);
     obj.cz = tableA.getValue("ZM", row);
   - Get label from the new Label column: (int) tableA.getValue("Label", row)

4. Forward test (A centroids in B label image):
   List<ObjectInfo> copyA = CPCAnalysis.copyObjects(objectsA);
   CPCAnalysis.testCoincidence(copyA, bObjImg);
   → Write to A's table:
     "CPC Coloc with {B}"   → partnerLabel > 0 ? 1 : 0
     "CPC Partner {B}"      → partnerLabel

5. Reverse test (B centroids in A label image):
   List<ObjectInfo> copyB = CPCAnalysis.copyObjects(objectsB);
   CPCAnalysis.testCoincidence(copyB, aObjImg);
   → Write to B's table:
     "CPC Coloc with {A}"   → partnerLabel > 0 ? 1 : 0
     "CPC Partner {A}"      → partnerLabel

6. Containment (derived from reverse results):
   Build map from reverse: partnerLabel → count
   (each B object's partnerLabel is the A label it fell inside)
   For each A object: look up own label in the map → "CPC Contains {B}" count
   Mirror for B using forward results → "CPC Contains {A}" count
```

Use the same `writeColocValuesForThisImage()` pattern to write float arrays matched by metadata.

#### 3c. Column Pre-creation

Add `ensureAllCpcColocColumns()` mirroring `ensureAllColocColumns()`:
- For each channel pair: pre-create `CPC Coloc with {X}`, `CPC Partner {X}`, `CPC Contains {X}` with 0 defaults
- Also pre-create `CPC Targets Hit` (0) and `CPC Pattern` ("None")

### Phase 4 — Multi-Target Columns in ThreeDObjectAnalysis

**File**: `ThreeDObjectAnalysis.java`

After ALL pairwise CPC tests complete (all pairs for current section), compute multi-target columns:

```java
private void appendCpcMultiTargetColumns(
    BinConfig cfg, Map<String, ResultsTable> channelTables,
    int scnIndex, String animalName, String hemisphere,
    String region, String roiLabel)
```

For each channel A, for each matching row:
1. Read all `CPC Coloc with {X}` columns for this row
2. Count how many are 1 → write to `CPC Targets Hit`
3. Build pattern string from channel names where value is 1:
   - Join with " + " → e.g. "GFAP + NeuN"
   - If none → "None"
   - Write to `CPC Pattern`

### Phase 5 — Retroactive CPC in Spatial Analysis

**File**: `SpatialAnalysis.java`

#### 5a. Dialog Addition

Add toggle in Spatial Analysis dialog:

```java
gdOpts.addHeader("CPC Colocalization");
ToggleSwitch cpcToggle = gdOpts.addToggle("Run CPC analysis", true);
gdOpts.addHelpText("Centroid-in-object colocalization from saved label images. "
    + "If CPC columns already exist from 3D Object Analysis, uses those for summaries.");
```

#### 5b. Detection: Already Computed?

Check if `CPC Coloc with {X}` columns exist in the first channel's object CSV. If yes → skip computation, go to aggregation. If no → run retroactive CPC.

#### 5c. Retroactive CPC Computation

For each channel pair (A, B), for each section group (grouped by SCN + metadata):

```
1. Construct label image paths from CSV metadata:
   basePath = "Image Analysis/" + animalName + "/"
   aPath = basePath + channelA + "_objects_" + hemisphere + "_" + region
   bPath = basePath + channelB + "_objects_" + hemisphere + "_" + region

2. Load label images:
   ImagePlus aLabelImg = IJ.openImage(aPath + ".tif");  // try common extensions
   ImagePlus bLabelImg = IJ.openImage(bPath + ".tif");
   If missing → log warning, skip this section

3. Build ObjectInfo for A from CSV rows (XM, YM, ZM):
   For each CSV row in this section group:
     ObjectInfo obj = new ObjectInfo(rowLabel);
     obj.cx = row.XM; obj.cy = row.YM; obj.cz = row.ZM;
   
   Label resolution (two-tier):
   a. If "Label" column exists → use it directly
   b. If not (legacy data) → self-lookup:
      Load A's own label image, look up pixel at (XM, YM, ZM) for each row
      That pixel value = the row's label

4. Run CPC tests (same as Phase 3 steps 4-6)

5. Write results as new columns in the object CSV

6. After all pairs: compute multi-target columns (same as Phase 4)

7. Close loaded images to free memory
```

#### 5d. Aggregation & Summary CSVs

After CPC columns exist (either pre-existing or just computed):

**`Data Analysis/Spatial/CPC_Spatial_Summary.csv`**:

| Animal Name | Hemisphere | Region | ROI | Source | vs | Objects | CPC Colocalized | CPC % | CPC Contains | CPC Contains % |
|---|---|---|---|---|---|---|---|---|---|---|

Aggregated per section: count objects, count CPC Coloc = 1, compute percentage. Same for Contains > 0.

**`Data Analysis/Spatial/CPC_Multi_Target_Summary.csv`**:

| Animal Name | Hemisphere | Region | ROI | Source | Pattern | Count | % |
|---|---|---|---|---|---|---|---|

Aggregated per section: group by CPC Pattern value, count occurrences, compute percentage of source objects.

### Phase 6 — Conditional Execution Summary

| Volumetric Toggle | CPC Toggle | 3D Object Analysis Behavior |
|---|---|---|
| ON | OFF | Current behavior only: `Colocalisation with {X}` columns |
| OFF | ON | CPC columns only: `CPC Coloc/Partner/Contains with {X}`, `CPC Targets Hit`, `CPC Pattern` |
| ON | ON | Both sets of columns |
| OFF | OFF | No colocalization columns (Label column still written) |

Spatial Analysis CPC toggle is independent — runs CPC retroactively if columns missing, aggregates if present.

---

## New Columns Added to Object CSVs

Written after existing columns, in this order:

| Column | Type | Description |
|---|---|---|
| `Label` | int | Object's pixel value in its own label image (always written) |
| `CPC Coloc with {Channel}` | int (0/1) | Centroid falls inside a partner object |
| `CPC Partner {Channel}` | int | Partner object's label value (0 if none) |
| `CPC Contains {Channel}` | int | Count of partner centroids inside this object |
| `CPC Targets Hit` | int | Number of other channels this object colocalizes with |
| `CPC Pattern` | string | Combination pattern e.g. "GFAP + NeuN" or "None" |

One set of `CPC Coloc/Partner/Contains` columns per partner channel. `Targets Hit` and `Pattern` appear once at the end.

---

## Critical Implementation Notes (from code review)

### BLOCKER 1: Column Whitelist

`ThreeDObjectAnalysis.java` has a `ResultsTableCleaner.keepOnlyColumns()` call (~line 613) with a hardcoded whitelist (`baseHeadings` array, ~line 582). A parallel whitelist exists at ~line 1376 in `writeTempTables()`. Any column NOT in the whitelist is silently stripped before CSV save.

**Action**: Update BOTH `keep` lists to include:
- `Label` — unconditionally
- `CPC Coloc with {X}`, `CPC Partner {X}`, `CPC Contains {X}` — for each partner channel, when CPC enabled
- `CPC Targets Hit`, `CPC Pattern` — when CPC enabled

### BLOCKER 2: XM/YM/ZM Coordinate System

CPC's `testCoincidence()` uses centroid coordinates as pixel indices. The mcib3d native path stores pixel coordinates (`MASS_CENTER_X_PIX` at ObjectsCounter3DWrapper line 385-387). BUT the legacy Counter3D path stores **calibrated** coordinates (microns) via `c_mass[0..2]` (ObjectsCounter3DWrapper lines 545-554).

**Action**: Before calling `testCoincidence`, detect coordinate system:
- If using native mcib3d path → XM/YM/ZM are already pixels, use directly
- If using legacy Counter3D path → convert back to pixels: `px = calibrated / pixelWidth`
- In Phase 5 (retroactive): check if calibration info is available; if XM values are much larger than image width in pixels, they're likely calibrated

### WARNING: Label Image Path Construction (Phase 5)

The label image filename suffix uses `roiLabel` (the `ROI` column in the CSV), NOT the `Region` column. The actual save code at ThreeDObjectAnalysis ~line 802 calls `saveObjectsImages` with `roiLabel`. The `buildFileSuffix` method (~line 1358) constructs: `{hemisphere}_{roiLabel}`.

**Action**: In Phase 5c, construct paths using:
```
Image Analysis/{Animal Name}/{Channel}_objects_{Hemisphere}_{ROI}.tif
```
NOT `_{Region}`. If `ROI` column is empty, fall back to `Region`.

### WARNING: Label Column Source

The plan's Phase 3a says to add Label in `appendStatsToChannelTable`, but that method doesn't have access to the mcib3d population object. Instead, add the Label column in `ObjectsCounter3DWrapper.buildNativeStatisticsTable()` (~line 348):
```java
rt.setValue("Label", i, (int) obj.getLabel());
```
Then it flows through automatically when stats are copied to the channel table.

### WARNING: CPC Pattern is a String Column

`ResultsTable.setValue(String colName, int row, String value)` must be used for the `CPC Pattern` column, not the numeric `setValue`. The `writeColocValuesForThisImage` helper only handles floats. Need a separate write path for String columns, or write Pattern values directly in the multi-target method.

### WARNING: Headless/CLI Mode

ThreeDObjectAnalysis supports `suppressDialogs` mode (headless). The new `doVolumetric` and `doCpc` booleans need defaults (both `true`) for headless execution. Check if `CLIArgumentParser` in `IHFPipeline.java` needs corresponding CLI flags.

### WARNING: Self-Lookup Fallback for Concave Objects

In Phase 5c step 3b (legacy data without Label column), the self-lookup may fail for concave objects whose centroid falls outside their own boundary. Pixel at centroid = 0 (background) or wrong label.

**Action**: If self-lookup returns 0, search a small 3x3x3 neighborhood around the centroid for the most common non-zero label. If still 0, log a warning and skip the object for containment.

### WARNING: Memory Management in Phase 5

Loading pairs of 3D label images for every section can be memory-heavy. Process one section fully before loading the next. Call `image.flush()` after `image.close()` since ImageJ retains pixel arrays until GC.

---

## Testing Checklist

- [ ] CPC static methods work (extractObjects, testCoincidence, copyObjects)
- [ ] JitPack serves CPC v1.2.0 correctly
- [ ] IHF compiles with CPC dependency
- [ ] Volumetric ON + CPC OFF = unchanged behavior
- [ ] Volumetric OFF + CPC ON = only CPC columns
- [ ] Both ON = both column sets
- [ ] Both OFF = no coloc columns, Label column still present
- [ ] Spatial Analysis detects existing CPC columns and skips recomputation
- [ ] Spatial Analysis retroactively computes CPC from saved label images
- [ ] Self-lookup works for legacy data without Label column
- [ ] Multi-target columns computed correctly
- [ ] Summary CSVs produced with correct aggregation
- [ ] Large dataset: memory management (close loaded images after each section)
