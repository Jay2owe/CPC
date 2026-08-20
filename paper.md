---
title: 'CPC: segmentation-agnostic object-based colocalization for Fiji/ImageJ'
tags:
  - Fiji
  - ImageJ
  - microscopy
  - colocalization
  - image analysis
  - histology
authors:
  - name: Jamie Malcolm
    orcid: 0009-0008-3862-2776
    corresponding: true
    affiliation: 1
  - name: Marco Brancaccio
    affiliation: 1
affiliations:
  - name: UK Dementia Research Institute at Imperial College London, United Kingdom
    index: 1
date: 20 August 2026
bibliography: paper.bib
---

# Summary

`CPC` (Centre-Particle Coincidence) is a Fiji/ImageJ [@Schindelin2012; @Schneider2012]
plugin that quantifies colocalization between segmented objects rather than between
pixel intensities. For every object in one channel it tests whether that object's
centroid falls inside a segmented object in another channel, and reports the result
per object, per directed channel pair, and as a dataset-level summary.

The plugin takes label images or ImageJ ROI sets as its only input, so it is
independent of how those objects were produced. Label maps from StarDist
[@Schmidt2018], Cellpose [@Stringer2021], MorphoLibJ [@Legland2016], intensity
thresholding, or manual annotation are all accepted without conversion. `CPC`
handles two to five channels in its dialog and macro interface, and an unbounded
number through its Java application programming interface (API), computing every
pairwise comparison in both directions. Optional multi-target output records which
combination of partners each individual object carries, and centroids may
optionally be intensity-weighted centres of mass computed from the matching raw
images.

Beyond the single-image case, `CPC` provides a batch mode that groups files in a
folder by a user-supplied regular expression with a capture group, previews the
resulting groups before any analysis runs, scans subfolders recursively, and writes
aggregated per-folder summaries into an organised output tree. Every option is
macro-recordable, and a dialog-free Java API opens no windows, writes no files and
requires no active ImageJ instance, making the same analysis available to scripted
and headless pipelines.

# Statement of need

Most colocalization tools in widespread use quantify the co-occurrence or
correlation of pixel intensities: Pearson and Manders coefficients [@Manders1993]
and their automated-threshold variants [@Costes2004]. These measures answer how far
two intensity distributions overlap across an image, which is a different question
from the one most histology and neuroanatomy experiments actually ask — how many
cells of one type also carry a second marker. Intensity-correlation measures are
additionally sensitive to background, spectral bleed-through and unequal expression
levels between channels, and the distinction between co-occurrence and correlation
is a recurring source of misinterpretation [@Dunn2011; @Aaron2018]. Object-based
approaches sidestep these problems by making the segmented object, not the pixel,
the unit of analysis [@Bolte2006].

Existing object-based tools in the ImageJ ecosystem are capable but coupled. DiAna
[@Gilles2017] and TANGO [@Ollion2013] both perform object-based colocalization and
distance analysis in 3D, but each is organised around its own segmentation
workflow, so using a modern deep-learning segmenter upstream means working against
the tool rather than with it. Both are also oriented towards pairs of channels,
whereas multiplexed histology routinely produces four or five markers whose
co-expression patterns are the actual result.

`CPC` addresses this gap with three deliberate design choices. First, segmentation
is fully decoupled: the plugin consumes label images from any source and never
performs segmentation itself, so it inherits improvements in upstream segmenters at
no cost. Second, the multi-channel case is treated as the default rather than an
extension, with all directed pairwise comparisons and explicit combination patterns
reported for each object. Third, everything reachable through the dialog is
reachable through the macro recorder and the Java API, so an analysis prototyped
interactively on one image can be applied unchanged to a folder of hundreds.

The centroid-in-object criterion itself is intentionally simple, and that
simplicity is the point: the result for any single object can be verified by eye
against the optional centroid overlay maps, which matters when a colocalization
count is going into a figure. `CPC` has been used in the authors' laboratory for
marker co-expression analysis in fluorescence histology, and forms the chassis for
a family of related object-analysis plugins. The analysis was developed from April
2026 within a larger histology pipeline from the same laboratory and extracted as a
standalone plugin the following month; its pre-extraction development history is
preserved on the repository's `prehistory` branch.

# Acknowledgements

Developed by Jamie Malcolm in the Brancaccio Lab at the UK Dementia Research
Institute, Imperial College London. This work was supported by the UK Dementia
Research Institute, which receives its core funding from the UK Medical Research
Council, the Alzheimer's Society, and Alzheimer's Research UK. We thank the Fiji
and SciJava communities for the platform on which this plugin is built.

# References
