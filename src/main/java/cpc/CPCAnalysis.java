package cpc;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centre-Particle Coincidence analysis.
 * <p>
 * For each object in image A, checks whether its centroid falls inside
 * an object in image B (and vice versa if bidirectional).
 * Supports 2–5 images with all pairwise comparisons.
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

    private List<List<ObjectInfo>> getOrExtractObjects() {
        if (cachedObjects != null) return cachedObjects;
        int n = images.size();
        cachedObjects = new ArrayList<List<ObjectInfo>>();
        for (int i = 0; i < n; i++) {
            IJ.showStatus("CPC: Extracting objects from image " + (i + 1) + "/" + n + "...");
            ImagePlus raw = (rawImages != null && i < rawImages.size()) ? rawImages.get(i) : null;
            cachedObjects.add(extractObjects(images.get(i), raw));
        }
        return cachedObjects;
    }

    public void run() {
        int n = images.size();
        List<List<ObjectInfo>> allObjects = getOrExtractObjects();

        // Run all pairwise comparisons
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                ImagePlus imgI = images.get(i);
                ImagePlus imgJ = images.get(j);
                List<ObjectInfo> objI = allObjects.get(i);
                List<ObjectInfo> objJ = allObjects.get(j);

                // Forward: i centroids in j
                IJ.showStatus("CPC: Testing " + imgI.getTitle() + " in " + imgJ.getTitle() + "...");
                List<ObjectInfo> objICopy = copyObjects(objI);
                testCoincidence(objICopy, imgJ);
                results.add(buildResult(imgI.getTitle(), imgJ.getTitle(), objICopy, objJ.size()));

                if (bidirectional) {
                    // Reverse: j centroids in i
                    IJ.showStatus("CPC: Testing " + imgJ.getTitle() + " in " + imgI.getTitle() + "...");
                    List<ObjectInfo> objJCopy = copyObjects(objJ);
                    testCoincidence(objJCopy, imgI);
                    results.add(buildResult(imgJ.getTitle(), imgI.getTitle(), objJCopy, objI.size()));
                }
            }
        }

        int totalObjs = 0;
        for (List<ObjectInfo> objs : allObjects) totalObjs += objs.size();
        IJ.showStatus("CPC: Done (" + totalObjs + " objects across " + n + " images, " + results.size() + " comparisons).");
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
     */
    public static List<ObjectInfo> extractObjects(ImagePlus img, ImagePlus rawImg) {
        ImageStack stack = img.getStack();
        int w = img.getWidth();
        int h = img.getHeight();
        int nSlices = stack.getSize();

        if (rawImg != null) {
            // Intensity-weighted path: [sumX*I, sumY*I, sumZ*I, sumI, sumX, sumY, sumZ, count]
            ImageStack rawStack = rawImg.getStack();
            Map<Integer, double[]> stats = new LinkedHashMap<Integer, double[]>();

            for (int z = 0; z < nSlices; z++) {
                ImageProcessor ip = stack.getProcessor(z + 1);
                ImageProcessor rp = rawStack.getProcessor(z + 1);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int label = (int) ip.getf(x, y);
                        if (label <= 0) continue;
                        double intensity = rp.getf(x, y);
                        double[] s = stats.get(label);
                        if (s == null) {
                            s = new double[8];
                            stats.put(label, s);
                        }
                        s[0] += x * intensity;
                        s[1] += y * intensity;
                        s[2] += z * intensity;
                        s[3] += intensity;
                        s[4] += x;
                        s[5] += y;
                        s[6] += z;
                        s[7]++;
                    }
                }
                IJ.showProgress(z + 1, nSlices * 2);
            }

            List<ObjectInfo> objects = new ArrayList<ObjectInfo>(stats.size());
            for (Map.Entry<Integer, double[]> entry : stats.entrySet()) {
                ObjectInfo obj = new ObjectInfo(entry.getKey());
                double[] s = entry.getValue();
                obj.voxelCount = (int) s[7];
                if (s[3] > 0) {
                    obj.cx = s[0] / s[3];
                    obj.cy = s[1] / s[3];
                    obj.cz = s[2] / s[3];
                } else {
                    // Zero total intensity — fall back to geometric centroid
                    obj.cx = s[4] / s[7];
                    obj.cy = s[5] / s[7];
                    obj.cz = s[6] / s[7];
                }
                objects.add(obj);
            }
            return objects;
        }

        // Geometric centroid path (original): accumulate [sumX, sumY, sumZ, count]
        Map<Integer, long[]> stats = new LinkedHashMap<Integer, long[]>();

        for (int z = 0; z < nSlices; z++) {
            ImageProcessor ip = stack.getProcessor(z + 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int label = (int) ip.getf(x, y);
                    if (label <= 0) continue;
                    long[] s = stats.get(label);
                    if (s == null) {
                        s = new long[4];
                        stats.put(label, s);
                    }
                    s[0] += x;
                    s[1] += y;
                    s[2] += z;
                    s[3]++;
                }
            }
            IJ.showProgress(z + 1, nSlices * 2);
        }

        List<ObjectInfo> objects = new ArrayList<ObjectInfo>(stats.size());
        for (Map.Entry<Integer, long[]> entry : stats.entrySet()) {
            ObjectInfo obj = new ObjectInfo(entry.getKey());
            long[] s = entry.getValue();
            obj.voxelCount = (int) s[3];
            obj.cx = (double) s[0] / s[3];
            obj.cy = (double) s[1] / s[3];
            obj.cz = (double) s[2] / s[3];
            objects.add(obj);
        }
        return objects;
    }

    /**
     * For each object, look up the voxel value in the target label image
     * at the object's centroid position.
     */
    public static void testCoincidence(List<ObjectInfo> objects, ImagePlus targetImage) {
        ImageStack stack = targetImage.getStack();
        int w = targetImage.getWidth();
        int h = targetImage.getHeight();
        int nSlices = stack.getSize();

        for (ObjectInfo obj : objects) {
            int x = (int) Math.round(obj.cx);
            int y = (int) Math.round(obj.cy);
            int z = (int) Math.round(obj.cz);

            if (x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < nSlices) {
                obj.partnerLabel = (int) stack.getProcessor(z + 1).getf(x, y);
            }
        }
    }

    private DirectionResult buildResult(String source, String target, List<ObjectInfo> objects, int targetTotalObjects) {
        DirectionResult r = new DirectionResult();
        r.sourceName = source;
        r.targetName = target;
        r.objects = objects;
        r.totalObjects = objects.size();
        r.targetTotalObjects = targetTotalObjects;
        r.colocalizedCount = 0;
        for (ObjectInfo obj : objects) {
            if (obj.isColocalized()) r.colocalizedCount++;
        }
        return r;
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

    /** Find cached objects for an image by title. */
    private List<ObjectInfo> findTargetObjects(String title) {
        if (cachedObjects != null) {
            for (int i = 0; i < images.size(); i++) {
                if (images.get(i).getTitle().equals(title)) {
                    return cachedObjects.get(i);
                }
            }
        }
        for (ImagePlus img : images) {
            if (img.getTitle().equals(title)) return extractObjects(img);
        }
        return new ArrayList<ObjectInfo>();
    }

    // ── Multi-target analysis ─────────────────────────────────────────

    /**
     * For each image as source, test its objects against all other images as targets.
     * Builds combination data showing which targets each object colocalizes with.
     */
    public void runMultiTarget() {
        int n = images.size();
        List<List<ObjectInfo>> allObjects = getOrExtractObjects();

        for (int src = 0; src < n; src++) {
            MultiTargetResult mt = new MultiTargetResult();
            mt.sourceName = images.get(src).getTitle();
            mt.targetNames = new ArrayList<String>();
            mt.objects = allObjects.get(src);
            mt.sourceTotal = mt.objects.size();
            mt.objectPartners = new ArrayList<Map<String, Integer>>();
            for (int k = 0; k < mt.objects.size(); k++) {
                mt.objectPartners.add(new LinkedHashMap<String, Integer>());
            }

            for (int tgt = 0; tgt < n; tgt++) {
                if (tgt == src) continue;
                String targetName = images.get(tgt).getTitle();
                mt.targetNames.add(targetName);

                IJ.showStatus("CPC Multi: " + mt.sourceName + " → " + targetName + "...");
                List<ObjectInfo> copy = copyObjects(mt.objects);
                testCoincidence(copy, images.get(tgt));

                for (int k = 0; k < copy.size(); k++) {
                    mt.objectPartners.get(k).put(targetName, copy.get(k).partnerLabel);
                }
            }

            multiTargetResults.add(mt);
        }
        IJ.showStatus("CPC Multi: Done (" + n + " source images).");
    }

    public void showMultiTargetPerObjectResults(boolean extendedData) {
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
            if (displayResults) rt.show("CPC Multi: " + mt.sourceName);
            autoSave(rt, multiSaveDir, "CPC_Multi_" + sanitize(mt.sourceName) + ".csv");
        }
    }

    public void showMultiTargetSummary() {
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

            // Totals row
            int row = rt.getCounter();
            rt.incrementCounter();
            rt.setValue("Source", row, mt.sourceName);
            rt.setValue("Pattern", row, "— Any —");
            rt.addValue("Count", anyCount);
            rt.addValue("% of Source",
                    Math.round(anyCount * 10000.0 / mt.sourceTotal) / 100.0);
        }
        if (displayResults) rt.show("CPC Multi-Target Summary");
        autoSave(rt, multiSaveDir, "CPC_Multi-Target_Summary.csv");
    }

    public List<MultiTargetResult> getMultiTargetResults() { return multiTargetResults; }

    // ── Centroid label maps ──────────────────────────────────────────

    /**
     * Creates centroid label maps: for each image, duplicates it and draws
     * all other images' centroids on top as cross markers.
     */
    public void showCentroidLabelMaps() {
        int n = images.size();
        for (int i = 0; i < n; i++) {
            ImagePlus base = images.get(i);
            // Collect centroids from all other images
            List<ObjectInfo> allCentroids = new ArrayList<ObjectInfo>();
            StringBuilder otherNames = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                // Find the result where source=j, target=i to get j's objects
                List<ObjectInfo> objs = findObjectsForImage(j, i);
                if (objs != null) {
                    allCentroids.addAll(objs);
                } else {
                    // Extract fresh if no result exists (unidirectional case)
                    allCentroids.addAll(extractObjects(images.get(j)));
                }
                if (otherNames.length() > 0) otherNames.append("+");
                otherNames.append(images.get(j).getTitle());
            }
            ImagePlus map = createCentroidLabelMap(base, allCentroids,
                    base.getTitle() + " + " + otherNames + " centroids");
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

    private List<ObjectInfo> findObjectsForImage(int sourceIdx, int targetIdx) {
        String sourceName = images.get(sourceIdx).getTitle();
        String targetName = images.get(targetIdx).getTitle();
        for (DirectionResult r : results) {
            if (r.sourceName.equals(sourceName) && r.targetName.equals(targetName)) {
                return r.objects;
            }
        }
        return null;
    }

    private ImagePlus createCentroidLabelMap(ImagePlus labelImg, List<ObjectInfo> centroids, String title) {
        ImagePlus dup = labelImg.duplicate();
        dup.setTitle(title);
        ImageStack stack = dup.getStack();
        int w = dup.getWidth();
        int h = dup.getHeight();
        int nSlices = stack.getSize();

        int radius = 2;
        for (ObjectInfo obj : centroids) {
            int cx = (int) Math.round(obj.cx);
            int cy = (int) Math.round(obj.cy);
            int cz = (int) Math.round(obj.cz);
            if (cz < 0 || cz >= nSlices) continue;

            ImageProcessor ip = stack.getProcessor(cz + 1);
            // Draw a cross marker
            for (int d = -radius; d <= radius; d++) {
                int px = cx + d;
                int py = cy + d;
                if (px >= 0 && px < w) ip.setf(px, cy, obj.label);
                if (py >= 0 && py < h) ip.setf(cx, py, obj.label);
            }
        }
        return dup;
    }

    // ── Getters ────────────────────────────────────────────────────

    public List<DirectionResult> getResults() { return results; }

    /** Legacy getter — returns first forward result (A→B) or null. */
    public DirectionResult getResultAtoB() { return results.size() > 0 ? results.get(0) : null; }
    /** Legacy getter — returns first reverse result (B→A) or null. */
    public DirectionResult getResultBtoA() { return results.size() > 1 ? results.get(1) : null; }
}
