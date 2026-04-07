package cpc;

import ij.measure.ResultsTable;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Regression test: pivotFolderSummary must not multiply the Objects
 * denominator by the number of target channels. Each source image's
 * object count should be counted once, not once per target pair.
 */
public class CPCBatchFolderSummaryTest {

    /**
     * Reproduces the bug: a source image with 100 objects compared against
     * 3 targets produced Objects=300 (3x) and percentages 3x too low.
     */
    @Test
    public void test_objects_not_multiplied_by_target_count() {
        // Regex: group 1 = channel, group 2 = region
        Pattern pat = Pattern.compile("(.+?)_objects_(.+)\\.tif");
        int varyingGroup = 1;

        // Build a long-format summary: one source image vs 3 targets
        ResultsTable longFmt = new ResultsTable();
        String[] targets = {"DAPI", "GFAP", "mCherry"};
        int sourceObjects = 100;
        int[] colocCounts = {40, 20, 10}; // 40%, 20%, 10%

        for (int i = 0; i < targets.length; i++) {
            int r = longFmt.getCounter();
            longFmt.incrementCounter();
            longFmt.setValue("Folder", r, "animal1");
            longFmt.setValue("Group", r, "objects_LH_SCN");
            longFmt.setValue("Image", r, "CK1d_objects_LH_SCN.tif");
            longFmt.setValue("vs", r, targets[i] + "_objects_LH_SCN.tif");
            longFmt.addValue("Objects", sourceObjects);
            longFmt.addValue("vs Objects", 80);
            longFmt.addValue("Colocalized", colocCounts[i]);
            longFmt.addValue("% Colocalized", colocCounts[i]);
            longFmt.addValue("Contains", 5);
            longFmt.addValue("% Contains", 5);
            longFmt.addValue("Coloc or Contains", colocCounts[i] + 2);
            longFmt.addValue("% Coloc or Contains", colocCounts[i] + 2);
        }

        ResultsTable result = CPCBatch.pivotFolderSummary(longFmt, pat, varyingGroup);

        assertEquals("Should produce one row for (animal1, CK1d)", 1, result.getCounter());

        // Objects must be 100, NOT 300
        assertEquals("Objects count must not be multiplied by target count",
                100.0, result.getValue("Objects", 0), 0.01);

        // DAPI colocalized = 40 out of 100 = 40%
        assertEquals(40.0, result.getValue("DAPI % Colocalized", 0), 0.01);
        // GFAP colocalized = 20 out of 100 = 20%
        assertEquals(20.0, result.getValue("GFAP % Colocalized", 0), 0.01);
        // mCherry colocalized = 10 out of 100 = 10%
        assertEquals(10.0, result.getValue("mCherry % Colocalized", 0), 0.01);
    }

    /**
     * Multiple groups in one folder: objects summed across groups,
     * but still only once per source image (not per target).
     */
    @Test
    public void test_objects_summed_across_groups_not_targets() {
        Pattern pat = Pattern.compile("(.+?)_objects_(.+)\\.tif");
        int varyingGroup = 1;

        ResultsTable longFmt = new ResultsTable();
        // Group 1: 100 objects, 50 colocalized with DAPI
        // Group 2: 60 objects, 30 colocalized with DAPI
        String[][] groups = {
                {"objects_LH_SCN", "100", "50"},
                {"objects_RH_SCN", "60", "30"},
        };
        for (String[] g : groups) {
            for (String tgt : new String[]{"DAPI", "GFAP"}) {
                int r = longFmt.getCounter();
                longFmt.incrementCounter();
                longFmt.setValue("Folder", r, "animal1");
                longFmt.setValue("Group", r, g[0]);
                longFmt.setValue("Image", r, "CK1d_" + g[0] + ".tif");
                longFmt.setValue("vs", r, tgt + "_" + g[0] + ".tif");
                longFmt.addValue("Objects", Double.parseDouble(g[1]));
                longFmt.addValue("vs Objects", 80);
                int coloc = tgt.equals("DAPI") ? Integer.parseInt(g[2]) : 10;
                longFmt.addValue("Colocalized", coloc);
                longFmt.addValue("% Colocalized", coloc);
                longFmt.addValue("Contains", 0);
                longFmt.addValue("% Contains", 0);
                longFmt.addValue("Coloc or Contains", coloc);
                longFmt.addValue("% Coloc or Contains", coloc);
            }
        }

        ResultsTable result = CPCBatch.pivotFolderSummary(longFmt, pat, varyingGroup);

        assertEquals(1, result.getCounter());
        // 100 + 60 = 160 (NOT 100*2 + 60*2 = 320)
        assertEquals(160.0, result.getValue("Objects", 0), 0.01);
        // DAPI: (50+30)/160 = 50%
        assertEquals(50.0, result.getValue("DAPI % Colocalized", 0), 0.01);
    }
}
