/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package cpc;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Public Java facade for CPC folder batch processing.
 * <p>
 * This API opens image files, closes them after processing, and writes CSV/TIFF
 * outputs when auto-save is enabled. It does not show Swing dialogs.
 */
public final class CPCBatchRunner {

    private CPCBatchRunner() {
    }

    /**
     * Scan the batch input folder and return the same grouping preview used by the UI.
     */
    public static String preview(CPCBatchParameters parameters) {
        CompiledBatch compiled = compile(parameters);
        Map<String, Map<String, List<File>>> groups = CPCBatch.findGroupsRecursive(
                parameters.getLabelFolder(), compiled.labelPattern,
                parameters.getVaryingGroup(), parameters.isRecursive());
        return CPCBatch.previewNestedGroups(groups);
    }

    /**
     * Run CPC over all runnable groups discovered from the batch parameters.
     */
    public static CPCBatchResult run(CPCBatchParameters parameters) {
        CompiledBatch compiled = compile(parameters);
        Map<String, Map<String, List<File>>> groups = CPCBatch.findGroupsRecursive(
                parameters.getLabelFolder(), compiled.labelPattern,
                parameters.getVaryingGroup(), parameters.isRecursive());
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("No matching files found in: "
                    + parameters.getLabelFolder());
        }
        File saveDir = parameters.getSaveDir() != null
                ? parameters.getSaveDir()
                : parameters.getLabelFolder();
        return CPCBatch.runBatch(groups, compiled.labelPattern, parameters.getVaryingGroup(),
                parameters.isCenterOfMass(), parameters.getRawFolder(), compiled.rawPattern,
                parameters.isBidirectional(), parameters.isIncludePerObjectTables(),
                parameters.isIncludeSummaryTable(), parameters.isExtendedData(),
                parameters.isIncludeCentroidLabelMaps(), parameters.isAutoSave(),
                saveDir.getAbsolutePath(), parameters.isSaveSubdirs());
    }

    private static CompiledBatch compile(CPCBatchParameters parameters) {
        validate(parameters);
        Pattern labelPattern;
        try {
            labelPattern = Pattern.compile(parameters.getLabelRegex());
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("Invalid label regex: " + ex.getMessage(), ex);
        }

        Pattern rawPattern = null;
        if (parameters.isCenterOfMass()) {
            try {
                rawPattern = Pattern.compile(parameters.getRawRegex());
            } catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException("Invalid raw regex: " + ex.getMessage(), ex);
            }
        }
        return new CompiledBatch(labelPattern, rawPattern);
    }

    private static void validate(CPCBatchParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("CPC batch parameters must not be null.");
        }
        if (parameters.getLabelFolder() == null) {
            throw new IllegalArgumentException("Label folder must not be null.");
        }
        if (!parameters.getLabelFolder().isDirectory()) {
            throw new IllegalArgumentException("Label folder does not exist: "
                    + parameters.getLabelFolder());
        }
        if (!hasText(parameters.getLabelRegex())) {
            throw new IllegalArgumentException("Label regex must not be empty.");
        }
        if (parameters.getVaryingGroup() < 0) {
            throw new IllegalArgumentException("Varying group must be 0 or greater.");
        }
        if (parameters.isCenterOfMass()) {
            if (parameters.getRawFolder() == null || !parameters.getRawFolder().isDirectory()) {
                throw new IllegalArgumentException("Raw folder is required for center-of-mass batch mode.");
            }
            if (!hasText(parameters.getRawRegex())) {
                throw new IllegalArgumentException("Raw regex is required for center-of-mass batch mode.");
            }
        }
        if (parameters.isAutoSave() && parameters.getSaveDir() != null
                && parameters.getSaveDir().exists()
                && !parameters.getSaveDir().isDirectory()) {
            throw new IllegalArgumentException("Save path is not a directory: "
                    + parameters.getSaveDir());
        }
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static final class CompiledBatch {
        final Pattern labelPattern;
        final Pattern rawPattern;

        CompiledBatch(Pattern labelPattern, Pattern rawPattern) {
            this.labelPattern = labelPattern;
            this.rawPattern = rawPattern;
        }
    }
}
