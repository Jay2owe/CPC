/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package cpc;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sc.fiji.cpc.core.CentroidCoincidence;
import sc.fiji.cpc.core.CentroidMapBuilder;
import sc.fiji.cpc.core.Channel;
import sc.fiji.cpc.core.CoincidenceObject;
import sc.fiji.cpc.core.CoincidenceResult;
import sc.fiji.cpc.core.MultiTargetSummary;
import sc.fiji.cpc.core.PairwiseCoincidenceRunner;
import sc.fiji.oc3d.core.measure.CentroidScan;

/**
 * Centre-Particle Coincidence analysis.
 * <p>
 * For each object in image A, checks whether its centroid falls inside
 * an object in image B (and vice versa if bidirectional).
 * Supports 2–5 images with all pairwise comparisons.
 * <p>
 * The measurement and the test now live in {@code cpc-core}, so that other
 * plugins can offer centroid coincidence without CPC being installed. What
 * stays here is everything ImageJ-facing: building {@link ResultsTable}s,
 * showing windows, writing the auto-save tree, and the legacy shapes the
 * documented Java API exposes. The core returns models; this class turns them
 * into tables, which is exactly the boundary that lets a plugin with a
 * different table layout embed the same engine.
 */
public class CPCAnalysis {

    /** A single 3D object extracted from a label image. */
    public static class ObjectInfo {
        public final int label;
        public double cx, cy, cz;
        public int voxelCount;
        public int partnerLabel;

        public ObjectInfo(int label) {
            this.label = label;
        }

        public boolean isColocalized() {
            return partnerLabel > 0;
        }
    }

    /** Results for one direction of analysis (e.g. A centroids in B). */
    public static class DirectionResult {
        public String sourceName;
        public String targetName;
        public List<ObjectInfo> objects;
        public int totalObjects;
        public int targetTotalObjects;
        public int colocalizedCount;

        public double getPercentColocalized() {
            return totalObjects > 0 ? (colocalizedCount * 100.0 / totalObjects) : 0;
        }

        public double getPercentOfTarget() {
            return targetTotalObjects > 0 ? (colocalizedCount * 100.0 / targetTotalObjects) : 0;
        }
    }

    /** Multi-target results: one entry per source image, each tested against all other images. */
    public static class MultiTargetResult {
        public String sourceName;
        public List<String> targetNames;
        public List<ObjectInfo> objects;
        /** For each object (parallel to objects list): target name → partner label (0 = no coloc). */
        public List<Map<String, Integer>> objectPartners;
        public int sourceTotal;
    }

    private final List<ImagePlus> images;
    private final List<ImagePlus> rawImages; // parallel list, null entries = geometric centroid
    private final boolean bidirectional;

    private final List<DirectionResult> results = new ArrayList<DirectionResult>();
    private final List<MultiTargetResult> multiTargetResults = new ArrayList<MultiTargetResult>();
    private List<List<ObjectInfo>> cachedObjects;
    private List<Channel> channels;
    private String saveDir;
    private String objectsSaveDir;
    private String multiSaveDir;
    private String mapsSaveDir;

    /** Set a directory to auto-save all results to. Creates CPC/ subdirectory structure. Null = don't save. */
    public void setSaveDir(String dir) {
        this.saveDir = dir;
        if (dir != null) {
            String cpcDir = dir + "/CPC";
            objectsSaveDir = cpcDir + "/Objects";
            multiSaveDir = cpcDir + "/Multi";
            mapsSaveDir = cpcDir + "/Maps";
        }
    }

    private boolean displayResults = true;

    /** Set whether to display results in the UI. When false, only auto-save. */
    public void setDisplayResults(boolean display) { this.displayResults = display; }

    private String savePrefix = "";

    /** Set a prefix for auto-saved filenames (used by batch flat-save mode). */
    public void setSavePrefix(String prefix) { this.savePrefix = prefix != null ? prefix : ""; }

    /** Sanitize an image title for use as a filename component. */
    private static String sanitize(String title) {
        // Strip common extensions, then replace non-filename chars
        String s = title.replaceAll("\\.(tif|tiff|png|jpg|jpeg|zip)$", "");
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Save a ResultsTable as CSV to a specific subdirectory, creating it on first use. */
    private void autoSave(ResultsTable rt, String dir, String filename) {
        if (dir == null) return;
        ensureSaveDir(dir);
        try {
            rt.save(dir + "/" + savePrefix + filename);
            IJ.log("CPC: Saved " + savePrefix + filename);
        } catch (Exception e) {
            IJ.log("CPC: Failed to save " + savePrefix + filename + ": " + e.getMessage());
        }
    }

    /** Create a save subdirectory and write its README on first use. */
    private void ensureSaveDir(String dir) {
        File d = new File(dir);
        if (d.exists()) return;
        d.mkdirs();
        if (dir.equals(objectsSaveDir)) {
            writeTextFile(dir, "README.txt",
                "Per-object colocalization data and pairwise summary.\n\n"
              + "CPC_{ImageA}_vs_{ImageB}.csv\n"
              + "  Per-object table for each pairwise comparison.\n"
              + "  Columns: Label, Colocalized, Coloc Partner Label,\n"
              + "  Contains, Contains Count, Contains Partner Labels.\n"
              + "  Extended data (if enabled): Volume, Centroid X/Y/Z.\n\n"
              + "CPC_Summary.csv\n"
              + "  One row per pairwise comparison.\n"
              + "  Columns: Image, vs, Objects, vs Objects, Colocalized, %,\n"
              + "  Contains, %, Coloc or Contains, %.\n");
        } else if (dir.equals(multiSaveDir)) {
            writeTextFile(dir, "README.txt",
                "Multi-target colocalization analysis.\n\n"
              + "CPC_Multi_{ImageName}.csv\n"
              + "  Per-object table showing which targets each object\n"
              + "  colocalizes with. Columns per target: Coloc, Partner.\n"
              + "  Final column: Targets Hit.\n"
              + "  Extended data (if enabled): Volume, Centroid X/Y/Z.\n\n"
              + "CPC_Multi-Target_Summary.csv\n"
              + "  Combination pattern counts and percentages.\n"
              + "  Each row shows how many objects match a specific\n"
              + "  combination pattern (e.g. \"ImageB + ImageC\", \"None\").\n");
        } else if (dir.equals(mapsSaveDir)) {
            writeTextFile(dir, "README.txt",
                "Centroid label maps.\n\n"
              + "CPC_Centroid_Map_{ImageName}.tif\n"
              + "  Label image with centroids from all other channels\n"
              + "  overlaid as cross markers.\n");
        }
    }

    private static void writeTextFile(String dir, String name, String text) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(dir + "/" + name);
            fw.write(text);
            fw.close();
        } catch (Exception ignored) { }
    }

    /** Legacy two-image constructor. */
    public CPCAnalysis(ImagePlus imageA, ImagePlus imageB, boolean bidirectional) {
        this.images = new ArrayList<ImagePlus>();
        this.images.add(imageA);
        this.images.add(imageB);
        this.rawImages = null;
        this.bidirectional = bidirectional;
    }

    /** Multi-image constructor (2–5 images). */
    public CPCAnalysis(List<ImagePlus> images, boolean bidirectional) {
        this.images = images;
        this.rawImages = null;
        this.bidirectional = bidirectional;
    }

    /**
     * Multi-image constructor with optional raw images for intensity-weighted centroids.
     * @param rawImages parallel list (same size as images), null entries use geometric centroid
     */
    public CPCAnalysis(List<ImagePlus> images, List<ImagePlus> rawImages, boolean bidirectional) {
        this.images = images;
        this.rawImages = rawImages;
        this.bidirectional = bidirectional;
    }

    /**
     * Scans each image once, and keeps both views of the result: the core's
     * channels, which every later call works from, and the legacy
     * {@link ObjectInfo} lists the documented Java API hands out.
     */
    private List<Channel> getOrBuildChannels() {
        if (channels != null) return channels;
        int n = images.size();
        channels = new ArrayList<Channel>(n);
        cachedObjects = new ArrayList<List<ObjectInfo>>(n);
        for (int i = 0; i < n; i++) {
            IJ.showStatus("CPC: Extracting objects from image " + (i + 1) + "/" + n + "...");
            IJ.showProgress(i, n);
            ImagePlus raw = (rawImages != null && i < rawImages.size()) ? rawImages.get(i) : null;
            Channel channel = Channel.of(images.get(i).getTitle(), images.get(i), raw);
            channels.add(channel);
            cachedObjects.add(toObjectInfo(channel.centroids()));
        }
        IJ.showProgress(1.0);
        return channels;
    }

    private List<List<ObjectInfo>> getOrExtractObjects() {
        getOrBuildChannels();
        return cachedObjects;
    }

    public void run() {
        List<Channel> chans = getOrBuildChannels();
        int n = chans.size();

        CoincidenceResult coincidence = PairwiseCoincidenceRunner.run(chans, bidirectional);
        for (sc.fiji.cpc.core.DirectionResult direction : coincidence.directions()) {
            IJ.showStatus("CPC: Testing " + direction.sourceName()
                    + " in " + direction.targetName() + "...");
            results.add(toDirectionResult(direction));
        }

        int totalObjs = 0;
        for (List<ObjectInfo> objs : cachedObjects) totalObjs += objs.size();
        IJ.showStatus("CPC: Done (" + totalObjs + " objects across " + n + " images, " + results.size() + " comparisons).");
    }

    // ── Adapters between the core's models and CPC's legacy shapes ────

    private static List<ObjectInfo> toObjectInfo(CentroidScan.Result centroids) {
        List<ObjectInfo> objects = new ArrayList<ObjectInfo>(centroids.objectCount());
        for (CentroidScan.Centroid centroid : centroids.centroids()) {
            objects.add(toObjectInfo(centroid, 0));
        }
        return objects;
    }

    private static ObjectInfo toObjectInfo(CentroidScan.Centroid centroid, int partnerLabel) {
        ObjectInfo object = new ObjectInfo(centroid.label());
        object.cx = centroid.x();
        object.cy = centroid.y();
        object.cz = centroid.z();
        object.voxelCount = (int) centroid.voxelCount();
        object.partnerLabel = partnerLabel;
        return object;
    }

    private static ObjectInfo toObjectInfo(CoincidenceObject object) {
        ObjectInfo info = new ObjectInfo(object.label());
        info.cx = object.x();
        info.cy = object.y();
        info.cz = object.z();
        info.voxelCount = (int) object.voxelCount();
        info.partnerLabel = object.partnerLabel();
        return info;
    }

    private static DirectionResult toDirectionResult(sc.fiji.cpc.core.DirectionResult direction) {
        DirectionResult result = new DirectionResult();
        result.sourceName = direction.sourceName();
        result.targetName = direction.targetName();
        result.objects = new ArrayList<ObjectInfo>(direction.objects().size());
        for (CoincidenceObject object : direction.objects()) {
            result.objects.add(toObjectInfo(object));
        }
        result.totalObjects = direction.totalObjects();
        result.targetTotalObjects = direction.targetObjectCount();
        result.colocalizedCount = direction.coincidentCount();
        return result;
    }

    /** Deep copy object list so each pairwise test gets its own partnerLabel state. */
    public static List<ObjectInfo> copyObjects(List<ObjectInfo> originals) {
        List<ObjectInfo> copy = new ArrayList<ObjectInfo>(originals.size());
        for (ObjectInfo o : originals) {
            ObjectInfo c = new ObjectInfo(o.label);
            c.cx = o.cx;
            c.cy = o.cy;
            c.cz = o.cz;
            c.voxelCount = o.voxelCount;
            copy.add(c);
        }
        return copy;
    }

    /**
     * Extract all objects and compute their centroids from a label image.
     * Each unique non-zero pixel value is treated as a separate object.
     * Uses geometric centroid (unweighted).
     */
    public static List<ObjectInfo> extractObjects(ImagePlus img) {
        return extractObjects(img, null);
    }

    /**
     * Extract all objects and compute their centroids from a label image.
     * If rawImg is provided, computes intensity-weighted centroids (center of mass)
     * using pixel intensities from rawImg as weights. Otherwise geometric centroid.
     * <p>
     * Objects come back ascending by label, which is the order every table in
     * the plugin family uses. The measurement itself is
     * {@code oc3d-core}'s, so a plugin that measures with one and tests
     * coincidence with the other cannot end up with two different ideas of
     * which voxels belong to which object.
     */
    public static List<ObjectInfo> extractObjects(ImagePlus img, ImagePlus rawImg) {
        return toObjectInfo(CentroidScan.scan(img, rawImg));
    }

    /**
     * For each object, look up the voxel value in the target label image
     * at the object's centroid position.
     */
    public static void testCoincidence(List<ObjectInfo> objects, ImagePlus targetImage) {
        for (ObjectInfo obj : objects) {
            obj.partnerLabel = CentroidCoincidence.labelAt(
                    targetImage, obj.cx, obj.cy, obj.cz);
        }
    }

    // ── Results display ────────────────────────────────────────────

    /** @deprecated Legacy "in" tables — use {@link #showConsolidatedResults(boolean)} instead. */
    public void showPerObjectResults() {
        for (DirectionResult r : results) {
            showPerObjectTable(r);
        }
    }

    private void showPerObjectTable(DirectionResult result) {
        ResultsTable rt = new ResultsTable();
        for (ObjectInfo obj : result.objects) {
            rt.incrementCounter();
            rt.addValue("Label", obj.label);
            rt.addValue("Centroid X (px)", obj.cx);
            rt.addValue("Centroid Y (px)", obj.cy);
            rt.addValue("Centroid Z (slice)", obj.cz);
            rt.addValue("Volume (voxels)", obj.voxelCount);
            rt.addValue("Colocalized", obj.isColocalized() ? 1 : 0);
            rt.addValue("Partner Label", obj.partnerLabel);
        }
        String title = "CPC: " + result.sourceName + " in " + result.targetName;
        if (displayResults) rt.show(title);
    }

    /** Build the summary ResultsTable without displaying or saving. */
    public ResultsTable getSummaryTable() {
        Map<String, DirectionResult> lookup = new LinkedHashMap<String, DirectionResult>();
        for (DirectionResult r : results) {
            lookup.put(r.sourceName + "\u2192" + r.targetName, r);
        }

        ResultsTable rt = new ResultsTable();
        int n = images.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String nameI = images.get(i).getTitle();
                String nameJ = images.get(j).getTitle();

                DirectionResult forward = lookup.get(nameI + "\u2192" + nameJ);
                DirectionResult reverse = lookup.get(nameJ + "\u2192" + nameI);

                if (forward != null) {
                    addConsolidatedSummaryRow(rt, nameI, nameJ, forward, reverse);
                }
                if (reverse != null) {
                    addConsolidatedSummaryRow(rt, nameJ, nameI, reverse, forward);
                }
            }
        }
        return rt;
    }

    public void showSummaryResults() {
        ResultsTable rt = getSummaryTable();
        if (displayResults) rt.show("CPC Summary");
        autoSave(rt, objectsSaveDir, "CPC_Summary.csv");
    }

    private void addConsolidatedSummaryRow(ResultsTable rt, String sourceName, String targetName,
                                            DirectionResult colocResult, DirectionResult containResult) {
        int row = rt.getCounter();
        rt.incrementCounter();
        rt.setValue("Image", row, sourceName);
        rt.setValue("vs", row, targetName);
        int total = colocResult.totalObjects;
        rt.addValue("Objects", total);
        rt.addValue("vs Objects", colocResult.targetTotalObjects);

        // Colocalized: source centroid in target — collect labels
        java.util.Set<Integer> colocLabels = new java.util.HashSet<Integer>();
        for (ObjectInfo obj : colocResult.objects) {
            if (obj.isColocalized()) colocLabels.add(obj.label);
        }
        rt.addValue("Colocalized", colocLabels.size());
        rt.addValue("% Colocalized",
                Math.round(colocResult.getPercentColocalized() * 100.0) / 100.0);

        // Contains: source objects that contain at least one target centroid — collect labels
        java.util.Set<Integer> containsLabels = new java.util.HashSet<Integer>();
        int totalContained = 0;
        if (containResult != null) {
            Map<Integer, Integer> containsHits = new LinkedHashMap<Integer, Integer>();
            for (ObjectInfo obj : containResult.objects) {
                if (obj.partnerLabel > 0) {
                    Integer c = containsHits.get(obj.partnerLabel);
                    containsHits.put(obj.partnerLabel, c == null ? 1 : c + 1);
                }
            }
            containsLabels.addAll(containsHits.keySet());
            for (int c : containsHits.values()) totalContained += c;
        }
        rt.addValue("Contains", containsLabels.size());
        rt.addValue("% Contains",
                total > 0 ? Math.round(containsLabels.size() * 10000.0 / total) / 100.0 : 0);

        // Either: colocalized OR contains (union)
        java.util.Set<Integer> eitherLabels = new java.util.HashSet<Integer>(colocLabels);
        eitherLabels.addAll(containsLabels);
        int eitherCount = eitherLabels.size();
        rt.addValue("Coloc or Contains", eitherCount);
        rt.addValue("% Coloc or Contains",
                total > 0 ? Math.round(eitherCount * 10000.0 / total) / 100.0 : 0);
    }

    // ── Consolidated (vs) results ───────────────────────────────────

    /**
     * Shows consolidated "vs" tables. For each pair, one table per image
     * showing both colocalization (my centroid in theirs) and containment
     * (their centroids in me). Requires bidirectional results.
     *
     * @param extendedData if true, append centroid X/Y/Z as last columns
     */
    public void showConsolidatedResults(boolean extendedData) {
        // Build lookup: "sourceName→targetName" → DirectionResult
        Map<String, DirectionResult> lookup = new LinkedHashMap<String, DirectionResult>();
        for (DirectionResult r : results) {
            lookup.put(r.sourceName + "\u2192" + r.targetName, r);
        }

        // For each pair, produce a table for each direction
        int n = images.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String nameI = images.get(i).getTitle();
                String nameJ = images.get(j).getTitle();

                // Table for image I: colocalized from I→J, contains from J→I inverted
                DirectionResult forward = lookup.get(nameI + "\u2192" + nameJ);
                DirectionResult reverse = lookup.get(nameJ + "\u2192" + nameI);
                if (forward != null) {
                    showConsolidatedTable(nameI, nameJ, i, forward, reverse, extendedData);
                }

                // Table for image J: colocalized from J→I, contains from I→J inverted
                if (reverse != null) {
                    showConsolidatedTable(nameJ, nameI, j, reverse, forward, extendedData);
                }
            }
        }
    }

    /**
     * Returns a single ResultsTable with per-object data from all pairs,
     * using normalised column names and Image/vs columns to identify the pair.
     */
    public ResultsTable getConsolidatedTable(boolean extendedData) {
        Map<String, DirectionResult> lookup = new LinkedHashMap<String, DirectionResult>();
        for (DirectionResult r : results) {
            lookup.put(r.sourceName + "\u2192" + r.targetName, r);
        }

        ResultsTable rt = new ResultsTable();
        int n = images.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String nameI = images.get(i).getTitle();
                String nameJ = images.get(j).getTitle();
                DirectionResult forward = lookup.get(nameI + "\u2192" + nameJ);
                DirectionResult reverse = lookup.get(nameJ + "\u2192" + nameI);
                if (forward != null)
                    appendConsolidatedRows(rt, nameI, nameJ, i, forward, reverse, extendedData);
                if (reverse != null)
                    appendConsolidatedRows(rt, nameJ, nameI, j, reverse, forward, extendedData);
            }
        }
        return rt;
    }

    private void appendConsolidatedRows(ResultsTable rt, String sourceName, String targetName,
                                         int sourceIdx, DirectionResult colocResult,
                                         DirectionResult containResult, boolean extendedData) {
        Map<Integer, Integer> colocMap = new LinkedHashMap<Integer, Integer>();
        for (ObjectInfo obj : colocResult.objects) {
            colocMap.put(obj.label, obj.partnerLabel);
        }
        Map<Integer, List<Integer>> containsMap = new LinkedHashMap<Integer, List<Integer>>();
        if (containResult != null) {
            for (ObjectInfo obj : containResult.objects) {
                if (obj.partnerLabel > 0) {
                    List<Integer> list = containsMap.get(obj.partnerLabel);
                    if (list == null) {
                        list = new ArrayList<Integer>();
                        containsMap.put(obj.partnerLabel, list);
                    }
                    list.add(obj.label);
                }
            }
        }
        List<ObjectInfo> sourceObjects = cachedObjects != null
                ? cachedObjects.get(sourceIdx) : extractObjects(images.get(sourceIdx));

        for (ObjectInfo obj : sourceObjects) {
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Image", row, sourceName);
            rt.setValue("vs", row, targetName);
            rt.addValue("Label", obj.label);
            rt.addValue("Volume (voxels)", obj.voxelCount);
            Integer partner = colocMap.get(obj.label);
            int partnerVal = partner != null ? partner : 0;
            rt.addValue("Colocalized", partnerVal > 0 ? 1 : 0);
            rt.addValue("Coloc Partner Label", partnerVal);
            List<Integer> contained = containsMap.get(obj.label);
            int containCount = contained != null ? contained.size() : 0;
            rt.addValue("Contains", containCount > 0 ? 1 : 0);
            rt.addValue("Contains Count", containCount);
            rt.setValue("Contains Partner Labels", row,
                    containCount > 0 ? labelsToString(contained) : "");
            if (extendedData) {
                rt.addValue("Centroid X (px)", obj.cx);
                rt.addValue("Centroid Y (px)", obj.cy);
                rt.addValue("Centroid Z (slice)", obj.cz);
            }
        }
    }

    /**
     * Build one consolidated table for sourceImg vs targetImg.
     *
     * @param sourceName  this image's name (rows are its objects)
     * @param targetName  the other image's name
     * @param sourceIdx   index into images/cachedObjects for the source
     * @param colocResult source→target direction result (colocalization data)
     * @param containResult target→source direction result (invert for containment), may be null
     * @param extendedData  append volume and centroid columns
     */
    private void showConsolidatedTable(String sourceName, String targetName,
                                        int sourceIdx, DirectionResult colocResult,
                                        DirectionResult containResult, boolean extendedData) {
        // Build colocalization lookup: sourceLabel → partnerLabel
        Map<Integer, Integer> colocMap = new LinkedHashMap<Integer, Integer>();
        for (ObjectInfo obj : colocResult.objects) {
            colocMap.put(obj.label, obj.partnerLabel);
        }

        // Build containment lookup: sourceLabel → list of target labels whose centroid is inside it
        Map<Integer, List<Integer>> containsMap = new LinkedHashMap<Integer, List<Integer>>();
        if (containResult != null) {
            for (ObjectInfo obj : containResult.objects) {
                if (obj.partnerLabel > 0) {
                    List<Integer> list = containsMap.get(obj.partnerLabel);
                    if (list == null) {
                        list = new ArrayList<Integer>();
                        containsMap.put(obj.partnerLabel, list);
                    }
                    list.add(obj.label);
                }
            }
        }

        // Get all source objects (sorted by label via extractObjects order)
        List<ObjectInfo> sourceObjects = cachedObjects != null
                ? cachedObjects.get(sourceIdx) : extractObjects(images.get(sourceIdx));

        ResultsTable rt = new ResultsTable();
        for (ObjectInfo obj : sourceObjects) {
            rt.incrementCounter();
            rt.addValue("Label", obj.label);

            // Colocalized: this object's centroid in target
            Integer partner = colocMap.get(obj.label);
            int partnerVal = partner != null ? partner : 0;
            rt.addValue("Colocalized (" + sourceName + " in " + targetName + ")",
                    partnerVal > 0 ? 1 : 0);
            rt.addValue("Coloc Partner Label", partnerVal);

            // Contains: target centroids inside this object
            List<Integer> contained = containsMap.get(obj.label);
            int containCount = contained != null ? contained.size() : 0;
            rt.addValue("Contains (" + targetName + " in " + sourceName + ")",
                    containCount > 0 ? 1 : 0);
            rt.addValue("Contains Count", containCount);
            int row = rt.getCounter() - 1;
            rt.setValue("Contains Partner Labels", row,
                    containCount > 0 ? labelsToString(contained) : "");

            // Optional extended data at end
            if (extendedData) {
                rt.addValue("Volume (voxels)", obj.voxelCount);
                rt.addValue("Centroid X (px)", obj.cx);
                rt.addValue("Centroid Y (px)", obj.cy);
                rt.addValue("Centroid Z (slice)", obj.cz);
            }
        }
        if (displayResults) rt.show("CPC: " + sourceName + " vs " + targetName);
        autoSave(rt, objectsSaveDir, "CPC_" + sanitize(sourceName) + "_vs_" + sanitize(targetName) + ".csv");
    }

    /** Join a list of label integers into a comma-separated string for the table. */
    private static String labelsToString(List<Integer> labels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(labels.get(i));
        }
        return sb.toString();
    }

    // ── Multi-target analysis ─────────────────────────────────────────

    /**
     * For each image as source, test its objects against all other images as targets.
     * Builds combination data showing which targets each object colocalizes with.
     */
    public void runMultiTarget() {
        List<Channel> chans = getOrBuildChannels();
        int n = chans.size();

        for (sc.fiji.cpc.core.MultiTargetResult core : MultiTargetSummary.run(chans)) {
            IJ.showStatus("CPC Multi: " + core.sourceName() + " → " + core.targetNames() + "...");
            MultiTargetResult mt = new MultiTargetResult();
            mt.sourceName = core.sourceName();
            mt.targetNames = new ArrayList<String>(core.targetNames());
            mt.objects = new ArrayList<ObjectInfo>(core.objects().size());
            mt.objectPartners = new ArrayList<Map<String, Integer>>(core.objects().size());
            for (int k = 0; k < core.objects().size(); k++) {
                // partnerLabel stays 0 here, as it always has. In a
                // multi-target row the answer is the per-target map, and
                // picking one target's partner to put in a single field would
                // be an arbitrary choice presented as a fact.
                ObjectInfo object = toObjectInfo(core.objects().get(k));
                object.partnerLabel = 0;
                mt.objects.add(object);
                mt.objectPartners.add(new LinkedHashMap<String, Integer>(core.partnersFor(k)));
            }
            mt.sourceTotal = mt.objects.size();
            multiTargetResults.add(mt);
        }
        IJ.showStatus("CPC Multi: Done (" + n + " source images).");
    }

    /** Build one multi-target per-object table per source image without displaying or saving. */
    public List<ResultsTable> getMultiTargetPerObjectTables(boolean extendedData) {
        List<ResultsTable> tables = new ArrayList<ResultsTable>();
        for (MultiTargetResult mt : multiTargetResults) {
            ResultsTable rt = new ResultsTable();
            for (int k = 0; k < mt.objects.size(); k++) {
                ObjectInfo obj = mt.objects.get(k);
                Map<String, Integer> partners = mt.objectPartners.get(k);
                rt.incrementCounter();
                rt.addValue("Label", obj.label);
                int hits = 0;
                for (String target : mt.targetNames) {
                    int partner = partners.get(target);
                    rt.addValue(target + " Coloc", partner > 0 ? 1 : 0);
                    rt.addValue(target + " Partner", partner);
                    if (partner > 0) hits++;
                }
                rt.addValue("Targets Hit", hits);
                if (extendedData) {
                    rt.addValue("Volume (voxels)", obj.voxelCount);
                    rt.addValue("Centroid X (px)", obj.cx);
                    rt.addValue("Centroid Y (px)", obj.cy);
                    rt.addValue("Centroid Z (slice)", obj.cz);
                }
            }
            tables.add(rt);
        }
        return tables;
    }

    public void showMultiTargetPerObjectResults(boolean extendedData) {
        List<ResultsTable> tables = getMultiTargetPerObjectTables(extendedData);
        for (int i = 0; i < tables.size(); i++) {
            ResultsTable rt = tables.get(i);
            MultiTargetResult mt = multiTargetResults.get(i);
            if (displayResults) rt.show("CPC Multi: " + mt.sourceName);
            autoSave(rt, multiSaveDir, "CPC_Multi_" + sanitize(mt.sourceName) + ".csv");
        }
    }

    /** Build the multi-target summary table without displaying or saving. */
    public ResultsTable getMultiTargetSummaryTable() {
        ResultsTable rt = new ResultsTable();
        for (MultiTargetResult mt : multiTargetResults) {
            // Count combination patterns
            Map<String, Integer> patternCounts = new LinkedHashMap<String, Integer>();
            int anyCount = 0;

            for (int k = 0; k < mt.objects.size(); k++) {
                Map<String, Integer> partners = mt.objectPartners.get(k);
                StringBuilder pattern = new StringBuilder();
                for (String target : mt.targetNames) {
                    if (partners.get(target) > 0) {
                        if (pattern.length() > 0) pattern.append(" + ");
                        pattern.append(target);
                    }
                }
                String key = pattern.length() > 0 ? pattern.toString() : "None";
                if (pattern.length() > 0) anyCount++;
                Integer count = patternCounts.get(key);
                patternCounts.put(key, count == null ? 1 : count + 1);
            }

            // Add rows for each combination pattern
            for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
                int row = rt.getCounter();
                rt.incrementCounter();
                rt.setValue("Source", row, mt.sourceName);
                rt.setValue("Pattern", row, entry.getKey());
                rt.addValue("Count", entry.getValue());
                rt.addValue("% of Source",
                        Math.round(entry.getValue() * 10000.0 / mt.sourceTotal) / 100.0);
            }

            // The None row is always present, even at zero.
            //
            // It is documented as script-readable: a script locates it and
            // reads the non-colocalized count directly. Emitting it only when
            // something failed to colocalize means the row vanishes on exactly
            // the datasets where everything worked, and a script that indexes
            // rows positionally then reads the totals row instead — silently,
            // and with a number that looks plausible.
            if (!patternCounts.containsKey("None")) {
                int noneRow = rt.getCounter();
                rt.incrementCounter();
                rt.setValue("Source", noneRow, mt.sourceName);
                rt.setValue("Pattern", noneRow, "None");
                rt.addValue("Count", 0);
                rt.addValue("% of Source", 0.0);
            }

            // Totals row
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Source", row, mt.sourceName);
            rt.setValue("Pattern", row, "— Any —");
            rt.addValue("Count", anyCount);
            rt.addValue("% of Source",
                Math.round(anyCount * 10000.0 / mt.sourceTotal) / 100.0);
        }
        return rt;
    }

    public void showMultiTargetSummary() {
        ResultsTable rt = getMultiTargetSummaryTable();
        if (displayResults) rt.show("CPC Multi-Target Summary");
        autoSave(rt, multiSaveDir, "CPC_Multi-Target_Summary.csv");
    }

    public List<MultiTargetResult> getMultiTargetResults() { return multiTargetResults; }

    // ── Centroid label maps ──────────────────────────────────────────

    /**
     * Creates centroid label maps: for each image, duplicates it and draws
     * all other images' centroids on top as cross markers.
     */
    /** Build centroid label maps without displaying or saving. */
    public List<ImagePlus> getCentroidLabelMaps() {
        List<Channel> chans = getOrBuildChannels();
        List<ImagePlus> maps = new ArrayList<ImagePlus>();
        for (int i = 0; i < chans.size(); i++) {
            StringBuilder otherNames = new StringBuilder();
            for (int j = 0; j < chans.size(); j++) {
                if (j == i) continue;
                if (otherNames.length() > 0) otherNames.append("+");
                otherNames.append(chans.get(j).name());
            }
            maps.add(CentroidMapBuilder.build(chans.get(i), chans,
                    chans.get(i).name() + " + " + otherNames + " centroids"));
        }
        return maps;
    }

    public void showCentroidLabelMaps() {
        List<ImagePlus> maps = getCentroidLabelMaps();
        for (int i = 0; i < maps.size(); i++) {
            ImagePlus base = images.get(i);
            ImagePlus map = maps.get(i);
            if (displayResults) map.show();
            if (mapsSaveDir != null) {
                ensureSaveDir(mapsSaveDir);
                String filename = savePrefix + "CPC_Centroid_Map_" + sanitize(base.getTitle()) + ".tif";
                IJ.saveAsTiff(map, mapsSaveDir + "/" + filename);
                IJ.log("CPC: Saved " + filename);
            }
        }
    }

    /** @deprecated Use {@link #showCentroidLabelMaps()} instead. */
    public void showCentroidLabelMaps(ImagePlus origA, ImagePlus origB) {
        showCentroidLabelMaps();
    }

    // ── Getters ────────────────────────────────────────────────────

    public List<DirectionResult> getResults() { return results; }

    /** Legacy getter — returns first forward result (A→B) or null. */
    public DirectionResult getResultAtoB() { return results.size() > 0 ? results.get(0) : null; }
    /** Legacy getter — returns first reverse result (B→A) or null. */
    public DirectionResult getResultBtoA() { return results.size() > 1 ? results.get(1) : null; }
}
