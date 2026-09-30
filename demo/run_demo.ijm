// Run the saved macro in Fiji with Centre-Particle Coincidence installed.
macroFile = getInfo("macro.filepath");
if (macroFile == "") exit("Open this saved macro in Fiji's editor, then choose Run.");
// CPC's macro interface requires forward slashes, including on Windows.
demoDir = replace(File.getParent(macroFile), "\\\\", "/") + "/";
inputDir = demoDir + "data/";
if (!File.exists(inputDir + "Demo_A.tif") || !File.exists(inputDir + "Demo_B.tif"))
    exit("Keep run_demo.ijm beside the supplied data folder.");
outputDir = getDirectory("Choose an EMPTY folder for the CPC demo outputs");
outputDir = replace(outputDir, "\\\\", "/");
outputEntries = getFileList(outputDir);
if (outputEntries.length != 0) exit("Choose an empty output folder.");
// Geometric centroids, both directions, per-object and summary tables.
options = "image1_path=[" + inputDir + "Demo_A.tif] "
    + "image2_path=[" + inputDir + "Demo_B.tif] "
    + "bidirectional objects summary extended auto_save hide_display "
    + "save_dir=[" + outputDir + "]";
started = getTime();
run("Centre-Particle Coincidence", options);
if (!File.exists(outputDir + "CPC/Objects/CPC_Summary.csv"))
    exit("Demo did not produce its summary. Check the ImageJ Log for errors.");
print("Centre-Particle Coincidence simulated demo elapsed seconds: " + (getTime() - started) / 1000);
print("Expected: Demo_A in Demo_B = 3/4 (75%); reverse = 1/4 (25%).");
print("Demo output folder: " + outputDir);
