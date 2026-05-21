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
import ij.gui.Roi;

import java.io.File;
import java.io.IOException;

/**
 * Public facade for converting ROI sets into CPC-compatible label images.
 */
public final class CPCLabelImages {

    private CPCLabelImages() {
    }

    public static Roi[] loadRoiSet(String path) throws IOException {
        return LabelUtils.loadRoiSet(path);
    }

    public static ImagePlus fromRois(ImagePlus reference, Roi[] rois) {
        validate(reference, rois);
        return LabelUtils.roiSetToLabelImage(reference, rois);
    }

    public static ImagePlus fromRoiSetFile(ImagePlus reference, String path) throws IOException {
        if (path == null || path.trim().length() == 0) {
            throw new IllegalArgumentException("ROI set path must not be empty.");
        }
        ImagePlus labels = fromRois(reference, loadRoiSet(path));
        labels.setTitle(baseNameWithoutExtension(path));
        return labels;
    }

    private static void validate(ImagePlus reference, Roi[] rois) {
        if (reference == null) {
            throw new IllegalArgumentException("Reference image must not be null.");
        }
        if (rois == null) {
            throw new IllegalArgumentException("ROI array must not be null.");
        }
        if (rois.length == 0) {
            throw new IllegalArgumentException("ROI array must not be empty.");
        }
    }

    private static String baseNameWithoutExtension(String path) {
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
