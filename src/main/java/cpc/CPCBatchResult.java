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

/**
 * Summary returned by the CPC batch file runner.
 */
public final class CPCBatchResult {

    private final int totalGroups;
    private final int validGroups;
    private final int processedGroups;
    private final int skippedGroups;
    private final int errorGroups;
    private final File outputDirectory;

    CPCBatchResult(int totalGroups, int validGroups, int processedGroups,
                   int skippedGroups, int errorGroups, File outputDirectory) {
        this.totalGroups = totalGroups;
        this.validGroups = validGroups;
        this.processedGroups = processedGroups;
        this.skippedGroups = skippedGroups;
        this.errorGroups = errorGroups;
        this.outputDirectory = outputDirectory;
    }

    public int getTotalGroups() {
        return totalGroups;
    }

    public int getValidGroups() {
        return validGroups;
    }

    public int getProcessedGroups() {
        return processedGroups;
    }

    public int getSkippedGroups() {
        return skippedGroups;
    }

    public int getErrorGroups() {
        return errorGroups;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public boolean hasErrors() {
        return errorGroups > 0;
    }
}
