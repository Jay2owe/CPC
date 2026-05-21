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
import java.util.Collections;
import java.util.List;

/**
 * Immutable input bundle for CPC analysis.
 */
public final class CPCParameters {

    public static final int MIN_IMAGES = 2;
    public static final int MAX_IMAGES = 5;

    private final List<ImagePlus> images;
    private final List<ImagePlus> rawImages;
    private final boolean bidirectional;
    private final boolean includePerObjectTables;
    private final boolean includeSummaryTable;
    private final boolean extendedData;
    private final boolean includeMultiTarget;
    private final boolean includeCentroidLabelMaps;

    private CPCParameters(Builder builder) {
        this.images = immutableCopy(builder.images);
        this.rawImages = builder.rawImages == null ? null : immutableCopy(builder.rawImages);
        this.bidirectional = builder.bidirectional;
        this.includePerObjectTables = builder.includePerObjectTables;
        this.includeSummaryTable = builder.includeSummaryTable;
        this.extendedData = builder.extendedData;
        this.includeMultiTarget = builder.includeMultiTarget;
        this.includeCentroidLabelMaps = builder.includeCentroidLabelMaps;
    }

    public static Builder builder(List<ImagePlus> images) {
        return new Builder().images(images);
    }

    public static Builder builder(ImagePlus imageA, ImagePlus imageB) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(imageA);
        images.add(imageB);
        return builder(images);
    }

    public List<ImagePlus> getImages() {
        return images;
    }

    /**
     * Returns the optional raw-image list used for intensity-weighted centroids.
     * The list is parallel to {@link #getImages()} and may contain null entries.
     */
    public List<ImagePlus> getRawImages() {
        return rawImages;
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

    public boolean isIncludeMultiTarget() {
        return includeMultiTarget;
    }

    public boolean isIncludeCentroidLabelMaps() {
        return includeCentroidLabelMaps;
    }

    public boolean usesIntensityWeightedCentroids() {
        return rawImages != null;
    }

    private static List<ImagePlus> immutableCopy(List<ImagePlus> input) {
        if (input == null) return null;
        return Collections.unmodifiableList(new ArrayList<ImagePlus>(input));
    }

    public static final class Builder {
        private List<ImagePlus> images = new ArrayList<ImagePlus>();
        private List<ImagePlus> rawImages;
        private boolean bidirectional = true;
        private boolean includePerObjectTables = true;
        private boolean includeSummaryTable = true;
        private boolean extendedData = false;
        private boolean includeMultiTarget = false;
        private boolean includeCentroidLabelMaps = false;

        private Builder() {
        }

        public Builder images(List<ImagePlus> images) {
            this.images = images == null
                    ? new ArrayList<ImagePlus>()
                    : new ArrayList<ImagePlus>(images);
            return this;
        }

        public Builder rawImages(List<ImagePlus> rawImages) {
            this.rawImages = rawImages == null
                    ? null
                    : new ArrayList<ImagePlus>(rawImages);
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

        public Builder includeMultiTarget(boolean includeMultiTarget) {
            this.includeMultiTarget = includeMultiTarget;
            return this;
        }

        public Builder includeCentroidLabelMaps(boolean includeCentroidLabelMaps) {
            this.includeCentroidLabelMaps = includeCentroidLabelMaps;
            return this;
        }

        public CPCParameters build() {
            return new CPCParameters(this);
        }
    }
}
