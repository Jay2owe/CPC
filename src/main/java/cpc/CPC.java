/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package cpc;

import ij.ImagePlus;
import ij.measure.ResultsTable;

import java.util.Collections;
import java.util.List;

/**
 * Public Java facade for running CPC without opening dialogs or result windows.
 */
public final class CPC {

    private CPC() {
    }

    public static CPCResult run(ImagePlus imageA, ImagePlus imageB) {
        return run(CPCParameters.builder(imageA, imageB).build());
    }

    public static CPCResult run(List<ImagePlus> images) {
        return run(CPCParameters.builder(images).build());
    }

    public static CPCResult run(CPCParameters parameters) {
        validate(parameters);

        CPCAnalysis analysis = parameters.getRawImages() != null
                ? new CPCAnalysis(parameters.getImages(), parameters.getRawImages(), parameters.isBidirectional())
                : new CPCAnalysis(parameters.getImages(), parameters.isBidirectional());
        analysis.setDisplayResults(false);
        analysis.run();

        if (parameters.isIncludeMultiTarget()) {
            analysis.runMultiTarget();
        }

        ResultsTable consolidated = parameters.isIncludePerObjectTables()
                ? analysis.getConsolidatedTable(parameters.isExtendedData())
                : null;
        ResultsTable summary = parameters.isIncludeSummaryTable()
                ? analysis.getSummaryTable()
                : null;
        List<ResultsTable> multiPerObject = parameters.isIncludeMultiTarget()
                ? analysis.getMultiTargetPerObjectTables(parameters.isExtendedData())
                : Collections.<ResultsTable>emptyList();
        ResultsTable multiSummary = parameters.isIncludeMultiTarget()
                ? analysis.getMultiTargetSummaryTable()
                : null;
        List<ImagePlus> maps = parameters.isIncludeCentroidLabelMaps()
                ? analysis.getCentroidLabelMaps()
                : Collections.<ImagePlus>emptyList();

        return new CPCResult(parameters, analysis, consolidated, summary,
                multiPerObject, multiSummary, maps);
    }

    private static void validate(CPCParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("CPC parameters must not be null.");
        }
        List<ImagePlus> images = parameters.getImages();
        if (images == null || images.size() < CPCParameters.MIN_IMAGES) {
            throw new IllegalArgumentException("CPC requires at least 2 label images.");
        }
        if (images.size() > CPCParameters.MAX_IMAGES) {
            throw new IllegalArgumentException("CPC supports at most 5 label images.");
        }
        for (int i = 0; i < images.size(); i++) {
            ImagePlus image = images.get(i);
            if (image == null) {
                throw new IllegalArgumentException("Label image " + (i + 1) + " is null.");
            }
            if (image.getStack() == null) {
                throw new IllegalArgumentException("Label image " + (i + 1) + " has no stack.");
            }
            for (int j = i + 1; j < images.size(); j++) {
                if (image == images.get(j)) {
                    throw new IllegalArgumentException("Label images " + (i + 1)
                            + " and " + (j + 1) + " refer to the same ImagePlus.");
                }
            }
        }

        List<ImagePlus> rawImages = parameters.getRawImages();
        if (rawImages == null) return;
        if (rawImages.size() != images.size()) {
            throw new IllegalArgumentException("Raw image list must match label image count.");
        }
        for (int i = 0; i < rawImages.size(); i++) {
            ImagePlus raw = rawImages.get(i);
            if (raw == null) continue;
            ImagePlus label = images.get(i);
            if (raw.getWidth() != label.getWidth()
                    || raw.getHeight() != label.getHeight()
                    || raw.getStackSize() != label.getStackSize()) {
                throw new IllegalArgumentException("Raw image " + (i + 1)
                        + " dimensions do not match label image " + (i + 1) + ".");
            }
        }
    }
}
