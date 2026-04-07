# Folder summary Objects count multiplied by number of targets
**Date**: 2026-04-07
**Files changed**: `src/main/java/cpc/CPCBatch.java`
**Guard**: `src/test/java/cpc/CPCBatchFolderSummaryTest.java` — `test_objects_not_multiplied_by_target_count`, `test_objects_summed_across_groups_not_targets`

## What went wrong
The `pivotFolderSummary` method aggregates per-object summary rows across groups within a folder. It sums the "Objects" column (total source objects) from the long-format summary table. However, in long format, each source image appears once per target channel (e.g., CK1d vs DAPI, CK1d vs GFAP, CK1d vs mCherry = 3 rows, all with Objects=100). The sum counted 300 instead of 100, making all colocalization percentages ~3x too low (e.g., 15% instead of 45%).

## The broken pattern
```java
for (int r = 0; r < longFmt.getCounter(); r++) {
    // ...
    v[0] += longFmt.getValue("Objects", r);  // BUG: runs for every row, including duplicate source images
    // ...
}
```

## The fix
Track which source images have already been counted using a Set. Only add the Objects value on first encounter of each unique image:
```java
LinkedHashSet<String> countedImages = new LinkedHashSet<String>();
// ...
String imgKey = folder + "|" + image;
if (countedImages.add(imgKey)) {   // returns false if already seen
    v[0] += longFmt.getValue("Objects", r);
}
```

## Why it matters
If this fix is reverted, the Objects denominator in folder-level summaries will be inflated by the number of target channels, producing systematically deflated percentages. With 3 targets, all percentages are ~3x too low. This silently produces plausible but wrong numbers — the kind of bug that propagates into publications.
