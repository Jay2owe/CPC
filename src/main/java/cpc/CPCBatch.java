package cpc;

import cpc.ui.CPCDialog;
import cpc.ui.ToggleSwitch;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import javax.swing.*;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Batch processing for CPC analysis.
 * <p>
 * Scans a folder (optionally including subfolders) for label images
 * matching a regex, groups them by a varying capture group (e.g.
 * channel name), and runs CPC on each group.
 */
public class CPCBatch {

    /**
     * Opens the batch processing dialog.
     */
    public static void showBatchDialog() {
        CPCDialog d = new CPCDialog("CPC \u2014 Batch Processing");

        // ── Label Images ──────────────────────────────────────────
        d.addHeader("Label Images");
        final JTextField labelFolderField = d.addDirectoryField("Folder", "");
        final JTextField labelRegexField = d.addStringField("Filename regex",
                "(.+?)_objects_(.+)\\.tif", 24);
        final JTextField groupIndexField = d.addNumericField("Varying group", 1, 0);
        d.addHelpText("Regex must match the entire filename. One capture group "
                + "marks the part that varies between paired images (e.g. "
                + "channel name). Set \u201cVarying group\u201d to that group\u2019s index.");
        final ToggleSwitch recursiveToggle = d.addToggle("Include subfolders", true);
        d.addHelpText("Scan subdirectories for matching images. "
                + "Each subfolder is processed separately.");

        // Preview area (hidden until Preview button clicked)
        final JTextArea previewArea = new JTextArea(10, 40);
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        final JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        previewScroll.setVisible(false);

        JButton previewBtn = d.addButton("Preview Groups");
        d.addComponent(previewScroll);

        previewBtn.addActionListener(e -> {
            String folder = labelFolderField.getText().trim();
            String regex = labelRegexField.getText().trim();
            String idxStr = groupIndexField.getText().trim();
            if (folder.isEmpty() || regex.isEmpty()) {
                previewArea.setText("Enter a folder and regex first.");
                previewScroll.setVisible(true);
                d.repack();
                return;
            }
            try {
                Pattern p = Pattern.compile(regex);
                int idx = Integer.parseInt(idxStr);
                boolean recursive = recursiveToggle.isSelected();
                Map<String, Map<String, List<File>>> nested =
                        findGroupsRecursive(new File(folder), p, idx, recursive);
                previewArea.setText(previewNestedGroups(nested));
            } catch (PatternSyntaxException ex) {
                previewArea.setText("Invalid regex: " + ex.getMessage());
            } catch (NumberFormatException ex) {
                previewArea.setText("Invalid group index.");
            }
            previewScroll.setVisible(true);
            d.repack();
        });

        // ── Analysis ──────────────────────────────────────────────
        d.addHeader("Analysis");
        final ToggleSwitch biToggle = d.addToggle("Bidirectional", true);
        d.addHelpText("Test both directions for each pair (e.g. A\u2192B and B\u2192A)");

        final ToggleSwitch comToggle = d.addToggle("Intensity-weighted centroids", false);
        d.addHelpText("Use raw images to weight centroids by intensity");

        JPanel comGroup = d.beginGroup();
        final JTextField rawFolderField = d.addDirectoryField("Raw images folder", "");
        final JTextField rawRegexField = d.addStringField("Raw filename regex",
                "(.+?)_Filtered_(.+)\\.tif", 24);
        d.addHelpText("Same capture group structure as label regex. "
                + "Paired by matching channel name and context groups. "
                + "Subfolder structure must mirror label images folder.");
        d.endGroup();
        comGroup.setVisible(false);

        comToggle.addChangeListener(() -> {
            comGroup.setVisible(comToggle.isSelected());
            d.repack();
        });

        // ── Output ────────────────────────────────────────────────
        d.addHeader("Output");
        final ToggleSwitch perObjectToggle = d.addToggle("Per-object tables (vs)", true);
        d.addHelpText("Per-object table per pair: colocalized + contains columns");
        final ToggleSwitch summaryToggle = d.addToggle("Summary table", true);
        d.addHelpText("Counts and percentages overview");
        final ToggleSwitch extendedToggle = d.addToggle("Extended data", false);
        d.addHelpText("Include volume and centroid coordinates in per-object tables.");
        final ToggleSwitch mapsToggle = d.addToggle("Centroid label maps", false);
        final ToggleSwitch saveToggle = d.addToggle("Auto-save results", true);
        d.addHelpText("Save tables (CSV) and maps (TIFF) to a directory.");

        final JPanel saveGroup = d.beginGroup();
        final JTextField saveDirField = d.addDirectoryField("Save directory", "");
        final ToggleSwitch subdirsToggle = d.addToggle("Save in subdirectories", false);
        d.addHelpText("Off: all files save to the save directory with prefixed names. "
                + "On: results organised into subdirectories per folder and group.");
        d.endGroup();
        saveGroup.setVisible(true);

        saveToggle.addChangeListener(() -> {
            saveGroup.setVisible(saveToggle.isSelected());
            d.repack();
        });

        // ── OK ────────────────────────────────────────────────────
        d.setOnOK(() -> {
            final String labelFolder = labelFolderField.getText().trim();
            final String labelRegex = labelRegexField.getText().trim();
            final int groupIdx;
            try {
                groupIdx = Integer.parseInt(groupIndexField.getText().trim());
            } catch (NumberFormatException ex) {
                IJ.error("CPC Batch", "Invalid varying group index.");
                return;
            }

            if (labelFolder.isEmpty() || labelRegex.isEmpty()) {
                IJ.error("CPC Batch", "Please enter a label images folder and regex.");
                return;
            }

            final Pattern labelPattern;
            try {
                labelPattern = Pattern.compile(labelRegex);
            } catch (PatternSyntaxException ex) {
                IJ.error("CPC Batch", "Invalid label regex:\n" + ex.getMessage());
                return;
            }

            final boolean recursive = recursiveToggle.isSelected();

            final boolean comWeighted = comToggle.isSelected();
            final String rawFolder = rawFolderField.getText().trim();
            final Pattern rawPattern;
            if (comWeighted) {
                if (rawFolder.isEmpty() || rawRegexField.getText().trim().isEmpty()) {
                    IJ.error("CPC Batch",
                            "Intensity-weighted mode requires raw images folder and regex.");
                    return;
                }
                try {
                    rawPattern = Pattern.compile(rawRegexField.getText().trim());
                } catch (PatternSyntaxException ex) {
                    IJ.error("CPC Batch", "Invalid raw regex:\n" + ex.getMessage());
                    return;
                }
            } else {
                rawPattern = null;
            }

            final Map<String, Map<String, List<File>>> nestedGroups =
                    findGroupsRecursive(new File(labelFolder), labelPattern,
                            groupIdx, recursive);
            if (nestedGroups.isEmpty()) {
                IJ.error("CPC Batch", "No matching files found in:\n" + labelFolder);
                return;
            }

            int validCount = 0;
            for (Map<String, List<File>> fg : nestedGroups.values()) {
                for (List<File> g : fg.values()) {
                    if (g.size() >= 2) validCount++;
                }
            }
            if (validCount == 0) {
                IJ.error("CPC Batch",
                        "No groups with 2+ images. Check your regex and varying group.\n"
                                + "Use \u201cPreview Groups\u201d to verify.");
                return;
            }

            final boolean bidirectional = biToggle.isSelected();
            final boolean perObject = perObjectToggle.isSelected();
            final boolean showSummary = summaryToggle.isSelected();
            final boolean extendedData = extendedToggle.isSelected();
            final boolean centroidMaps = mapsToggle.isSelected();
            final boolean autoSave = saveToggle.isSelected();
            final boolean saveSubdirs = subdirsToggle.isSelected();
            String sd = saveDirField.getText().trim();
            if (sd.isEmpty()) sd = labelFolder;
            final String saveDir = sd;

            new Thread(() -> runBatch(nestedGroups, labelPattern, groupIdx,
                    comWeighted, new File(rawFolder), rawPattern,
                    bidirectional, perObject, showSummary, extendedData,
                    centroidMaps, autoSave, saveDir, saveSubdirs),
                    "CPC-Batch").start();
        });

        d.showNonBlocking();
    }

    // ── Grouping ──────────────────────────────────────────────────

    /**
     * Scan a single folder for files matching pattern, group by replacing
     * the varying capture group with a wildcard.
     */
    static Map<String, List<File>> findGroups(File folder, Pattern pattern,
                                               int varyingGroup) {
        Map<String, List<File>> groups = new LinkedHashMap<String, List<File>>();
        File[] files = folder.listFiles();
        if (files == null) return groups;

        Arrays.sort(files);

        for (File f : files) {
            if (!f.isFile()) continue;
            Matcher m = pattern.matcher(f.getName());
            if (!m.matches()) continue;

            String key;
            if (varyingGroup >= 1 && varyingGroup <= m.groupCount()) {
                key = f.getName().substring(0, m.start(varyingGroup))
                        + "*"
                        + f.getName().substring(m.end(varyingGroup));
            } else {
                key = "all";
            }

            List<File> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<File>();
                groups.put(key, list);
            }
            list.add(f);
        }
        return groups;
    }

    /**
     * Scan folder (optionally recursing into subdirectories) and return
     * nested groups: relative folder path &rarr; (group key &rarr; files).
     */
    static Map<String, Map<String, List<File>>> findGroupsRecursive(
            File rootFolder, Pattern pattern, int varyingGroup,
            boolean recursive) {
        Map<String, Map<String, List<File>>> result =
                new LinkedHashMap<String, Map<String, List<File>>>();

        if (recursive) {
            walkDirectories(rootFolder, rootFolder, "", pattern,
                    varyingGroup, result);
        } else {
            Map<String, List<File>> groups =
                    findGroups(rootFolder, pattern, varyingGroup);
            if (!groups.isEmpty()) {
                result.put("", groups);
            }
        }
        return result;
    }

    private static void walkDirectories(File root, File current,
                                         String relativePath, Pattern pattern,
                                         int varyingGroup,
                                         Map<String, Map<String, List<File>>> result) {
        Map<String, List<File>> groups =
                findGroups(current, pattern, varyingGroup);
        if (!groups.isEmpty()) {
            result.put(relativePath, groups);
        }

        File[] subdirs = current.listFiles(File::isDirectory);
        if (subdirs != null) {
            Arrays.sort(subdirs);
            for (File subdir : subdirs) {
                String childPath = relativePath.isEmpty()
                        ? subdir.getName()
                        : relativePath + "/" + subdir.getName();
                walkDirectories(root, subdir, childPath, pattern,
                        varyingGroup, result);
            }
        }
    }

    // ── Preview ───────────────────────────────────────────────────

    /**
     * Build a human-readable preview of the nested group structure.
     */
    static String previewNestedGroups(
            Map<String, Map<String, List<File>>> nestedGroups) {
        if (nestedGroups.isEmpty()) return "No matching files found.";

        int totalFolders = nestedGroups.size();
        int totalGroups = 0;
        int validGroups = 0;
        int totalFiles = 0;
        for (Map<String, List<File>> fg : nestedGroups.values()) {
            totalGroups += fg.size();
            for (List<File> g : fg.values()) {
                totalFiles += g.size();
                if (g.size() >= 2) validGroups++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(totalFolders).append(" folder(s), ")
                .append(totalGroups).append(" group(s), ")
                .append(validGroups).append(" runnable, ")
                .append(totalFiles).append(" files\n\n");

        for (Map.Entry<String, Map<String, List<File>>> folderEntry
                : nestedGroups.entrySet()) {
            String folderPath = folderEntry.getKey();
            Map<String, List<File>> groups = folderEntry.getValue();

            int folderFiles = 0;
            for (List<File> g : groups.values()) folderFiles += g.size();

            String displayFolder = folderPath.isEmpty()
                    ? "(root)" : folderPath + "/";
            sb.append(displayFolder)
                    .append("  (").append(groups.size()).append(" groups, ")
                    .append(folderFiles).append(" files)\n");

            for (Map.Entry<String, List<File>> groupEntry : groups.entrySet()) {
                List<File> files = groupEntry.getValue();
                sb.append("  ").append(groupEntry.getKey())
                        .append("  (").append(files.size())
                        .append(files.size() < 2 ? " \u2014 SKIP" : "")
                        .append(")\n");
                for (File f : files) {
                    sb.append("    ").append(f.getName()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // ── Raw image matching ────────────────────────────────────────

    /**
     * Match raw images to label images by channel (varying group value)
     * and context (non-varying group values).
     *
     * @return parallel list to labelFiles — may contain null entries
     */
    static List<File> matchRawImages(List<File> labelFiles, Pattern labelPattern,
                                      File rawFolder, Pattern rawPattern,
                                      int varyingGroup) {
        // Index raw files by "contextKey|channel"
        Map<String, File> rawLookup = new LinkedHashMap<String, File>();
        File[] rawFiles = rawFolder.listFiles();
        if (rawFiles != null) {
            for (File f : rawFiles) {
                if (!f.isFile()) continue;
                Matcher m = rawPattern.matcher(f.getName());
                if (!m.matches()) continue;
                if (varyingGroup < 1 || varyingGroup > m.groupCount()) continue;
                String channel = m.group(varyingGroup);
                String ctx = buildContextKey(m, varyingGroup);
                rawLookup.put(ctx + "|" + channel, f);
            }
        }

        List<File> result = new ArrayList<File>();
        for (File labelFile : labelFiles) {
            Matcher m = labelPattern.matcher(labelFile.getName());
            if (!m.matches() || varyingGroup < 1
                    || varyingGroup > m.groupCount()) {
                result.add(null);
                continue;
            }
            String channel = m.group(varyingGroup);
            String ctx = buildContextKey(m, varyingGroup);
            result.add(rawLookup.get(ctx + "|" + channel));
        }
        return result;
    }

    private static String buildContextKey(Matcher m, int varyingGroup) {
        StringBuilder ctx = new StringBuilder();
        for (int g = 1; g <= m.groupCount(); g++) {
            if (g == varyingGroup) continue;
            if (ctx.length() > 0) ctx.append("|");
            ctx.append(m.group(g));
        }
        return ctx.toString();
    }

    // ── Batch execution ───────────────────────────────────────────

    /**
     * Derive a clean folder/prefix name from a group key.
     * e.g. "*_objects_LH_SCN.tif" &rarr; "objects_LH_SCN"
     */
    private static String groupDisplayName(String groupKey) {
        String name = groupKey.replace("*", "");
        name = name.replaceAll("^[_\\-./\\\\]+|[_\\-./\\\\]+$", "");
        name = name.replaceAll("[_\\-]{2,}", "_");
        name = name.replaceAll("\\.[^.]+$", "");
        if (name.isEmpty()) name = "batch";
        return name;
    }

    /**
     * Process all groups: open images, run CPC, save results, close.
     */
    static void runBatch(Map<String, Map<String, List<File>>> nestedGroups,
                          Pattern labelPattern, int varyingGroup,
                          boolean comWeighted, File rawRootFolder,
                          Pattern rawPattern,
                          boolean bidirectional,
                          boolean perObject, boolean showSummary,
                          boolean extendedData,
                          boolean centroidMaps,
                          boolean autoSave, String saveDir,
                          boolean saveSubdirs) {
        // Count totals
        int totalGroups = 0;
        int validGroups = 0;
        for (Map<String, List<File>> fg : nestedGroups.values()) {
            for (List<File> g : fg.values()) {
                totalGroups++;
                if (g.size() >= 2) validGroups++;
            }
        }

        // Long-format summary (pivoted at the end)
        ResultsTable batchSummaryLong = new ResultsTable();
        // Object pivot: srcCh → (objKey → columnMap)
        Map<String, LinkedHashMap<String, Map<String, Object>>> pivotObjects =
                new LinkedHashMap<String, LinkedHashMap<String, Map<String, Object>>>();
        Map<String, LinkedHashSet<String>> objectTargets =
                new LinkedHashMap<String, LinkedHashSet<String>>();
        boolean doPairwise = perObject || showSummary || centroidMaps;

        int groupNum = 0;
        int processed = 0;
        int skipped = 0;
        int errors = 0;

        IJ.log("=== CPC Batch: " + nestedGroups.size() + " folder(s), "
                + totalGroups + " group(s), "
                + validGroups + " with 2+ images ===");

        for (Map.Entry<String, Map<String, List<File>>> folderEntry
                : nestedGroups.entrySet()) {
            String folderPath = folderEntry.getKey();
            // Last component of path for display / prefix
            String folderName;
            if (folderPath.isEmpty()) {
                folderName = "";
            } else {
                int slash = folderPath.lastIndexOf('/');
                folderName = slash >= 0
                        ? folderPath.substring(slash + 1) : folderPath;
            }

            for (Map.Entry<String, List<File>> groupEntry
                    : folderEntry.getValue().entrySet()) {
                groupNum++;
                String groupKey = groupEntry.getKey();
                List<File> labelFiles = groupEntry.getValue();
                String gName = groupDisplayName(groupKey);

                if (labelFiles.size() < 2) {
                    skipped++;
                    continue;
                }

                String displayName = (folderName.isEmpty() ? "" : folderName + "/")
                        + gName;
                IJ.log("  " + groupNum + "/" + totalGroups + ": " + displayName
                        + " (" + labelFiles.size() + " images)");
                IJ.showStatus("CPC Batch: " + displayName
                        + " (" + groupNum + "/" + totalGroups + ")");
                IJ.showProgress(groupNum - 1, totalGroups);

                try {
                    // ── Open label images ─────────────────────────
                    List<ImagePlus> images = new ArrayList<ImagePlus>();
                    boolean loadOK = true;
                    for (File f : labelFiles) {
                        ImagePlus img = IJ.openImage(f.getAbsolutePath());
                        if (img == null) {
                            IJ.log("    ERROR: Cannot open " + f.getName());
                            loadOK = false;
                            break;
                        }
                        images.add(img);
                    }
                    if (!loadOK || images.size() < 2) {
                        for (ImagePlus img : images) img.close();
                        errors++;
                        continue;
                    }

                    // ── Open raw images (intensity-weighted) ──────
                    List<ImagePlus> rawImages = null;
                    if (comWeighted && rawPattern != null) {
                        // Mirror subfolder structure
                        File effectiveRawFolder = folderPath.isEmpty()
                                ? rawRootFolder
                                : new File(rawRootFolder,
                                        folderPath.replace('/', File.separatorChar));
                        List<File> rawFiles = matchRawImages(labelFiles,
                                labelPattern, effectiveRawFolder, rawPattern,
                                varyingGroup);
                        rawImages = new ArrayList<ImagePlus>();
                        for (int i = 0; i < rawFiles.size(); i++) {
                            File rf = rawFiles.get(i);
                            if (rf != null) {
                                ImagePlus raw = IJ.openImage(
                                        rf.getAbsolutePath());
                                if (raw == null) {
                                    IJ.log("    Warning: Cannot open raw "
                                            + rf.getName());
                                }
                                rawImages.add(raw);
                            } else {
                                IJ.log("    Warning: No raw image for "
                                        + labelFiles.get(i).getName());
                                rawImages.add(null);
                            }
                        }
                    }

                    // ── Configure analysis ────────────────────────
                    CPCAnalysis analysis = rawImages != null
                            ? new CPCAnalysis(images, rawImages, bidirectional)
                            : new CPCAnalysis(images, bidirectional);
                    analysis.setDisplayResults(false);

                    // ── Run analysis ──────────────────────────────
                    if (doPairwise) {
                        analysis.run();
                        if (perObject)
                            analysis.showConsolidatedResults(extendedData);
                        if (showSummary)
                            analysis.showSummaryResults();
                    }
                    if (centroidMaps) analysis.showCentroidLabelMaps();

                    // ── Collect for batch tables ──────────────────
                    if (doPairwise) {
                        appendToLongSummary(batchSummaryLong,
                                analysis.getSummaryTable(),
                                folderName, gName);
                        collectObjectRows(pivotObjects, objectTargets,
                                analysis.getConsolidatedTable(extendedData),
                                folderName, gName, extendedData,
                                labelPattern, varyingGroup);
                    }

                    // ── Cleanup ───────────────────────────────────
                    for (ImagePlus img : images) img.close();
                    if (rawImages != null) {
                        for (ImagePlus raw : rawImages) {
                            if (raw != null) raw.close();
                        }
                    }

                    processed++;

                } catch (Exception ex) {
                    IJ.log("    ERROR: " + ex.getMessage());
                    errors++;
                }
            }
        }

        // ── Build and save to CPC/ subdirectories ────────────────
        if (!autoSave) {
            // nothing to save
        } else {
            String cpcDir = saveDir + File.separator + "CPC";
            String objectsDir = cpcDir + File.separator + "Objects";
            String folderDir = cpcDir + File.separator + "Folder";
            String multiDir = cpcDir + File.separator + "Multi";
            new File(objectsDir).mkdirs();
            new File(folderDir).mkdirs();
            new File(multiDir).mkdirs();

            // ── Objects dir: per-object tables + batch summary ────
            ResultsTable summary = null;
            if (batchSummaryLong.getCounter() > 0) {
                summary = pivotSummary(batchSummaryLong,
                        labelPattern, varyingGroup, true);
                saveBatchTable(summary, objectsDir, "CPC_Batch_Summary.csv");
            }
            for (Map.Entry<String, LinkedHashMap<String, Map<String, Object>>> se
                    : pivotObjects.entrySet()) {
                String srcCh = se.getKey();
                LinkedHashSet<String> targets = objectTargets.get(srcCh);
                if (targets == null || se.getValue().isEmpty()) continue;
                List<String> tgtList = new ArrayList<String>(targets);
                ResultsTable rt = buildPivotedObjects(
                        se.getValue(), tgtList, extendedData);
                saveBatchTable(rt, objectsDir, "CPC_Batch_Objects_" + srcCh + ".csv");
            }

            // ── Folder dir: folder summary + per-channel split ────
            if (batchSummaryLong.getCounter() > 0) {
                ResultsTable folderSummary = pivotFolderSummary(
                        batchSummaryLong, labelPattern, varyingGroup);
                saveBatchTable(folderSummary, folderDir, "CPC_Batch_Folder_Summary.csv");
                // Split folder summary by source channel (Image column)
                splitByColumn(folderSummary, "Image", folderDir,
                        "CPC_Batch_Folder_Summary_");
            }

            // ── Multi dir: multi summary + per-channel split ──────
            ResultsTable multiSummary = new ResultsTable();
            for (Map.Entry<String, LinkedHashMap<String, Map<String, Object>>> se
                    : pivotObjects.entrySet()) {
                String srcCh = se.getKey();
                LinkedHashSet<String> targets = objectTargets.get(srcCh);
                if (targets == null || se.getValue().isEmpty()) continue;
                List<String> tgtList = new ArrayList<String>(targets);
                buildMultiSummary(multiSummary, se.getValue(), tgtList, srcCh);
            }
            saveBatchTable(multiSummary, multiDir, "CPC_Batch_Multi_Summary.csv");
            // Split multi summary by source channel (Source column)
            splitByColumn(multiSummary, "Source", multiDir,
                    "CPC_Batch_Multi_Summary_");

            // ── READMEs ──────────────────────────────────────────
            writeTextFile(objectsDir, "README.txt",
                "Per-object colocalization data and pairwise summary.\n\n"
              + "CPC_Batch_Summary.csv\n"
              + "  One row per source image per group per folder.\n"
              + "  Target channels as column groups: vs Objects, Colocalized, %,\n"
              + "  Contains, %, Coloc or Contains, %.\n\n"
              + "CPC_Batch_Objects_{channel}.csv\n"
              + "  One row per object from that source channel across all folders/groups.\n"
              + "  Each target adds columns: Coloc, Partner, Contains, Count, Partners.\n"
              + "  Extended data (if enabled) adds Volume and Centroid X/Y/Z.\n");
            writeTextFile(folderDir, "README.txt",
                "Summary aggregated per folder (counts summed across groups, % recomputed).\n\n"
              + "CPC_Batch_Folder_Summary.csv\n"
              + "  One row per source channel per folder. Same target column groups as Objects.\n\n"
              + "CPC_Batch_Folder_Summary_{channel}.csv\n"
              + "  Same data filtered to a single source channel.\n");
            writeTextFile(multiDir, "README.txt",
                "Multi-target colocalization patterns — which combinations of targets\n"
              + "each source object colocalizes with, aggregated per folder.\n\n"
              + "CPC_Batch_Multi_Summary.csv\n"
              + "  One row per unique pattern per source channel per folder.\n"
              + "  Pattern: target combination (e.g. \"DAPI + GFAP\", \"None\").\n"
              + "  Coloc Count/% : centroid falls inside that target combination.\n"
              + "  Contains Count/% : contains centroids of that target combination.\n"
              + "  Either Count/% : colocalization or containment.\n\n"
              + "CPC_Batch_Multi_Summary_{channel}.csv\n"
              + "  Same data filtered to a single source channel.\n");
        }

        IJ.showProgress(1.0);
        IJ.log("=== CPC Batch Complete: " + processed + " processed, "
                + skipped + " skipped, " + errors + " error(s) ===");
        IJ.showStatus("CPC Batch: done (" + processed + " groups).");
    }

    /**
     * Split a ResultsTable into per-value CSVs based on a string column.
     * Saves each as {prefix}{value}.csv in the given directory.
     */
    private static void splitByColumn(ResultsTable rt, String column,
                                       String dir, String prefix) {
        if (rt == null || rt.getCounter() == 0) return;
        // Group row indices by column value
        Map<String, List<Integer>> groups = new LinkedHashMap<String, List<Integer>>();
        for (int r = 0; r < rt.getCounter(); r++) {
            String val = rt.getStringValue(column, r);
            List<Integer> rows = groups.get(val);
            if (rows == null) { rows = new ArrayList<Integer>(); groups.put(val, rows); }
            rows.add(r);
        }
        // Get all column headings
        String headingStr = rt.getColumnHeadings();
        String[] headings = headingStr.split("\t");

        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            ResultsTable split = new ResultsTable();
            for (int srcRow : entry.getValue()) {
                int dstRow = split.getCounter();
                split.incrementCounter();
                for (String h : headings) {
                    if (h.isEmpty()) continue;
                    // Try as number first; if NaN, use string
                    double num = rt.getValue(h, srcRow);
                    if (!Double.isNaN(num)) {
                        split.addValue(h, num);
                    } else {
                        split.setValue(h, dstRow, rt.getStringValue(h, srcRow));
                    }
                }
            }
            saveBatchTable(split, dir, prefix + entry.getKey() + ".csv");
        }
    }

    private static void writeTextFile(String dir, String name, String text) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                    dir + File.separator + name);
            fw.write(text);
            fw.close();
        } catch (Exception ignored) { }
    }

    private static void saveBatchTable(ResultsTable rt, String dir, String name) {
        if (rt == null || rt.getCounter() == 0) return;
        try {
            rt.save(dir + File.separator + name);
            IJ.log("  Saved " + name + " (" + rt.getCounter() + " rows)");
        } catch (Exception e) {
            IJ.log("  Failed to save " + name + ": " + e.getMessage());
        }
    }

    // ── Long-format summary collection ────────────────────────────

    private static void appendToLongSummary(ResultsTable agg,
                                             ResultsTable group,
                                             String folderName,
                                             String groupName) {
        for (int row = 0; row < group.getCounter(); row++) {
            int r = agg.getCounter();
            agg.incrementCounter();
            agg.setValue("Folder", r, folderName);
            agg.setValue("Group", r, groupName);
            agg.setValue("Image", r, group.getStringValue("Image", row));
            agg.setValue("vs", r, group.getStringValue("vs", row));
            agg.addValue("Objects", group.getValue("Objects", row));
            agg.addValue("vs Objects", group.getValue("vs Objects", row));
            agg.addValue("Colocalized", group.getValue("Colocalized", row));
            agg.addValue("% Colocalized", group.getValue("% Colocalized", row));
            agg.addValue("Contains", group.getValue("Contains", row));
            agg.addValue("% Contains", group.getValue("% Contains", row));
            agg.addValue("Coloc or Contains", group.getValue("Coloc or Contains", row));
            agg.addValue("% Coloc or Contains", group.getValue("% Coloc or Contains", row));
        }
    }

    // ── Object pivot collection ───────────────────────────────────

    private static void collectObjectRows(
            Map<String, LinkedHashMap<String, Map<String, Object>>> pivotObjects,
            Map<String, LinkedHashSet<String>> objectTargets,
            ResultsTable group, String folderName, String groupName,
            boolean extendedData, Pattern labelPattern, int varyingGroup) {
        for (int row = 0; row < group.getCounter(); row++) {
            String image = group.getStringValue("Image", row);
            String vs = group.getStringValue("vs", row);
            String srcCh = extractChannel(image, labelPattern, varyingGroup);
            String tgtCh = extractChannel(vs, labelPattern, varyingGroup);

            // Track target ordering per source
            LinkedHashSet<String> tgts = objectTargets.get(srcCh);
            if (tgts == null) { tgts = new LinkedHashSet<String>(); objectTargets.put(srcCh, tgts); }
            tgts.add(tgtCh);

            // Get/create source channel map
            LinkedHashMap<String, Map<String, Object>> objs = pivotObjects.get(srcCh);
            if (objs == null) { objs = new LinkedHashMap<String, Map<String, Object>>(); pivotObjects.put(srcCh, objs); }

            // Object key
            int label = (int) group.getValue("Label", row);
            String key = folderName + "|" + groupName + "|" + image + "|" + label;
            Map<String, Object> cols = objs.get(key);
            if (cols == null) {
                cols = new LinkedHashMap<String, Object>();
                objs.put(key, cols);
                cols.put("Folder", folderName);
                cols.put("Group", groupName);
                cols.put("Image", image);
                cols.put("Label", (double) label);
                if (extendedData) {
                    cols.put("Volume", group.getValue("Volume (voxels)", row));
                    cols.put("cx", group.getValue("Centroid X (px)", row));
                    cols.put("cy", group.getValue("Centroid Y (px)", row));
                    cols.put("cz", group.getValue("Centroid Z (slice)", row));
                }
            }

            cols.put(tgtCh + "|coloc", group.getValue("Colocalized", row));
            cols.put(tgtCh + "|partner", group.getValue("Coloc Partner Label", row));
            cols.put(tgtCh + "|contains", group.getValue("Contains", row));
            cols.put(tgtCh + "|count", group.getValue("Contains Count", row));
            cols.put(tgtCh + "|partners", group.getStringValue("Contains Partner Labels", row));
        }
    }

    // ── Build pivoted objects ResultsTable ─────────────────────────

    private static ResultsTable buildPivotedObjects(
            LinkedHashMap<String, Map<String, Object>> objects,
            List<String> targets, boolean extendedData) {
        ResultsTable rt = new ResultsTable();
        for (Map<String, Object> cols : objects.values()) {
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Folder", row, (String) cols.get("Folder"));
            rt.setValue("Group", row, (String) cols.get("Group"));
            rt.setValue("Image", row, (String) cols.get("Image"));
            rt.addValue("Label", (Double) cols.get("Label"));
            if (extendedData) {
                rt.addValue("Volume (voxels)", (Double) cols.get("Volume"));
                rt.addValue("Centroid X (px)", (Double) cols.get("cx"));
                rt.addValue("Centroid Y (px)", (Double) cols.get("cy"));
                rt.addValue("Centroid Z (slice)", (Double) cols.get("cz"));
            }
            for (String tgt : targets) {
                Double v;
                v = (Double) cols.get(tgt + "|coloc");
                rt.addValue(tgt + " Coloc", v != null ? v : 0);
                v = (Double) cols.get(tgt + "|partner");
                rt.addValue(tgt + " Partner", v != null ? v : 0);
                v = (Double) cols.get(tgt + "|contains");
                rt.addValue(tgt + " Contains", v != null ? v : 0);
                v = (Double) cols.get(tgt + "|count");
                rt.addValue(tgt + " Count", v != null ? v : 0);
                String s = (String) cols.get(tgt + "|partners");
                rt.setValue(tgt + " Partners", row, s != null ? s : "");
            }
        }
        return rt;
    }

    // ── Pivot summary: wide format ────────────────────────────────

    /**
     * Pivot long-format summary into wide: one row per (folder, group, source),
     * target channels as column groups.
     * @param includeGroup true for full summary, false for folder-level
     */
    private static ResultsTable pivotSummary(ResultsTable longFmt,
                                              Pattern labelPattern,
                                              int varyingGroup,
                                              boolean includeGroup) {
        LinkedHashSet<String> allTargets = new LinkedHashSet<String>();
        LinkedHashMap<String, Map<String, Object>> rows =
                new LinkedHashMap<String, Map<String, Object>>();

        for (int r = 0; r < longFmt.getCounter(); r++) {
            String folder = longFmt.getStringValue("Folder", r);
            String group = longFmt.getStringValue("Group", r);
            String image = longFmt.getStringValue("Image", r);
            String vs = longFmt.getStringValue("vs", r);
            String srcCh = extractChannel(image, labelPattern, varyingGroup);
            String tgtCh = extractChannel(vs, labelPattern, varyingGroup);
            allTargets.add(tgtCh);

            String key = includeGroup
                    ? folder + "|" + group + "|" + image
                    : folder + "|" + image;
            Map<String, Object> cols = rows.get(key);
            if (cols == null) {
                cols = new LinkedHashMap<String, Object>();
                rows.put(key, cols);
                cols.put("Folder", folder);
                if (includeGroup) cols.put("Group", group);
                cols.put("Image", image);
                cols.put("Objects", longFmt.getValue("Objects", r));
            }
            cols.put(tgtCh + "|vsObj", longFmt.getValue("vs Objects", r));
            cols.put(tgtCh + "|coloc", longFmt.getValue("Colocalized", r));
            cols.put(tgtCh + "|pctColoc", longFmt.getValue("% Colocalized", r));
            cols.put(tgtCh + "|contains", longFmt.getValue("Contains", r));
            cols.put(tgtCh + "|pctContains", longFmt.getValue("% Contains", r));
            cols.put(tgtCh + "|either", longFmt.getValue("Coloc or Contains", r));
            cols.put(tgtCh + "|pctEither", longFmt.getValue("% Coloc or Contains", r));
        }

        List<String> targets = new ArrayList<String>(allTargets);
        ResultsTable rt = new ResultsTable();
        for (Map<String, Object> cols : rows.values()) {
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Folder", row, (String) cols.get("Folder"));
            if (includeGroup)
                rt.setValue("Group", row, (String) cols.get("Group"));
            rt.setValue("Image", row, (String) cols.get("Image"));
            rt.addValue("Objects", (Double) cols.get("Objects"));
            for (String tgt : targets) {
                addPivotValue(rt, cols, tgt + " vs Objects", tgt + "|vsObj");
                addPivotValue(rt, cols, tgt + " Colocalized", tgt + "|coloc");
                addPivotValue(rt, cols, tgt + " % Colocalized", tgt + "|pctColoc");
                addPivotValue(rt, cols, tgt + " Contains", tgt + "|contains");
                addPivotValue(rt, cols, tgt + " % Contains", tgt + "|pctContains");
                addPivotValue(rt, cols, tgt + " Coloc or Contains", tgt + "|either");
                addPivotValue(rt, cols, tgt + " % Coloc or Contains", tgt + "|pctEither");
            }
        }
        return rt;
    }

    private static void addPivotValue(ResultsTable rt, Map<String, Object> cols,
                                       String colName, String mapKey) {
        Double v = (Double) cols.get(mapKey);
        rt.addValue(colName, v != null ? v : 0);
    }

    // ── Pivot folder summary: aggregate per folder ────────────────

    static ResultsTable pivotFolderSummary(ResultsTable longFmt,
                                                    Pattern labelPattern,
                                                    int varyingGroup) {
        LinkedHashSet<String> allTargets = new LinkedHashSet<String>();
        // folder|srcCh → accumulated values
        LinkedHashMap<String, double[]> accum =
                new LinkedHashMap<String, double[]>();
        // Track target indices: tgtCh → position in value array
        // Per key: [objects, t0_vsObj, t0_coloc, t0_contains, t0_either, t1_vsObj, ...]
        // Build target list first
        List<String> targetList = new ArrayList<String>();
        Map<String, Integer> targetIdx = new LinkedHashMap<String, Integer>();

        // First pass: discover targets
        for (int r = 0; r < longFmt.getCounter(); r++) {
            String tgtCh = extractChannel(longFmt.getStringValue("vs", r),
                    labelPattern, varyingGroup);
            if (!targetIdx.containsKey(tgtCh)) {
                targetIdx.put(tgtCh, targetList.size());
                targetList.add(tgtCh);
            }
        }

        int stride = 4; // vsObj, coloc, contains, either per target
        int arrLen = 1 + targetList.size() * stride; // objects + per-target

        // Second pass: accumulate
        LinkedHashMap<String, String[]> keyParts =
                new LinkedHashMap<String, String[]>();
        // Track which source images have been counted for Objects
        LinkedHashSet<String> countedImages = new LinkedHashSet<String>();
        for (int r = 0; r < longFmt.getCounter(); r++) {
            String folder = longFmt.getStringValue("Folder", r);
            String image = longFmt.getStringValue("Image", r);
            String srcCh = extractChannel(image, labelPattern, varyingGroup);
            String tgtCh = extractChannel(longFmt.getStringValue("vs", r),
                    labelPattern, varyingGroup);
            String key = folder + "|" + srcCh;

            double[] v = accum.get(key);
            if (v == null) {
                v = new double[arrLen];
                accum.put(key, v);
                keyParts.put(key, new String[]{folder, srcCh});
            }
            // Only count Objects once per unique source image
            String imgKey = folder + "|" + image;
            if (countedImages.add(imgKey)) {
                v[0] += longFmt.getValue("Objects", r);
            }
            int ti = targetIdx.get(tgtCh);
            int base = 1 + ti * stride;
            v[base] += longFmt.getValue("vs Objects", r);
            v[base + 1] += longFmt.getValue("Colocalized", r);
            v[base + 2] += longFmt.getValue("Contains", r);
            v[base + 3] += longFmt.getValue("Coloc or Contains", r);
        }

        ResultsTable rt = new ResultsTable();
        for (Map.Entry<String, double[]> entry : accum.entrySet()) {
            String[] parts = keyParts.get(entry.getKey());
            double[] v = entry.getValue();
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Folder", row, parts[0]);
            rt.setValue("Image", row, parts[1]);
            double objects = v[0];
            rt.addValue("Objects", objects);
            for (int ti = 0; ti < targetList.size(); ti++) {
                String tgt = targetList.get(ti);
                int base = 1 + ti * stride;
                rt.addValue(tgt + " vs Objects", v[base]);
                double c = v[base + 1];
                rt.addValue(tgt + " Colocalized", c);
                rt.addValue(tgt + " % Colocalized",
                        objects > 0 ? Math.round(c * 10000.0 / objects) / 100.0 : 0);
                double ct = v[base + 2];
                rt.addValue(tgt + " Contains", ct);
                rt.addValue(tgt + " % Contains",
                        objects > 0 ? Math.round(ct * 10000.0 / objects) / 100.0 : 0);
                double e = v[base + 3];
                rt.addValue(tgt + " Coloc or Contains", e);
                rt.addValue(tgt + " % Coloc or Contains",
                        objects > 0 ? Math.round(e * 10000.0 / objects) / 100.0 : 0);
            }
        }
        return rt;
    }

    // ── Multi-colocalization summary ─────────────────────────────

    /**
     * Build multi-colocalization summary: for each (folder, source, pattern),
     * count how many objects have that pattern via coloc, contains, or either.
     */
    private static void buildMultiSummary(
            ResultsTable rt,
            LinkedHashMap<String, Map<String, Object>> objects,
            List<String> targets, String srcCh) {
        // Discover all patterns and count per folder
        // Key: folder|pattern → [colocCount, containsCount, eitherCount, total]
        Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
        Map<String, int[]> totals = new LinkedHashMap<String, int[]>();

        for (Map<String, Object> cols : objects.values()) {
            String folder = (String) cols.get("Folder");

            int[] tot = totals.get(folder);
            if (tot == null) { tot = new int[1]; totals.put(folder, tot); }
            tot[0]++;

            // Build pattern for each mode
            String colocPat = buildPattern(cols, targets, "coloc", null);
            String containsPat = buildPattern(cols, targets, "contains", null);
            String eitherPat = buildPattern(cols, targets, "coloc", "contains");

            // Collect all unique patterns from this object
            LinkedHashSet<String> seen = new LinkedHashSet<String>();
            seen.add(colocPat);
            seen.add(containsPat);
            seen.add(eitherPat);

            for (String pat : seen) {
                String key = folder + "|" + pat;
                int[] c = counts.get(key);
                if (c == null) { c = new int[3]; counts.put(key, c); }
            }

            // Increment the appropriate counter for each pattern
            incrementPattern(counts, folder, colocPat, 0);
            incrementPattern(counts, folder, containsPat, 1);
            incrementPattern(counts, folder, eitherPat, 2);
        }

        // Build table rows
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int sep = entry.getKey().indexOf('|');
            String folder = entry.getKey().substring(0, sep);
            String pattern = entry.getKey().substring(sep + 1);
            int[] c = entry.getValue();
            int total = totals.get(folder)[0];
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Folder", row, folder);
            rt.setValue("Source", row, srcCh);
            rt.setValue("Pattern", row, pattern);
            rt.addValue("Coloc Count", c[0]);
            rt.addValue("Coloc %",
                    total > 0 ? Math.round(c[0] * 10000.0 / total) / 100.0 : 0);
            rt.addValue("Contains Count", c[1]);
            rt.addValue("Contains %",
                    total > 0 ? Math.round(c[1] * 10000.0 / total) / 100.0 : 0);
            rt.addValue("Either Count", c[2]);
            rt.addValue("Either %",
                    total > 0 ? Math.round(c[2] * 10000.0 / total) / 100.0 : 0);
        }
    }

    private static String buildPattern(Map<String, Object> cols,
                                        List<String> targets,
                                        String mode1, String mode2) {
        StringBuilder sb = new StringBuilder();
        for (String tgt : targets) {
            Double v1 = (Double) cols.get(tgt + "|" + mode1);
            boolean hit = v1 != null && v1 > 0;
            if (!hit && mode2 != null) {
                Double v2 = (Double) cols.get(tgt + "|" + mode2);
                hit = v2 != null && v2 > 0;
            }
            if (hit) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(tgt);
            }
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }

    private static void incrementPattern(Map<String, int[]> counts,
                                          String folder, String pattern,
                                          int index) {
        int[] c = counts.get(folder + "|" + pattern);
        if (c != null) c[index]++;
    }

    // ── Utilities ─────────────────────────────────────────────────

    private static String extractChannel(String filename, Pattern pattern,
                                          int varyingGroup) {
        Matcher m = pattern.matcher(filename);
        if (m.matches() && varyingGroup >= 1 && varyingGroup <= m.groupCount()) {
            return m.group(varyingGroup);
        }
        String s = filename.replaceAll("\\.[^.]+$", "");
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
