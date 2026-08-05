# Changelog

All notable changes to CPC are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and this project
adheres to [Semantic Versioning](https://semver.org/).

## [1.5.0] - 2026-08-05

The analysis engine moved into `cpc-core`, an embeddable module compiled into
this jar and into any other plugin that wants to offer centroid coincidence.
**The install story is unchanged: one jar, one update site, no prerequisites.**
Nothing new to download, and no module a user ever sees.

Extraction is a refactor, so outputs were gated against goldens captured from
the 1.4.0 build — 409 fixture/configuration comparisons, with **zero
differences that were not one of the deliberate changes below**. Every
remaining difference was traced to one of them; none were left unexplained.

### Changed
- **Object order in every per-object table is now ascending by label.** It was
  first-voxel-encounter order, so a batch CSV could run 1, 2, 4, 3 depending on
  where objects happened to sit. The values in each row are unchanged; only the
  order is. This also sorts the `Contains Partner Labels` cell, which lists the
  same labels it always did.
- **Labels are read by rounding, not truncation.** A 32-bit label image holding
  2.7 now reads as object 3 rather than object 2, and negative, NaN and
  overflowing values are treated as background instead of being cast blindly.
  Integer label images — which is nearly all of them — are unaffected.
- **Intensity-weighted centroids skip non-finite voxels.** A single NaN or
  infinite intensity used to make an object's intensity total non-finite, which
  silently dropped that object back to its geometric centroid. The centre of
  mass is now computed from the voxels that carry a measurement.
- **ROI sets produce a label image sized to the ROI count.** Previously always
  16-bit, which wrapped silently above 65,535 ROIs and turned ROI 65,536 into
  background. A set of 300 ROIs now yields an 8-bit image; labels and every
  measurement taken from them are identical, and a saved centroid map for an ROI
  run may record a smaller bit depth than before.
- **Centroid maps: where two markers overlap, the later one wins.** With the new
  object order, that can be the other object's label. Marker positions and
  labels are otherwise unchanged.

- **The multi-target summary always carries a `None` row**, including when its
  count is zero. It previously appeared only when some object matched nothing,
  so it vanished on exactly the datasets where everything colocalized — and a
  script indexing rows positionally to read the non-colocalized count would
  instead read the totals row, silently, with a plausible-looking number. The
  row's position is unchanged where it already appeared.
- **The Java API no longer rejects more than five label images.** The five was a
  dialog limit that had leaked into `CPC.run`: folder batch processing has
  always analysed larger groups, so the same six images succeeded from a folder
  and were rejected from Java. `CPCParameters.MAX_IMAGES` remains, and now
  describes the dialog and macro slots rather than a cap. The dialog and the
  macro grammar still expose five.

### Fixed
- **Batch no longer dies on a regex with an optional capture group.** A pattern
  such as `(.+?)_objects_(?:x(.+))?\.tif` matches filenames where the varying
  group does not participate; CPC threw
  `StringIndexOutOfBoundsException` before opening a single image, taking the
  whole batch with it. Files that cannot be assigned to a channel are now
  skipped and the rest of the batch runs.
- **Recursive batch scans terminate on a directory cycle.** A junction or
  symlink pointing at an ancestor — ordinary on Windows and in synced folders —
  used to recurse until the stack ran out.
- Raw-image matching now ignores a raw file whose varying capture group did not
  participate, rather than pairing it by a partial key.

### Internal
- `cpc.LabelUtils` and `cpc.ui.ToggleSwitch` were deleted; both now come from
  `oc3d-core`, where the rest of the plugin family shares them. Neither was
  public API. `cpc.CPCLabelImages` keeps its own validation messages verbatim.
- `cpc.CPC`, `CPCParameters`, `CPCResult`, `CPCBatchRunner`,
  `CPCBatchParameters`, `CPCBatchResult`, `CPCLabelImages`, `CPCMacroOptions`
  and `CPCMacroOptionsParser` are unchanged, and are excluded from shading so
  that existing Java callers and recorded macros keep working.

## [1.4.0] - 2026-05-17

### Changed
- Switched the current release line to the BSD 3-Clause License, with
  matching root `LICENSE`, Maven metadata, source headers, and
  `CITATION.cff` metadata.
- Updated README installation instructions to use the live ImageJ update
  site: `https://sites.imagej.net/Center-Particle-Coincidence/`.
- Rebuilt the public repository as a minimal ImageJ/Fiji plugin surface.

### Removed
- Removed local/private release-prep notes, agent context, and private
  path references from the public Git history.
- Removed non-essential public extras such as screenshots, local tools,
  public test sources, and generated/internal documentation.

[1.5.0]: https://github.com/Jay2owe/CPC/releases/tag/v1.5.0
[1.4.0]: https://github.com/Jay2owe/CPC/releases/tag/v1.4.0
