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

import java.util.ArrayList;
import java.util.List;

/**
 * Macro-facing CPC options, independent of ImageJ windows and dialogs.
 */
public final class CPCMacroOptions {

    public enum InputMode {
        LABELS,
        ROIS
    }

    public static final int MAX_IMAGES = 5;

    private InputMode mode = InputMode.LABELS;
    private final String[] imageTitles = new String[MAX_IMAGES];
    private final String[] imagePaths = new String[MAX_IMAGES];
    private String referenceTitle;
    private String referencePath;
    private final String[] roiPaths = new String[MAX_IMAGES];
    private final String[] rawTitles = new String[MAX_IMAGES];
    private final String[] rawPaths = new String[MAX_IMAGES];
    private boolean bidirectional = true;
    private boolean centerOfMass = false;
    private boolean perObjectTables = true;
    private boolean summaryTable = true;
    private boolean extendedData = false;
    private boolean multiTarget = false;
    private boolean centroidMaps = false;
    private boolean autoSave = false;
    private boolean hideDisplay = false;
    private String saveDir;

    public InputMode getMode() {
        return mode;
    }

    public void setMode(InputMode mode) {
        this.mode = mode == null ? InputMode.LABELS : mode;
    }

    public String getImageTitle(int index) {
        return imageTitles[index];
    }

    public void setImageTitle(int index, String title) {
        imageTitles[index] = clean(title);
    }

    public String getImagePath(int index) {
        return imagePaths[index];
    }

    public void setImagePath(int index, String path) {
        imagePaths[index] = clean(path);
    }

    public String getReferenceTitle() {
        return referenceTitle;
    }

    public void setReferenceTitle(String referenceTitle) {
        this.referenceTitle = clean(referenceTitle);
    }

    public String getReferencePath() {
        return referencePath;
    }

    public void setReferencePath(String referencePath) {
        this.referencePath = clean(referencePath);
    }

    public String getRoiPath(int index) {
        return roiPaths[index];
    }

    public void setRoiPath(int index, String path) {
        roiPaths[index] = clean(path);
    }

    public String getRawTitle(int index) {
        return rawTitles[index];
    }

    public void setRawTitle(int index, String title) {
        rawTitles[index] = clean(title);
    }

    public String getRawPath(int index) {
        return rawPaths[index];
    }

    public void setRawPath(int index, String path) {
        rawPaths[index] = clean(path);
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
    }

    public boolean isCenterOfMass() {
        return centerOfMass;
    }

    public void setCenterOfMass(boolean centerOfMass) {
        this.centerOfMass = centerOfMass;
    }

    public boolean isPerObjectTables() {
        return perObjectTables;
    }

    public void setPerObjectTables(boolean perObjectTables) {
        this.perObjectTables = perObjectTables;
    }

    public boolean isSummaryTable() {
        return summaryTable;
    }

    public void setSummaryTable(boolean summaryTable) {
        this.summaryTable = summaryTable;
    }

    public boolean isExtendedData() {
        return extendedData;
    }

    public void setExtendedData(boolean extendedData) {
        this.extendedData = extendedData;
    }

    public boolean isMultiTarget() {
        return multiTarget;
    }

    public void setMultiTarget(boolean multiTarget) {
        this.multiTarget = multiTarget;
    }

    public boolean isCentroidMaps() {
        return centroidMaps;
    }

    public void setCentroidMaps(boolean centroidMaps) {
        this.centroidMaps = centroidMaps;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isHideDisplay() {
        return hideDisplay;
    }

    public void setHideDisplay(boolean hideDisplay) {
        this.hideDisplay = hideDisplay;
    }

    public String getSaveDir() {
        return saveDir;
    }

    public void setSaveDir(String saveDir) {
        this.saveDir = clean(saveDir);
        if (hasText(this.saveDir)) this.autoSave = true;
    }

    public CPCParameters toParameters(List<ImagePlus> images, List<ImagePlus> rawImages) {
        return CPCParameters.builder(images)
                .rawImages(centerOfMass ? rawImages : null)
                .bidirectional(bidirectional)
                .includePerObjectTables(perObjectTables)
                .includeSummaryTable(summaryTable)
                .extendedData(extendedData)
                .includeMultiTarget(multiTarget)
                .includeCentroidLabelMaps(centroidMaps)
                .build();
    }

    public String toMacroOptions() {
        List<String> tokens = new ArrayList<String>();
        tokens.add("mode=" + (mode == InputMode.ROIS ? "rois" : "labels"));
        if (mode == InputMode.ROIS) {
            append(tokens, "reference", referenceTitle);
            append(tokens, "reference_path", referencePath);
            for (int i = 0; i < MAX_IMAGES; i++) {
                append(tokens, "roi" + (i + 1), roiPaths[i]);
            }
        } else {
            for (int i = 0; i < MAX_IMAGES; i++) {
                append(tokens, "image" + (i + 1), imageTitles[i]);
                append(tokens, "image" + (i + 1) + "_path", imagePaths[i]);
            }
            if (centerOfMass) {
                tokens.add("center_of_mass");
                for (int i = 0; i < MAX_IMAGES; i++) {
                    append(tokens, "raw" + (i + 1), rawTitles[i]);
                    append(tokens, "raw" + (i + 1) + "_path", rawPaths[i]);
                }
            }
        }
        tokens.add(bidirectional ? "bidirectional" : "unidirectional");
        tokens.add(perObjectTables ? "objects" : "hide_objects");
        tokens.add(summaryTable ? "summary" : "hide_summary");
        if (extendedData) tokens.add("extended");
        if (multiTarget) tokens.add("multi_target");
        if (centroidMaps) tokens.add("centroid_maps");
        if (autoSave) tokens.add("auto_save");
        append(tokens, "save_dir", saveDir);
        if (hideDisplay) tokens.add("hide_display");
        return join(tokens);
    }

    public static CPCMacroOptions fromDialogValues(String modeText,
                                                   String[] imageTitles,
                                                   String[] imagePaths,
                                                   String referenceTitle,
                                                   String referencePath,
                                                   String[] roiPaths,
                                                   String[] rawTitles,
                                                   String[] rawPaths,
                                                   boolean bidirectional,
                                                   boolean centerOfMass,
                                                   boolean perObjectTables,
                                                   boolean summaryTable,
                                                   boolean extendedData,
                                                   boolean multiTarget,
                                                   boolean centroidMaps,
                                                   boolean autoSave,
                                                   String saveDir) {
        CPCMacroOptions options = new CPCMacroOptions();
        options.setMode("ROI Sets".equals(modeText) ? InputMode.ROIS : InputMode.LABELS);
        copySlots(imageTitles, options.imageTitles);
        copySlots(imagePaths, options.imagePaths);
        options.setReferenceTitle(referenceTitle);
        options.setReferencePath(referencePath);
        copySlots(roiPaths, options.roiPaths);
        copySlots(rawTitles, options.rawTitles);
        copySlots(rawPaths, options.rawPaths);
        options.setBidirectional(bidirectional);
        options.setCenterOfMass(centerOfMass);
        options.setPerObjectTables(perObjectTables);
        options.setSummaryTable(summaryTable);
        options.setExtendedData(extendedData);
        options.setMultiTarget(multiTarget);
        options.setCentroidMaps(centroidMaps);
        options.setAutoSave(autoSave);
        if (autoSave) options.setSaveDir(saveDir);
        return options;
    }

    void validate() {
        if (mode == InputMode.ROIS && centerOfMass) {
            throw new IllegalArgumentException("center_of_mass is only supported with mode=labels.");
        }
        for (int i = 0; i < MAX_IMAGES; i++) {
            if (hasText(imageTitles[i]) && hasText(imagePaths[i])) {
                throw new IllegalArgumentException("Use either image" + (i + 1)
                        + " or image" + (i + 1) + "_path, not both.");
            }
            if (hasText(rawTitles[i]) && hasText(rawPaths[i])) {
                throw new IllegalArgumentException("Use either raw" + (i + 1)
                        + " or raw" + (i + 1) + "_path, not both.");
            }
        }
        if (hasText(referenceTitle) && hasText(referencePath)) {
            throw new IllegalArgumentException("Use either reference or reference_path, not both.");
        }
    }

    static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static void append(List<String> tokens, String key, String value) {
        if (!hasText(value)) return;
        tokens.add(key + "=" + encodeValue(value));
    }

    static String encodeValue(String value) {
        String normalized = value.trim().replace('\\', '/');
        if (normalized.indexOf('[') >= 0 || normalized.indexOf(']') >= 0
                || normalized.indexOf('"') >= 0 || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Macro option values must not contain brackets, quotes, or line breaks.");
        }
        return "[" + normalized + "]";
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static void copySlots(String[] source, String[] target) {
        if (source == null) return;
        int max = Math.min(source.length, target.length);
        for (int i = 0; i < max; i++) {
            target[i] = clean(source[i]);
        }
    }

    private static String join(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }
}
