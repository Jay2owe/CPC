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

import sc.fiji.oc3d.core.ingest.LabelUtils;

/**
 * Public facade for converting ROI sets into CPC-compatible label images.
 * <p>
 * The conversion itself moved to {@code oc3d-core}, where every plugin in the
 * family shares it. The validation stayed here, because these messages are
 * documented CPC behaviour and a caller that catches them by text should not
 * have to change when the implementation moves house.
 * <p>
 * One behaviour change comes with the shared version: the label image is sized
 * to the ROI count rather than always being 16-bit. The old fixed
 * {@code ShortProcessor} wrapped silently above 65,535 ROIs, turning ROI 65,536
 * into background with no error. A set of 300 ROIs now produces an 8-bit image
 * where it used to produce a 16-bit one; the labels themselves, and every
 * measurement taken from them, are unchanged.
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
