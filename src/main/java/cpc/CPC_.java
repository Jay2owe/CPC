/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package cpc;

import cpc.ui.CPCDialog;
import cpc.ui.ToggleSwitch;
import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GraphicsEnvironment;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CPC — Centre-Particle Coincidence.
 * <p>
 * Object-based colocalization that accepts any label image (StarDist,
 * Cellpose, threshold, etc.) or ROI set and determines colocalization
 * by testing whether each object's centroid falls inside a segmented
 * object in the other channel. Supports 2–5 images with all pairwise
 * comparisons.
 */
public class CPC_ implements PlugIn {

    private static final String NONE = "(none)";
    private static final int MAX_IMAGES = 5;
    private static final String[] LETTERS = {"A", "B", "C", "D", "E"};
    private static final FileNameExtensionFilter IMAGE_FILTER =
            new FileNameExtensionFilter("Images (*.tif, *.tiff, *.png)", "tif", "tiff", "png");

    @Override
    public void run(String arg) {
        String macroOptions = Macro.getOptions();
        if (!hasText(macroOptions) && hasText(arg)) {
            macroOptions = arg;
        }
        if (hasText(macroOptions) || GraphicsEnvironment.isHeadless()) {
            runMacro(macroOptions);
            return;
        }

        String[] openTitles = getImageTitles();
        boolean hasOpen = openTitles.length > 0;
        String[] dropdownItems;
        if (hasOpen) {
            dropdownItems = new String[openTitles.length + 1];
            dropdownItems[0] = NONE;
            System.arraycopy(openTitles, 0, dropdownItems, 1, openTitles.length);
        } else {
            dropdownItems = new String[]{NONE};
        }

        // ── Build dialog ───────────────────────────────────────────

        CPCDialog d = new CPCDialog("CPC \u2014 Centre-Particle Coincidence");

        d.addHeader("Input");
        JComboBox<String> modeCombo = d.addChoice("Input mode",
                new String[]{"Label Images", "ROI Sets"}, "Label Images");
        d.addHelpText("Label Images: 2\u20135 label/object maps. "
                + "ROI Sets: load ROI .zip files and a reference image.");

        // Label images group — up to 5 slots
        JPanel labelGroup = d.beginGroup();
        for (int i = 0; i < MAX_IMAGES; i++) {
            String defaultTitle = NONE;
            if (hasOpen && i < openTitles.length) defaultTitle = openTitles[i];
            d.addChoice("Image " + LETTERS[i], dropdownItems, defaultTitle);
            d.addFileField("or browse " + LETTERS[i], "", IMAGE_FILTER);
        }
        d.addHelpText("Fill in at least 2 images. Leave unused slots as \"(none)\".");
        d.endGroup();

        // ROI sets group (hidden by default) — reference image + up to 5 ROI sets
        JPanel roiGroup = d.beginGroup();
        d.addChoice("Reference image", dropdownItems, hasOpen ? openTitles[0] : NONE);
        d.addFileField("or browse ref", "", IMAGE_FILTER);
        for (int i = 0; i < MAX_IMAGES; i++) {
            d.addFileField("ROI Set " + LETTERS[i], "");
        }
        d.addHelpText("Fill in at least 2 ROI sets. Leave unused slots empty.");
        d.endGroup();
        roiGroup.setVisible(false);

        modeCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                boolean isLabel = "Label Images".equals(e.getItem());
                labelGroup.setVisible(isLabel);
                roiGroup.setVisible(!isLabel);
                d.repack();
            }
        });

        d.addHeader("Analysis");
        d.addToggle("Bidirectional", true);
        d.addHelpText("Test both directions for each pair (e.g. A\u2192B and B\u2192A)");

        ToggleSwitch comToggle = d.addToggle("Intensity-weighted centroids (center of mass)", false);
        d.addHelpText("Use a raw image per channel to weight centroids by intensity");

        JPanel comGroup = d.beginGroup();
        for (int i = 0; i < MAX_IMAGES; i++) {
            d.addChoice("Raw image " + LETTERS[i], dropdownItems, NONE);
            d.addFileField("or browse raw " + LETTERS[i], "", IMAGE_FILTER);
        }
        d.addHelpText("Match each raw image to the corresponding label image above.");
        d.endGroup();
        comGroup.setVisible(false);

        comToggle.addChangeListener(() -> {
            comGroup.setVisible(comToggle.isSelected());
            d.repack();
        });

        d.addHeader("Output");
        d.addToggle("Per-object tables (vs)", true);
        d.addHelpText("Per-object table per pair: colocalized + contains columns");
        d.addToggle("Summary table", true);
        d.addHelpText("Counts and percentages overview");
        d.addToggle("Extended data", false);
        d.addHelpText("Include volume and centroid coordinates in per-object tables");
        d.addToggle("Multi-target summary", false);
        d.addHelpText("Combination analysis: for each object, which targets it colocalizes with");
        d.addToggle("Centroid label maps", false);
        d.addHelpText("Label image with the other channels' centroids overlaid");

        ToggleSwitch saveToggle = d.addToggle("Auto-save results", false);
        d.addHelpText("Save all output tables (CSV) and maps (TIFF) to a directory");

        JPanel saveGroup = d.beginGroup();
        JTextField saveDirField = d.addDirectoryField("Save directory", "");
        d.endGroup();
        saveGroup.setVisible(false);

        saveToggle.addChangeListener(() -> {
            boolean on = saveToggle.isSelected();
            saveGroup.setVisible(on);
            if (on && saveDirField.getText().trim().isEmpty()) {
                String dir = IJ.getDirectory("image");
                if (dir != null) saveDirField.setText(dir);
            }
            d.repack();
        });

        d.setOnOK(new Runnable() {
            @Override
            public void run() {
                // ── Read values (on EDT) ───────────────────────────────
                final String mode = d.getNextChoice();  // Input mode

                // Label image slots (dropdown + file for each)
                final String[] titles = new String[MAX_IMAGES];
                final String[] files = new String[MAX_IMAGES];
                for (int i = 0; i < MAX_IMAGES; i++) {
                    titles[i] = d.getNextChoice();
                    files[i] = d.getNextString();
                }

                // ROI sets slots
                final String refTitle = d.getNextChoice();
                final String refFile = d.getNextString();
                final String[] roiPaths = new String[MAX_IMAGES];
                for (int i = 0; i < MAX_IMAGES; i++) {
                    roiPaths[i] = d.getNextString();
                }

                // Raw image slots for intensity-weighted centroids
                final String[] rawTitles = new String[MAX_IMAGES];
                final String[] rawFiles = new String[MAX_IMAGES];
                for (int i = 0; i < MAX_IMAGES; i++) {
                    rawTitles[i] = d.getNextChoice();
                    rawFiles[i] = d.getNextString();
                }

                final boolean bidirectional = d.getNextBoolean();
                final boolean comWeighted = d.getNextBoolean();
                final boolean perObject = d.getNextBoolean();
                final boolean showSummary = d.getNextBoolean();
                final boolean extendedData = d.getNextBoolean();
                final boolean multiTarget = d.getNextBoolean();
                final boolean centroidMaps = d.getNextBoolean();
                final boolean autoSave = d.getNextBoolean();
                final String saveDirPath = d.getNextString();

                // ── Resolve images ────────────────────────────────────
                final AtomicReference<List<ImagePlus>> imagesRef = new AtomicReference<List<ImagePlus>>();
                final AtomicReference<List<ImagePlus>> rawRef = new AtomicReference<List<ImagePlus>>();

                if ("Label Images".equals(mode)) {
                    List<ImagePlus> resolved = new ArrayList<ImagePlus>();
                    List<ImagePlus> rawResolved = new ArrayList<ImagePlus>();
                    for (int i = 0; i < MAX_IMAGES; i++) {
                        ImagePlus img = resolveImage(titles[i], files[i]);
                        if (img != null) {
                            resolved.add(img);
                            ImagePlus raw = comWeighted ? resolveImage(rawTitles[i], rawFiles[i]) : null;
                            if (raw != null && (raw.getWidth() != img.getWidth()
                                    || raw.getHeight() != img.getHeight()
                                    || raw.getStackSize() != img.getStackSize())) {
                                IJ.error("CPC", "Raw image " + LETTERS[i]
                                        + " dimensions do not match label image " + LETTERS[i] + ".");
                                return;
                            }
                            rawResolved.add(raw);
                        }
                    }
                    if (resolved.size() < 2) {
                        IJ.error("CPC", "Please select at least 2 label images.");
                        return;
                    }
                    // Check for duplicates
                    for (int i = 0; i < resolved.size(); i++) {
                        for (int j = i + 1; j < resolved.size(); j++) {
                            if (resolved.get(i) == resolved.get(j)) {
                                IJ.error("CPC", "Image " + LETTERS[i] + " and Image " + LETTERS[j]
                                        + " are the same image.");
                                return;
                            }
                        }
                    }
                    imagesRef.set(resolved);
                    rawRef.set(comWeighted ? rawResolved : null);
                } else {
                    ImagePlus ref = resolveImage(refTitle, refFile);
                    if (ref == null) {
                        IJ.error("CPC", "Reference image: select an open image or browse for a file.");
                        return;
                    }
                    List<ImagePlus> resolved = new ArrayList<ImagePlus>();
                    for (int i = 0; i < MAX_IMAGES; i++) {
                        if (roiPaths[i] == null || roiPaths[i].trim().isEmpty()) continue;
                        try {
                            Roi[] rois = LabelUtils.loadRoiSet(roiPaths[i]);
                            if (rois.length == 0) {
                                IJ.error("CPC", "ROI Set " + LETTERS[i] + " is empty.");
                                return;
                            }
                            resolved.add(LabelUtils.roiSetToLabelImage(ref, rois));
                        } catch (Exception ex) {
                            IJ.error("CPC", "Error loading ROI Set " + LETTERS[i] + ":\n" + ex.getMessage());
                            return;
                        }
                    }
                    if (resolved.size() < 2) {
                        IJ.error("CPC", "Please provide at least 2 ROI set files.");
                        return;
                    }
                    imagesRef.set(resolved);
                    rawRef.set(null);
                }

                final CPCMacroOptions recordedOptions = CPCMacroOptions.fromDialogValues(
                        mode, titles, files, refTitle, refFile, roiPaths,
                        rawTitles, rawFiles, bidirectional, comWeighted,
                        perObject, showSummary, extendedData, multiTarget,
                        centroidMaps, autoSave, saveDirPath);
                recordMacroCall(recordedOptions);

                // ── Run analysis in background thread ──────────────────
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<ImagePlus> imgs = imagesRef.get();
                        List<ImagePlus> raws = rawRef.get();
                        IJ.showStatus("CPC: running analysis on " + imgs.size() + " images...");
                        CPCAnalysis analysis = raws != null
                                ? new CPCAnalysis(imgs, raws, bidirectional)
                                : new CPCAnalysis(imgs, bidirectional);
                        if (autoSave) {
                            String dir = saveDirPath.trim();
                            if (dir.isEmpty()) {
                                // Default to directory of first image
                                ij.io.FileInfo fi = imgs.get(0).getOriginalFileInfo();
                                if (fi != null && fi.directory != null) {
                                    dir = fi.directory;
                                } else {
                                    dir = System.getProperty("user.home");
                                }
                            }
                            analysis.setSaveDir(dir);
                        }
                        if (perObject || showSummary || centroidMaps) {
                            analysis.run();
                            if (perObject) analysis.showConsolidatedResults(extendedData);
                            if (showSummary) analysis.showSummaryResults();
                        }
                        if (multiTarget) {
                            analysis.runMultiTarget();
                            if (perObject) analysis.showMultiTargetPerObjectResults(extendedData);
                            analysis.showMultiTargetSummary();
                        }
                        if (centroidMaps) analysis.showCentroidLabelMaps();
                        IJ.showStatus("CPC: done.");
                    }
                }, "CPC-Analysis").start();
            }
        });

        JButton batchBtn = d.addFooterButton("Batch...");
        batchBtn.addActionListener(e -> CPCBatch.showBatchDialog());

        d.showNonBlocking();
    }

    private void runMacro(String optionsText) {
        if (!hasText(optionsText)) {
            reportMacroError("CPC macro/headless execution requires explicit macro options.");
            return;
        }
        try {
            CPCMacroOptions options = CPCMacroOptionsParser.parse(optionsText);
            MacroInput input = resolveMacroInput(options);
            CPCResult result = CPC.run(options.toParameters(input.images, input.rawImages));
            showAndSaveMacroResult(result, options, input.images);
        } catch (Exception ex) {
            reportMacroError(ex.getMessage());
        }
    }

    private MacroInput resolveMacroInput(CPCMacroOptions options) throws Exception {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        List<ImagePlus> rawImages = options.isCenterOfMass()
                ? new ArrayList<ImagePlus>() : null;

        if (options.getMode() == CPCMacroOptions.InputMode.ROIS) {
            ImagePlus reference = resolveImageForMacro(options.getReferenceTitle(),
                    options.getReferencePath(), "reference");
            if (reference == null) {
                throw new IllegalArgumentException("ROI mode requires reference or reference_path.");
            }
            for (int i = 0; i < CPCMacroOptions.MAX_IMAGES; i++) {
                String roiPath = options.getRoiPath(i);
                if (!hasText(roiPath)) continue;
                Roi[] rois = LabelUtils.loadRoiSet(roiPath);
                if (rois.length == 0) {
                    throw new IllegalArgumentException("roi" + (i + 1) + " is empty.");
                }
                ImagePlus labels = LabelUtils.roiSetToLabelImage(reference, rois);
                labels.setTitle(baseNameWithoutExtension(roiPath));
                images.add(labels);
            }
            if (images.size() < 2) {
                throw new IllegalArgumentException("ROI mode requires at least 2 ROI set files.");
            }
            return new MacroInput(images, null);
        }

        for (int i = 0; i < CPCMacroOptions.MAX_IMAGES; i++) {
            ImagePlus image = resolveImageForMacro(options.getImageTitle(i),
                    options.getImagePath(i), "image" + (i + 1));
            if (image == null) continue;
            images.add(image);

            if (rawImages != null) {
                ImagePlus raw = resolveImageForMacro(options.getRawTitle(i),
                        options.getRawPath(i), "raw" + (i + 1));
                if (raw != null && (raw.getWidth() != image.getWidth()
                        || raw.getHeight() != image.getHeight()
                        || raw.getStackSize() != image.getStackSize())) {
                    throw new IllegalArgumentException("raw" + (i + 1)
                            + " dimensions do not match image" + (i + 1) + ".");
                }
                rawImages.add(raw);
            }
        }
        if (images.size() < 2) {
            throw new IllegalArgumentException("Label mode requires at least 2 label images.");
        }
        checkDuplicateImages(images);
        return new MacroInput(images, rawImages);
    }

    private ImagePlus resolveImageForMacro(String title, String path, String label) {
        if (hasText(path)) {
            ImagePlus img = IJ.openImage(path.trim());
            if (img == null) {
                throw new IllegalArgumentException("Could not open " + label + "_path: " + path);
            }
            return img;
        }
        if (hasText(title)) {
            ImagePlus img = WindowManager.getImage(title.trim());
            if (img == null) {
                throw new IllegalArgumentException("Open image not found for " + label + ": " + title);
            }
            return img;
        }
        return null;
    }

    private void showAndSaveMacroResult(CPCResult result, CPCMacroOptions options,
                                        List<ImagePlus> images) {
        CPCAnalysis analysis = result.analysis();
        boolean display = !GraphicsEnvironment.isHeadless() && !options.isHideDisplay();
        analysis.setDisplayResults(display);
        if (options.isAutoSave()) {
            analysis.setSaveDir(defaultSaveDir(options, images));
        }
        if (options.isPerObjectTables()) {
            analysis.showConsolidatedResults(options.isExtendedData());
        }
        if (options.isSummaryTable()) {
            analysis.showSummaryResults();
        }
        if (options.isMultiTarget()) {
            if (options.isPerObjectTables()) {
                analysis.showMultiTargetPerObjectResults(options.isExtendedData());
            }
            analysis.showMultiTargetSummary();
        }
        if (options.isCentroidMaps()) {
            analysis.showCentroidLabelMaps();
        }
        IJ.showStatus("CPC: done.");
    }

    private String defaultSaveDir(CPCMacroOptions options, List<ImagePlus> images) {
        if (hasText(options.getSaveDir())) {
            return options.getSaveDir().trim();
        }
        if (images != null && !images.isEmpty()) {
            ij.io.FileInfo fi = images.get(0).getOriginalFileInfo();
            if (fi != null && fi.directory != null) {
                return fi.directory;
            }
        }
        return System.getProperty("user.home");
    }

    private void checkDuplicateImages(List<ImagePlus> images) {
        for (int i = 0; i < images.size(); i++) {
            for (int j = i + 1; j < images.size(); j++) {
                if (images.get(i) == images.get(j)) {
                    throw new IllegalArgumentException("image" + (i + 1)
                            + " and image" + (j + 1) + " are the same image.");
                }
            }
        }
    }

    private void recordMacroCall(CPCMacroOptions options) {
        if (!Recorder.record) return;
        try {
            Recorder.recordString("run(\"Centre-Particle Coincidence\", \""
                    + options.toMacroOptions() + "\");\n");
        } catch (IllegalArgumentException ex) {
            IJ.log("CPC: Could not record macro options: " + ex.getMessage());
        }
    }

    private void reportMacroError(String message) {
        String text = hasText(message) ? message : "Unknown CPC macro error.";
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log("CPC ERROR: " + text);
        } else {
            IJ.error("CPC", text);
        }
    }

    private static String baseNameWithoutExtension(String path) {
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static final class MacroInput {
        final List<ImagePlus> images;
        final List<ImagePlus> rawImages;

        MacroInput(List<ImagePlus> images, List<ImagePlus> rawImages) {
            this.images = images;
            this.rawImages = rawImages;
        }
    }

    /**
     * Resolve an image from dropdown title or file path.
     * File path takes priority if non-empty.
     */
    private ImagePlus resolveImage(String dropdownTitle, String filePath) {
        if (filePath != null && !filePath.trim().isEmpty()) {
            ImagePlus img = IJ.openImage(filePath.trim());
            if (img == null) {
                IJ.error("CPC", "Could not open file:\n" + filePath);
            }
            return img;
        }
        if (dropdownTitle != null && !NONE.equals(dropdownTitle)) {
            return WindowManager.getImage(dropdownTitle);
        }
        return null;
    }

    private String[] getImageTitles() {
        int[] ids = WindowManager.getIDList();
        if (ids == null) return new String[0];
        String[] titles = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            titles[i] = WindowManager.getImage(ids[i]).getTitle();
        }
        return titles;
    }
}
