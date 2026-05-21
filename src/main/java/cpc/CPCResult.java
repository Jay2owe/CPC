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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output bundle returned by the public CPC Java API.
 */
public final class CPCResult {

    private final CPCParameters parameters;
    private final CPCAnalysis analysis;
    private final List<CPCAnalysis.DirectionResult> directionResults;
    private final ResultsTable consolidatedTable;
    private final ResultsTable summaryTable;
    private final List<CPCAnalysis.MultiTargetResult> multiTargetResults;
    private final List<ResultsTable> multiTargetPerObjectTables;
    private final ResultsTable multiTargetSummaryTable;
    private final List<ImagePlus> centroidLabelMaps;

    CPCResult(CPCParameters parameters,
              CPCAnalysis analysis,
              ResultsTable consolidatedTable,
              ResultsTable summaryTable,
              List<ResultsTable> multiTargetPerObjectTables,
              ResultsTable multiTargetSummaryTable,
              List<ImagePlus> centroidLabelMaps) {
        this.parameters = parameters;
        this.analysis = analysis;
        this.directionResults = immutableCopy(analysis.getResults());
        this.consolidatedTable = consolidatedTable;
        this.summaryTable = summaryTable;
        this.multiTargetResults = immutableCopy(analysis.getMultiTargetResults());
        this.multiTargetPerObjectTables = immutableCopy(multiTargetPerObjectTables);
        this.multiTargetSummaryTable = multiTargetSummaryTable;
        this.centroidLabelMaps = immutableCopy(centroidLabelMaps);
    }

    public CPCParameters getParameters() {
        return parameters;
    }

    public List<CPCAnalysis.DirectionResult> getDirectionResults() {
        return directionResults;
    }

    public ResultsTable getConsolidatedTable() {
        return consolidatedTable;
    }

    public ResultsTable getSummaryTable() {
        return summaryTable;
    }

    public List<CPCAnalysis.MultiTargetResult> getMultiTargetResults() {
        return multiTargetResults;
    }

    public List<ResultsTable> getMultiTargetPerObjectTables() {
        return multiTargetPerObjectTables;
    }

    public ResultsTable getMultiTargetSummaryTable() {
        return multiTargetSummaryTable;
    }

    public List<ImagePlus> getCentroidLabelMaps() {
        return centroidLabelMaps;
    }

    public int getImageCount() {
        return parameters.getImages().size();
    }

    public int getComparisonCount() {
        return directionResults.size();
    }

    CPCAnalysis analysis() {
        return analysis;
    }

    private static <T> List<T> immutableCopy(List<T> input) {
        if (input == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(input));
    }
}
