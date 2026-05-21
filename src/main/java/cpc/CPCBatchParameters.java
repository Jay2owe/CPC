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
 * Immutable input bundle for CPC folder batch processing.
 */
public final class CPCBatchParameters {

    private final File labelFolder;
    private final String labelRegex;
    private final int varyingGroup;
    private final boolean recursive;
    private final boolean centerOfMass;
    private final File rawFolder;
    private final String rawRegex;
    private final boolean bidirectional;
    private final boolean includePerObjectTables;
    private final boolean includeSummaryTable;
    private final boolean extendedData;
    private final boolean includeCentroidLabelMaps;
    private final boolean autoSave;
    private final File saveDir;
    private final boolean saveSubdirs;

    private CPCBatchParameters(Builder builder) {
        this.labelFolder = builder.labelFolder;
        this.labelRegex = builder.labelRegex;
        this.varyingGroup = builder.varyingGroup;
        this.recursive = builder.recursive;
        this.centerOfMass = builder.centerOfMass;
        this.rawFolder = builder.rawFolder;
        this.rawRegex = builder.rawRegex;
        this.bidirectional = builder.bidirectional;
        this.includePerObjectTables = builder.includePerObjectTables;
        this.includeSummaryTable = builder.includeSummaryTable;
        this.extendedData = builder.extendedData;
        this.includeCentroidLabelMaps = builder.includeCentroidLabelMaps;
        this.autoSave = builder.autoSave;
        this.saveDir = builder.saveDir;
        this.saveSubdirs = builder.saveSubdirs;
    }

    public static Builder builder(File labelFolder, String labelRegex, int varyingGroup) {
        return new Builder(labelFolder, labelRegex, varyingGroup);
    }

    public File getLabelFolder() {
        return labelFolder;
    }

    public String getLabelRegex() {
        return labelRegex;
    }

    public int getVaryingGroup() {
        return varyingGroup;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public boolean isCenterOfMass() {
        return centerOfMass;
    }

    public File getRawFolder() {
        return rawFolder;
    }

    public String getRawRegex() {
        return rawRegex;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public boolean isIncludePerObjectTables() {
        return includePerObjectTables;
    }

    public boolean isIncludeSummaryTable() {
        return includeSummaryTable;
    }

    public boolean isExtendedData() {
        return extendedData;
    }

    public boolean isIncludeCentroidLabelMaps() {
        return includeCentroidLabelMaps;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public File getSaveDir() {
        return saveDir;
    }

    public boolean isSaveSubdirs() {
        return saveSubdirs;
    }

    public static final class Builder {
        private final File labelFolder;
        private final String labelRegex;
        private final int varyingGroup;
        private boolean recursive = true;
        private boolean centerOfMass = false;
        private File rawFolder;
        private String rawRegex;
        private boolean bidirectional = true;
        private boolean includePerObjectTables = true;
        private boolean includeSummaryTable = true;
        private boolean extendedData = false;
        private boolean includeCentroidLabelMaps = false;
        private boolean autoSave = true;
        private File saveDir;
        private boolean saveSubdirs = false;

        private Builder(File labelFolder, String labelRegex, int varyingGroup) {
            this.labelFolder = labelFolder;
            this.labelRegex = labelRegex;
            this.varyingGroup = varyingGroup;
        }

        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public Builder centerOfMass(boolean centerOfMass) {
            this.centerOfMass = centerOfMass;
            return this;
        }

        public Builder rawFolder(File rawFolder) {
            this.rawFolder = rawFolder;
            return this;
        }

        public Builder rawRegex(String rawRegex) {
            this.rawRegex = rawRegex;
            return this;
        }

        public Builder bidirectional(boolean bidirectional) {
            this.bidirectional = bidirectional;
            return this;
        }

        public Builder includePerObjectTables(boolean includePerObjectTables) {
            this.includePerObjectTables = includePerObjectTables;
            return this;
        }

        public Builder includeSummaryTable(boolean includeSummaryTable) {
            this.includeSummaryTable = includeSummaryTable;
            return this;
        }

        public Builder extendedData(boolean extendedData) {
            this.extendedData = extendedData;
            return this;
        }

        public Builder includeCentroidLabelMaps(boolean includeCentroidLabelMaps) {
            this.includeCentroidLabelMaps = includeCentroidLabelMaps;
            return this;
        }

        public Builder autoSave(boolean autoSave) {
            this.autoSave = autoSave;
            return this;
        }

        public Builder saveDir(File saveDir) {
            this.saveDir = saveDir;
            return this;
        }

        public Builder saveSubdirs(boolean saveSubdirs) {
            this.saveSubdirs = saveSubdirs;
            return this;
        }

        public CPCBatchParameters build() {
            return new CPCBatchParameters(this);
        }
    }
}
