# CPC Colocalisation vs Volumetric Colocalisation

## Overview

When studying the brain using fluorescent markers, researchers often need to know whether two proteins appear in the same place. "Colocalisation" is the formal term for this: measuring whether signals from two different markers overlap in three-dimensional space. The IHF Analysis Pipeline offers two fundamentally different ways to measure colocalisation, each answering a different question. Understanding which to use — and what each one actually tells you — is essential for interpreting your results correctly.


## Volumetric Colocalisation

### What it means

Volumetric colocalisation measures the physical overlap between two objects by counting shared space. For every detected object in one marker channel, the pipeline calculates what percentage of that object's total volume is also occupied by an object from a partner channel.

### In plain terms

"How much of this object is physically sitting inside a partner object?" The answer is a percentage, from zero (no overlap at all) to one hundred (completely enclosed).

### How it works

The pipeline examines every tiny unit of three-dimensional space (a voxel, the 3D equivalent of a pixel) inside each object. It checks whether that same point in space is also occupied by an object from the partner channel. If an object has one thousand voxels and three hundred of them overlap with a partner, the volumetric colocalisation for that object is thirty percent.

When an object overlaps with more than one partner, only the best match — the partner with the highest overlap — is reported.

### When it works well

Volumetric colocalisation excels when you care about the **degree** of spatial overlap between structures. It is particularly useful when:

- You want to quantify how much of one cell type's marker physically merges with another marker.
- You need a graded measure rather than a simple yes-or-no answer.
- Object sizes are similar enough that percentage overlap is a meaningful comparison.

### When it can be misleading

- **Large objects dominate**: A very large object may only overlap a tiny sliver of a small partner, yet the small partner could show a high overlap percentage because most of its volume falls within the large one. The same physical overlap can yield very different percentages depending on which direction you measure from.
- **Threshold sensitivity**: The pipeline uses a threshold (typically thirty percent) to decide whether an object counts as "colocalised" or not. Objects sitting close to this cutoff may flip between colocalised and non-colocalised with small changes in segmentation.
- **Partial overlap is ambiguous**: A forty percent overlap might mean two objects are genuinely merging, or it might mean one object's irregular shape happens to protrude into another's territory. The number alone does not distinguish these cases.

### Worked example

Imagine you have two fluorescent markers: one labelling astrocytes (GFAP) and one labelling neurons (NeuN).

A particular GFAP-labelled astrocyte occupies 500 voxels. Of those 500 voxels, 200 are also occupied by a NeuN-labelled object. The volumetric colocalisation for this astrocyte with NeuN is:

> 200 / 500 = 40%

With the default threshold of thirty percent, this astrocyte **would** be counted as colocalised with NeuN, because forty percent exceeds the thirty percent cutoff.

Meanwhile, the NeuN object occupies 2,000 voxels. Of those, the same 200 overlap with the astrocyte:

> 200 / 2,000 = 10%

From the neuron's perspective, this is only ten percent overlap — below the threshold — so the neuron would **not** be counted as colocalised with GFAP.

This asymmetry is a key feature of volumetric colocalisation: the same physical overlap can be significant from one direction but not the other.


## CPC Colocalisation (Centre-Particle Coincidence)

### What it means

CPC colocalisation tests a single, precise question for each object: does the very centre of this object fall inside a partner object? The "centre" is the centroid — the average position of all the object's voxels, essentially its centre of mass. If that single point lands within the boundaries of any object in the partner channel, the object is marked as colocalised. If not, it is not.

### In plain terms

"Is the middle of this object sitting inside a partner object?" The answer is simply yes or no.

### How it works

For each detected object, the pipeline calculates the centroid — the single point that represents the average position of the entire object. It then checks whether that point falls within the boundaries of any object from the partner channel. If the centroid lands inside a partner, the object is marked as colocalised (a value of one). If it lands in empty space or outside all partners, it is not colocalised (a value of zero).

The pipeline also counts the reverse: for each object, how many partner centroids fall inside it? This gives a "containment count" — a measure of how many partner objects are centred within a given structure.

### When it works well

CPC is ideal when you care about **containment** — whether one structure is located within another. It is particularly useful when:

- You want to know if small objects (such as nuclei or puncta) are physically inside larger structures (such as cell bodies).
- You need a clean binary answer without threshold ambiguity.
- You are studying compartmentalisation — which cellular compartment contains which signals.

### When it can be misleading

- **Insensitive to partial overlap**: Two objects could share a substantial volume of overlap, but if neither centroid falls inside the other, CPC reports no colocalisation. This means CPC can miss cases where objects are closely intermingled but not actually containing one another.
- **Centroid position can be unintuitive**: For irregularly shaped objects (crescents, rings, branching structures), the centroid may fall outside the object itself or at an edge. In these cases, the centroid test may not reflect the true spatial relationship.
- **Size-blind**: CPC treats a tiny punctum the same as a massive cell body. A tiny object whose centre just barely lands inside a large partner is marked identically to a small object completely enclosed within a large one.

### Worked example

Consider the same astrocyte and neuron from before.

The astrocyte has an irregular, star-shaped body. Its centroid — the average position of all 500 voxels — lands at a specific point in space. When the pipeline checks the neuron's label image at that exact point, it finds a neuron object there. The astrocyte is marked as CPC-colocalised with NeuN: **yes**.

Now consider the neuron. Its centroid — the average of its 2,000 voxels — falls in the middle of the neuron's cell body, which does not overlap with the astrocyte at all (the overlap was at the edge). The neuron is marked as CPC-colocalised with GFAP: **no**.

Additionally, the pipeline counts that one NeuN centroid did NOT fall inside this particular astrocyte, so the astrocyte's "containment count" for NeuN is zero for this pair. But if three other NeuN centroids happened to fall within the astrocyte's boundaries, the containment count would be three.


## How These Concepts Relate to Each Other

Volumetric and CPC colocalisation are **complementary, not competing** measures. They answer fundamentally different questions about the same spatial data:

| Question | Use this method |
|----------|----------------|
| How much physical overlap is there between two objects? | Volumetric |
| Is one object located inside another? | CPC |
| What fraction of a population shows significant overlap? | Volumetric (with threshold) |
| How many small objects are contained within a large structure? | CPC (containment count) |
| Which combination of markers does each object colocalise with? | CPC (pattern summary) |

**They can disagree.** An object can have high volumetric overlap but fail CPC (the overlap is at the edges, not where the centroid sits). Conversely, an object can pass CPC but have low volumetric overlap (the centroid sits inside a partner, but only a sliver of volume actually overlaps). Neither result is wrong — they are measuring different things.

**Volumetric gives you a spectrum; CPC gives you a category.** Volumetric overlap is a continuous percentage that describes how much two structures merge. CPC is a binary classification that describes whether one structure is inside another. If you need nuance about the degree of overlap, use volumetric. If you need a clean count of contained objects, use CPC.

**Both are directional.** Measuring "A colocalised with B" can give a different result from "B colocalised with A" for both methods. With volumetric, this is because the denominator (total volume) differs. With CPC, this is because centroids are in different positions. The pipeline always computes both directions.


## Interpretation Guide

**If your question is...**

- **"What percentage of my astrocytes overlap with neurons?"** — Use volumetric colocalisation. Look at the count of objects exceeding the overlap threshold, expressed as a percentage of total objects.

- **"How much do astrocytes and neurons physically merge?"** — Use volumetric colocalisation. Look at the mean overlap percentage across all objects.

- **"Are these puncta inside cell bodies?"** — Use CPC. The binary colocalisation flag tells you whether each punctum's centre is inside a cell body.

- **"How many small objects does each large structure contain?"** — Use CPC containment counts. This tells you how many partner centroids fall within each object's boundaries.

- **"Which markers does each object colocalise with?"** — Use CPC pattern summaries. These list which partner channels each object is centred within.

- **"I want a simple colocalised-or-not count for my population"** — Either method works, but they will likely give different numbers. Volumetric counts depend on your chosen threshold. CPC counts depend on centroid positioning. Report which method you used.

- **"My objects are irregularly shaped — which method is more reliable?"** — Volumetric is generally more robust for irregular shapes, since it considers the entire volume rather than a single point. CPC centroids can be misleading for crescents, rings, or branching structures where the average position may not represent the object well.


## Quick Reference

- **Volumetric colocalisation**: The percentage of one object's volume that overlaps with a partner object. A graded measure from zero to one hundred percent.

- **CPC colocalisation (Centre-Particle Coincidence)**: A yes-or-no test of whether one object's centroid falls inside a partner object. A binary containment measure.

- **Colocalisation threshold**: The minimum volumetric overlap percentage (typically thirty percent) required to classify an object as "colocalised" in summary counts. Does not apply to CPC.

- **Containment count**: The number of partner centroids that fall within a given object's boundaries. A CPC-derived measure of how many partners a structure contains.

- **Colocalisation pattern**: A CPC-derived label listing which partner channels each object is centred within (for example, "GFAP + NeuN" or "None").

- **Directionality**: Both methods can give different results depending on which channel is the source and which is the partner. Always note which direction you are reporting.
