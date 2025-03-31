package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.PolygonRoi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.process.ImageStatistics;
import ij.plugin.frame.RoiManager;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import com.sebastianmicu.scheli.Debug;

public class SaveData {
    // Measurement flags for comprehensive analysis
    private static final int ORIGINAL_MEASUREMENTS = Measurements.AREA | Measurements.MEAN |
                                                     Measurements.STD_DEV | Measurements.MODE |
                                                     Measurements.MIN_MAX | Measurements.CENTROID |
                                                     Measurements.CENTER_OF_MASS | Measurements.RECT |
                                                     Measurements.ELLIPSE | Measurements.MEDIAN |
                                                     Measurements.SKEWNESS | Measurements.KURTOSIS;
    
    // Neighbor radius (in pixels) from configuration
    private double NEIGHBOUR_RADIUS;
    
    // Grid cell size for spatial indexing (should be larger than typical search radius)
    private static final int GRID_CELL_SIZE = 100;
    
    private static final int STAIN_MEASUREMENTS = Measurements.MEAN | Measurements.STD_DEV |
                                                 Measurements.MODE | Measurements.MIN_MAX |
                                                 Measurements.MEDIAN | Measurements.SKEWNESS |
                                                 Measurements.KURTOSIS;
    
    // Locale for decimal point formatting
    private static final java.util.Locale DECIMAL_LOCALE = java.util.Locale.US;
    
    private ImagePlus originalImage;
    private ImagePlus hematoxylinImage;
    private ImagePlus eosinImage;
    private List<Roi> nucleiROIs;
    private List<Roi> cytoplasmROIs;
    private List<Roi> cellROIs;
    private List<Roi> vesselROIs;
    private boolean useSemicolon;
    private boolean useCommaForDecimals;
    private float scale;
    private boolean ignoreBorderVessels;
    
    /**
     * Constructor with all ROI types
     */
    public SaveData(ImagePlus originalImage, ImagePlus hematoxylinImage, ImagePlus eosinImage,
                   List<Roi> nucleiROIs, List<Roi> cytoplasmROIs, List<Roi> cellROIs,
                   List<Roi> vesselROIs) {
        this.originalImage = originalImage;
        this.hematoxylinImage = hematoxylinImage;
        this.eosinImage = eosinImage;
        this.nucleiROIs = nucleiROIs != null ? nucleiROIs : new ArrayList<>();
        this.cytoplasmROIs = cytoplasmROIs != null ? cytoplasmROIs : new ArrayList<>();
        this.cellROIs = cellROIs != null ? cellROIs : new ArrayList<>();
        this.vesselROIs = vesselROIs != null ? vesselROIs : new ArrayList<>();
        this.useSemicolon = ConfigVariables.getUseSemicolons();
        this.useCommaForDecimals = ConfigVariables.getUseCommaForDecimals();
        this.scale = ConfigVariables.getPathScale();
        this.NEIGHBOUR_RADIUS = ConfigVariables.getNeighborRadius();
        this.ignoreBorderVessels = ConfigVariables.getIgnoreBorderVessels();
        
        // Log the scale and neighbor radius being used
        Debug.log("Using scale factor: " + this.scale);
        Debug.log("Using neighbor radius: " + this.NEIGHBOUR_RADIUS);
    }
    
    //Apply scale to an ImagePlus
    private void applyScale(ImagePlus imp) {
        if (scale != 1.0f) {
            // For um/pixel scale, pixelWidth and pixelHeight should be set to scale directly
            // This tells ImageJ that each pixel is 'scale' micrometers wide/tall
            imp.getCalibration().pixelWidth = scale;
            imp.getCalibration().pixelHeight = scale;
            imp.getCalibration().setUnit("µm");
            Debug.log("Applied scale factor " + scale + " µm/pixel to " + imp.getTitle());
        }
    }
    
    /**
     * Measure all ROIs and save results to CSV files
     */
    public void measureROIs() {
        Debug.log("Measuring ROIs...");
        
        // Show the original image if it's not already visible
        if (!originalImage.isVisible()) {
            originalImage.show();
            // Move window off-screen
            if (originalImage.getWindow() != null) {
                originalImage.getWindow().setLocation(-1000, -1000);
            }
        }
        
        // Create a duplicate for measurements to avoid modifying the original
        ImagePlus impDup = originalImage.duplicate();
        impDup.setTitle("Duplicate of " + originalImage.getTitle());
        
        // Apply scale to the duplicate image
        applyScale(impDup);
        
        impDup.show();
        // Move window off-screen
        if (impDup.getWindow() != null) {
            impDup.getWindow().setLocation(-1000, -1000);
        }
        
        // Get the ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager();
        }
        
        // Add all ROIs to the manager for easier access
        rm.reset();
        addAllRoisToManager(rm);
        
        Debug.log("Added " + rm.getCount() + " ROIs to manager for measurement");
    }
    
    /**
     * Save all measurements to CSV files
     */
    public void saveToCSV(String baseName) {
        CountDownLatch latch = new CountDownLatch(1);
        
        Thread saveThread = new Thread(() -> {
            try {
                // Create output directory
                String outputPath = ConfigVariables.getPathOutput();
                if (outputPath == null || outputPath.isEmpty()) {
                    outputPath = System.getProperty("user.home");
                }
                
                File outputDir = new File(outputPath);
                outputDir.mkdirs();
                
                // Create subfolders for different types of output
                File individualDir = new File(outputDir, "individual");
                File averagesDir = new File(outputDir, "averages");
                File roisetsDir = new File(outputDir, "roisets");
                
                individualDir.mkdirs();
                averagesDir.mkdirs();
                roisetsDir.mkdirs();
                
                // Get the duplicate image for measurements
                ImagePlus imp = WindowManager.getImage("Duplicate of " + originalImage.getTitle());
                if (imp == null) {
                    imp = originalImage.duplicate();
                    imp.setTitle("Duplicate of " + originalImage.getTitle());
                    // Apply scale to the duplicate image
                    applyScale(imp);
                    imp.show();
                    // Move window off-screen
                    if (imp.getWindow() != null) {
                        imp.getWindow().setLocation(-1000, -1000);
                    }
                }
                
                // Apply scale to hematoxylin and eosin images if they exist
                if (hematoxylinImage != null) {
                    applyScale(hematoxylinImage);
                }
                if (eosinImage != null) {
                    applyScale(eosinImage);
                }
                
                // Create results files
                File resultsFile = new File(individualDir, baseName + "_data.csv");
                File averagesFile = new File(averagesDir, baseName + "_averages.csv");
                
                // Save measurements to results files
                saveData(resultsFile, imp);
                Debug.log("Individual measurements saved to: " + resultsFile.getAbsolutePath());
                
                // Save averages if enabled
                if (ConfigVariables.getSaveAverages()) {
                    saveAverages(averagesFile, imp);
                    Debug.log("Average measurements saved to: " + averagesFile.getAbsolutePath());
                }
                
                // Save ROIs if enabled
                if (ConfigVariables.getSaveRoi()) {
                    File roiFile = new File(roisetsDir, baseName + "_ROIs.zip");
                    saveRois(roiFile);
                    Debug.log("ROIs saved to: " + roiFile.getAbsolutePath());
                }
                
                // Clean up
                if (imp != null && imp != originalImage) {
                    imp.changes = false;
                    imp.close();
                }
                
                Debug.log("Data saved successfully to: " + outputDir.getAbsolutePath());
                
            } catch (Exception e) {
                Debug.log("Error saving data: " + e.getMessage());
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        
        saveThread.start();
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Add all ROIs to the ROI Manager
     */
    private void addAllRoisToManager(RoiManager rm) {
        // Add nuclei ROIs
        for (Roi roi : nucleiROIs) {
            rm.addRoi(roi);
        }
        
        // Add cytoplasm ROIs
        for (Roi roi : cytoplasmROIs) {
            rm.addRoi(roi);
        }
        
        // Add cell ROIs
        for (Roi roi : cellROIs) {
            rm.addRoi(roi);
        }
        
        // Add vessel ROIs
        for (Roi roi : vesselROIs) {
            rm.addRoi(roi);
        }
    }
    
    /**
     * Save detailed measurements for all ROIs
     */
    private void saveData(File resultsFile, ImagePlus imp) {
        // Check if deconvolution images exist
        boolean hasDeconvolutedImages = (hematoxylinImage != null && eosinImage != null);
        
        // Get the ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null || rm.getCount() == 0) {
            IJ.error("No ROIs in ROI Manager");
            return;
        }

        // Create a new ResultsTable
        ResultsTable rt = new ResultsTable();
        
        // Get all ROIs as an array
        Roi[] rois = rm.getRoisAsArray();

        // Collect vessel coordinates and build spatial index for fast distance calculations
        List<VesselData> vesselDataList = new ArrayList<>();
        SpatialGrid<VesselData> vesselGrid = new SpatialGrid<>(
            vessel -> vessel.gridX,
            vessel -> vessel.gridY
        );
        
        // Collect nucleus data for neighbor counting
        List<NucleusData> nucleusDataList = new ArrayList<>();
        SpatialGrid<NucleusData> nucleusGrid = new SpatialGrid<>(
            nucleus -> nucleus.gridX,
            nucleus -> nucleus.gridY
        );
        
        // Log the number of ROIs to help with debugging
        Debug.log("Total ROIs in manager: " + rois.length);
        int vesselCount = 0;
        int nucleusCount = 0;
        
        // First pass: collect all vessel and nucleus data
        for (int j = 0; j < rois.length; j++) {
            Roi roi = rois[j];
            String roiName = rm.getName(j);
            
            // Set ROI and get centroid
            imp.setRoi(roi);
            imp.getProcessor().setRoi(roi);
            ImageStatistics stats = imp.getStatistics(Measurements.AREA | Measurements.CENTROID);
            
            // Check for vessel ROIs - only include ROIs that start with "Vessel_"
            // If ignoreBorderVessels is true, exclude vessels with "_Border" in their name
            if (roiName.startsWith("Vessel_") && (!ignoreBorderVessels || !roiName.contains("_Border"))) {
                
                double vesselArea = stats.area;
                double vesselRadius = vesselArea > 0 ? Math.sqrt(vesselArea / Math.PI) : 0;
                
                VesselData vessel = new VesselData(
                    roiName,
                    stats.xCentroid,
                    stats.yCentroid,
                    vesselRadius
                );
                
                vesselDataList.add(vessel);
                vesselGrid.add(vessel);
                vesselCount++;
                Debug.log("Added vessel: " + roiName);
            }
            
            // Check for nucleus ROIs
            if (roiName.contains("Nucleus")) {
                int nucleusNumber = -1;
                try {
                    String[] parts = roiName.split("_");
                    if (parts.length >= 2) {
                        nucleusNumber = Integer.parseInt(parts[1].split("_")[0]);
                    }
                } catch (Exception e) {
                    // If parsing fails, just use -1
                }
                
                // Get full measurements for ellipse parameters
                imp.setRoi(roi);
                ImageStatistics fullStats = imp.getStatistics(ORIGINAL_MEASUREMENTS);
                
                NucleusData nucleus = new NucleusData(
                    roiName,
                    j,  // Store the ROI index
                    nucleusNumber,
                    stats.xCentroid,
                    stats.yCentroid,
                    fullStats.major,
                    fullStats.minor,
                    fullStats.angle
                );
                
                nucleusDataList.add(nucleus);
                nucleusGrid.add(nucleus);
                nucleusCount++;
            }
        }
        
        Debug.log("Found " + vesselCount + " vessels and " + nucleusCount + " nuclei for calculations");

        // Create a sorted list of ROIs based on their names and indices
        List<Integer> roiIndices = sortRoiIndices(rm, rois);
        
        // Process ROIs in the sorted order
        for (int idx : roiIndices) {
            Roi roi = rois[idx];
            String roiName = rm.getName(idx);
            
            // Measure Original image with full set of measurements
            imp.setRoi(roi);
            imp.getProcessor().setRoi(roi);
            ImageStatistics stats = imp.getStatistics(ORIGINAL_MEASUREMENTS);

            rt.incrementCounter();
            rt.addValue("ROI", roiName);

            double vesselDistance = Double.NaN;
            String closestVessel = "N/A";
            int neighborCount = 0;
            double closestNeighborDistance = Double.NaN;
            String closestNeighbor = "N/A";
            
            // Extract nucleus number from ROI name
            int roiNumber = -1;
            try {
                if (roiName.contains("Nucleus_") || roiName.contains("Cell_") || roiName.contains("Cytoplasm_")) {
                    String[] parts = roiName.split("_");
                    if (parts.length >= 2) {
                        roiNumber = Integer.parseInt(parts[1].split("_")[0]);
                    }
                }
            } catch (Exception e) {
                // If parsing fails, just use -1
            }
            
            // Check if this is a nucleus ROI
            if (roiName.contains("Nucleus_")) {
                // Find the matching nucleus data
                NucleusData currentNucleus = null;
                for (NucleusData nucleus : nucleusDataList) {
                    if (nucleus.name.equals(roiName)) {
                        currentNucleus = nucleus;
                        break;
                    }
                }
                
                if (currentNucleus != null) {
                    // Calculate vessel distance if not already calculated
                    if (Double.isNaN(currentNucleus.vesselDistance)) {
                        // Create a temporary vessel data object for the current ROI
                        VesselData tempVessel = new VesselData(
                            roiName,
                            currentNucleus.x,
                            currentNucleus.y,
                            0
                        );
                        
                        // Calculate distance for all ROI types
                        if (!vesselDataList.isEmpty()) {
                            // First try to get nearby vessels using spatial grid
                            List<VesselData> nearbyVessels = vesselGrid.getNearby(tempVessel, 2);
                            
                            // If no nearby vessels found, search all vessels
                            if (nearbyVessels.isEmpty()) {
                                nearbyVessels = vesselDataList;
                                Debug.log("No nearby vessels found for " + roiName + ", searching all " + vesselDataList.size() + " vessels");
                            }
                            
                            double minDist = Double.POSITIVE_INFINITY;
                            VesselData closest = null;
                            
                            for (VesselData vessel : nearbyVessels) {
                                // Skip if comparing to itself
                                if (roiName.equals(vessel.name)) {
                                    continue;
                                }
                                
                                // Fast distance calculation
                                double dx = vessel.x - tempVessel.x;
                                double dy = vessel.y - tempVessel.y;
                                double currentDist = Math.sqrt(dx*dx + dy*dy);
                                
                                if (currentDist < minDist) {
                                    minDist = currentDist;
                                    closest = vessel;
                                }
                            }
                            
                            if (closest != null) {
                                currentNucleus.vesselDistance = minDist;
                                currentNucleus.closestVessel = closest.name;
                            }
                        }
                    }
                    
                    // Calculate neighbor count and closest neighbor if not already calculated
                    if (currentNucleus.neighborCount == 0) {
                        // Get nearby nuclei using spatial grid
                        int gridSearchRadius = (int)(NEIGHBOUR_RADIUS / GRID_CELL_SIZE) + 1;
                        List<NucleusData> nearbyNuclei = nucleusGrid.getNearby(currentNucleus, gridSearchRadius);
                        
                        double minNeighborDist = Double.POSITIVE_INFINITY;
                        NucleusData closestNucleusNeighbor = null;
                        
                        // Count neighbors within NEIGHBOUR_RADIUS and find closest
                        for (NucleusData otherNucleus : nearbyNuclei) {
                            // Skip if it's the same nucleus
                            if (otherNucleus.name.equals(currentNucleus.name)) {
                                continue;
                            }
                            
                            // Calculate center-to-center distance
                            double dx = otherNucleus.x - currentNucleus.x;
                            double dy = otherNucleus.y - currentNucleus.y;
                            double distance = Math.sqrt(dx*dx + dy*dy);
                            
                            // Count if within neighbor radius
                            if (distance <= NEIGHBOUR_RADIUS) {
                                currentNucleus.neighborCount++;
                                
                                // Calculate border-to-border distance (assuming ellipses)
                                double borderDistance = currentNucleus.calculateBorderDistance(otherNucleus);
                                
                                // Check if this is the closest neighbor
                                if (borderDistance < minNeighborDist) {
                                    minNeighborDist = borderDistance;
                                    closestNucleusNeighbor = otherNucleus;
                                }
                            }
                        }
                        
                        // Store closest neighbor information
                        if (closestNucleusNeighbor != null) {
                            currentNucleus.closestNeighborDistance = minNeighborDist;
                            currentNucleus.closestNeighbor = closestNucleusNeighbor.name;
                        }
                    }
                    
                    // Use the cached values
                    vesselDistance = currentNucleus.vesselDistance;
                    closestVessel = currentNucleus.closestVessel;
                    neighborCount = currentNucleus.neighborCount;
                    closestNeighborDistance = currentNucleus.closestNeighborDistance;
                    closestNeighbor = currentNucleus.closestNeighbor;
                }
            }
            // For Cell or Cytoplasm ROIs, use the corresponding Nucleus measurements
            else if ((roiName.contains("Cell_") || roiName.contains("Cytoplasm_")) && roiNumber >= 0) {
                // Find the corresponding nucleus
                for (NucleusData nucleus : nucleusDataList) {
                    if (nucleus.nucleusNumber == roiNumber) {
                        // Use the cached values from the nucleus
                        vesselDistance = nucleus.vesselDistance;
                        closestVessel = nucleus.closestVessel;
                        neighborCount = nucleus.neighborCount;
                        closestNeighborDistance = nucleus.closestNeighborDistance;
                        closestNeighbor = nucleus.closestNeighbor;
                        break;
                    }
                }
            }
            // For other ROI types (like vessels), calculate distance directly
            else {
                // Create a temporary vessel data object for the current ROI
                VesselData tempVessel = new VesselData(
                    roiName,
                    stats.xCentroid,
                    stats.yCentroid,
                    0
                );
                
                // Calculate distance for all ROI types
                if (!vesselDataList.isEmpty()) {
                    // First try to get nearby vessels using spatial grid
                    List<VesselData> nearbyVessels = vesselGrid.getNearby(tempVessel, 2);
                    
                    // If no nearby vessels found, search all vessels
                    if (nearbyVessels.isEmpty()) {
                        nearbyVessels = vesselDataList;
                        Debug.log("No nearby vessels found for " + roiName + ", searching all " + vesselDataList.size() + " vessels");
                    }
                    
                    double minDist = Double.POSITIVE_INFINITY;
                    VesselData closest = null;
                    
                    for (VesselData vessel : nearbyVessels) {
                        // Skip if comparing to itself
                        if (roiName.equals(vessel.name)) {
                            continue;
                        }
                        
                        // Fast distance calculation
                        double dx = vessel.x - tempVessel.x;
                        double dy = vessel.y - tempVessel.y;
                        double currentDist = Math.sqrt(dx*dx + dy*dy);
                        
                        if (currentDist < minDist) {
                            minDist = currentDist;
                            closest = vessel;
                        }
                    }
                    
                    if (closest != null) {
                        vesselDistance = minDist;
                        closestVessel = closest.name;
                    }
                }
            }
            
            // Add the measurements to the results table
            if (Double.isNaN(vesselDistance)) {
                rt.addValue("Vessel Distance", "N/A");
            } else {
                rt.addValue("Vessel Distance", vesselDistance);
            }
            
            rt.addValue("Closest Vessel", closestVessel);
            rt.addValue("Neighbor Count", neighborCount);
            
            if (Double.isNaN(closestNeighborDistance)) {
                rt.addValue("Closest Neighbor Distance", "N/A");
            } else {
                rt.addValue("Closest Neighbor Distance", closestNeighborDistance);
            }
            
            rt.addValue("Closest Neighbor", closestNeighbor);

            // Original image measurements
            double perimeter = roi.getLength();
            double[] feretValues = roi.getFeretValues();

            // Calculate convex hull area for solidity
            Roi originalRoi = (Roi)roi.clone();
            ShapeRoi convexHullRoi = getConvexHullRoi(originalRoi);

            imp.setRoi(originalRoi);
            double originalArea = imp.getStatistics(Measurements.AREA).area;
            
            imp.setRoi(convexHullRoi);
            double convexHullArea = imp.getStatistics(Measurements.AREA).area;
            
            rt.addValue("Area", stats.area);
            rt.addValue("X", stats.xCentroid);
            rt.addValue("Y", stats.yCentroid);
            rt.addValue("XM", stats.xCenterOfMass);
            rt.addValue("YM", stats.yCenterOfMass);
            rt.addValue("Perim.", perimeter);
            rt.addValue("BX", stats.roiX);
            rt.addValue("BY", stats.roiY);
            rt.addValue("Width", stats.roiWidth);
            rt.addValue("Height", stats.roiHeight);
            rt.addValue("Major", stats.major);
            rt.addValue("Minor", stats.minor);
            rt.addValue("Angle", stats.angle);
            rt.addValue("Circ.", (4 * Math.PI * stats.area) / (perimeter * perimeter));
            rt.addValue("IntDen", (stats.area) * stats.mean);
            rt.addValue("Feret", feretValues[0]);
            rt.addValue("FeretX", feretValues[3]);
            rt.addValue("FeretY", feretValues[4]);
            rt.addValue("FeretAngle", feretValues[1]);
            rt.addValue("MinFeret", feretValues[2]);
            rt.addValue("AR", stats.major / stats.minor);
            rt.addValue("Round", (4 * stats.area) / (Math.PI * stats.major * stats.major));

            // Calculate solidity (area/convex hull area)
            double solidity = originalArea / convexHullArea;
            rt.addValue("Solidity", solidity);

            rt.addValue("Mean", stats.mean);
            rt.addValue("StdDev", stats.stdDev);
            rt.addValue("Mode", stats.mode);
            rt.addValue("Min", stats.min);
            rt.addValue("Max", stats.max);
            rt.addValue("Median", stats.median);
            rt.addValue("Skew", stats.skewness);
            rt.addValue("Kurt", stats.kurtosis);

            // Only perform Hematoxylin and Eosin measurements if the images exist
            if (hasDeconvolutedImages) {
                // Hematoxylin measurements
                hematoxylinImage.setRoi(roi);
                hematoxylinImage.getProcessor().setRoi(roi);
                ImageStatistics hemaStats = hematoxylinImage.getStatistics(STAIN_MEASUREMENTS);
                rt.addValue("Hema_Mean", hemaStats.mean);
                rt.addValue("Hema_StdDev", hemaStats.stdDev);
                rt.addValue("Hema_Mode", hemaStats.mode);
                rt.addValue("Hema_Min", hemaStats.min);
                rt.addValue("Hema_Max", hemaStats.max);
                rt.addValue("Hema_Median", hemaStats.median);
                rt.addValue("Hema_Skew", hemaStats.skewness);
                rt.addValue("Hema_Kurt", hemaStats.kurtosis);
    
                // Eosin measurements
                eosinImage.setRoi(roi);
                eosinImage.getProcessor().setRoi(roi);
                ImageStatistics eosinStats = eosinImage.getStatistics(STAIN_MEASUREMENTS);
                rt.addValue("Eosin_Mean", eosinStats.mean);
                rt.addValue("Eosin_StdDev", eosinStats.stdDev);
                rt.addValue("Eosin_Mode", eosinStats.mode);
                rt.addValue("Eosin_Min", eosinStats.min);
                rt.addValue("Eosin_Max", eosinStats.max);
                rt.addValue("Eosin_Median", eosinStats.median);
                rt.addValue("Eosin_Skew", eosinStats.skewness);
                rt.addValue("Eosin_Kurt", eosinStats.kurtosis);
            } else {
                // Add placeholder values if deconvoluted images are not available
                addPlaceholderStainValues(rt, "Hema");
                addPlaceholderStainValues(rt, "Eosin");
            }
        }

        // Save the results table to the file with appropriate separator
        saveTableWithSeparator(rt, resultsFile);
        Debug.log("Results saved to: " + resultsFile.getAbsolutePath());
    }
    
    /**
     * Add placeholder values for stain measurements
     */
    private void addPlaceholderStainValues(ResultsTable rt, String prefix) {
        rt.addValue(prefix + "_Mean", 0);
        rt.addValue(prefix + "_StdDev", 0);
        rt.addValue(prefix + "_Mode", 0);
        rt.addValue(prefix + "_Min", 0);
        rt.addValue(prefix + "_Max", 0);
        rt.addValue(prefix + "_Median", 0);
        rt.addValue(prefix + "_Skew", 0);
        rt.addValue(prefix + "_Kurt", 0);
    }
    
    /**
     * Sort ROI indices based on type and numeric index
     */
    private List<Integer> sortRoiIndices(RoiManager rm, Roi[] rois) {
        List<Integer> roiIndices = new ArrayList<>();
        List<String> roiTypes = new ArrayList<>();
        List<Integer> numericIndices = new ArrayList<>();
        List<Boolean> isBorderROIs = new ArrayList<>();
        
        // First, collect all ROI indices and extract their types and numeric indices
        for (int i = 0; i < rois.length; i++) {
            String roiName = rm.getName(i);
            roiIndices.add(i);
            
            // Check if this is a border ROI
            boolean isBorderROI = roiName.contains("_Border");
            isBorderROIs.add(isBorderROI);
            
            // Remove the _Border suffix for parsing if present
            String nameForParsing = isBorderROI ?
                roiName.substring(0, roiName.indexOf("_Border")) :
                roiName;
            
            // Extract the ROI type and numeric index
            String roiType = "";
            int numericIndex = 0;
            
            if (nameForParsing.startsWith("Vessel_")) {
                roiType = "Vessel";
                try {
                    numericIndex = Integer.parseInt(nameForParsing.substring(7));
                } catch (NumberFormatException e) {
                    numericIndex = Integer.MAX_VALUE;
                }
            } else if (nameForParsing.startsWith("Border_")) {
                roiType = "Border";
                try {
                    numericIndex = Integer.parseInt(nameForParsing.substring(7));
                } catch (NumberFormatException e) {
                    numericIndex = Integer.MAX_VALUE;
                }
            } else if (nameForParsing.startsWith("Nucleus_")) {
                roiType = "Nucleus";
                try {
                    numericIndex = Integer.parseInt(nameForParsing.substring(8));
                } catch (NumberFormatException e) {
                    numericIndex = Integer.MAX_VALUE;
                }
            } else if (nameForParsing.startsWith("Cytoplasm_")) {
                roiType = "Cytoplasm";
                try {
                    numericIndex = Integer.parseInt(nameForParsing.substring(10));
                } catch (NumberFormatException e) {
                    numericIndex = Integer.MAX_VALUE;
                }
            } else if (nameForParsing.startsWith("Cell_")) {
                roiType = "Cell";
                try {
                    numericIndex = Integer.parseInt(nameForParsing.substring(5));
                } catch (NumberFormatException e) {
                    numericIndex = Integer.MAX_VALUE;
                }
            } else {
                roiType = "Other";
                numericIndex = Integer.MAX_VALUE;
            }
            
            roiTypes.add(roiType);
            numericIndices.add(numericIndex);
        }
        
        // Sort the ROI indices based on the desired order
        roiIndices.sort((a, b) -> {
            String typeA = roiTypes.get(a);
            String typeB = roiTypes.get(b);
            int indexA = numericIndices.get(a);
            int indexB = numericIndices.get(b);
            
            // Vessels come first, sorted by their index
            if (typeA.equals("Vessel") && typeB.equals("Vessel")) {
                return Integer.compare(indexA, indexB);
            } else if (typeA.equals("Vessel")) {
                return -1; // A is a Vessel, so it comes before B
            } else if (typeB.equals("Vessel")) {
                return 1;  // B is a Vessel, so it comes before A
            }
            
            // Border vessels come next, sorted by their index
            if (typeA.equals("Border") && typeB.equals("Border")) {
                return Integer.compare(indexA, indexB);
            } else if (typeA.equals("Border")) {
                return -1; // A is a Border, so it comes before B
            } else if (typeB.equals("Border")) {
                return 1;  // B is a Border, so it comes before A
            }
            
            // For other types, first compare by numeric index
            if (indexA != indexB) {
                return Integer.compare(indexA, indexB);
            }
            
            // If numeric indices are the same, order by type: Nucleus, Cytoplasm, Cell
            if (!typeA.equals(typeB)) {
                if (typeA.equals("Nucleus")) return -1;
                if (typeB.equals("Nucleus")) return 1;
                if (typeA.equals("Cytoplasm")) return -1;
                if (typeB.equals("Cytoplasm")) return 1;
                if (typeA.equals("Cell")) return -1;
                if (typeB.equals("Cell")) return 1;
            }
            
            // If all else is equal, maintain original order
            return Integer.compare(a, b);
        });
        
        return roiIndices;
    }
    
    /**
     * Get a convex hull ROI from an original ROI
     */
    private ShapeRoi getConvexHullRoi(Roi originalRoi) {
        try {
            java.awt.Polygon convexHullPoly = originalRoi.getConvexHull();
            float[] xpoints = new float[convexHullPoly.npoints];
            float[] ypoints = new float[convexHullPoly.npoints];
            
            for (int p = 0; p < convexHullPoly.npoints; p++) {
                xpoints[p] = convexHullPoly.xpoints[p];
                ypoints[p] = convexHullPoly.ypoints[p];
            }
            
            PolygonRoi polyRoi = new PolygonRoi(xpoints, ypoints, convexHullPoly.npoints, Roi.POLYGON);
            return new ShapeRoi(polyRoi);
        } catch (Exception e) {
            Debug.log("Error calculating convex hull: " + e.getMessage());
            // Return the original ROI as a ShapeRoi if convex hull fails
            return new ShapeRoi(originalRoi);
        }
    }
    
    /**
     * Save averages of measurements for different ROI groups to a CSV file
     */
    private void saveAverages(File averagesFile, ImagePlus imp) {
        // Check if deconvolution images exist
        boolean hasDeconvolutedImages = (hematoxylinImage != null && eosinImage != null);
        
        // Get the ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null || rm.getCount() == 0) {
            Debug.log("No ROIs available for calculating averages");
            return;
        }
        
        // Get all ROIs as an array
        Roi[] rois = rm.getRoisAsArray();
        
        // Create lists to store ROIs by type
        Map<String, List<Roi>> roiGroups = groupRoisByType(rois, rm);
        
        // Create a new ResultsTable for averages
        ResultsTable rt = new ResultsTable();
        
        // Calculate and add averages for each group
        for (Map.Entry<String, List<Roi>> entry : roiGroups.entrySet()) {
            String groupName = entry.getKey();
            List<Roi> groupRois = entry.getValue();
            
            if (!groupRois.isEmpty()) {
                addAverageForGroup(rt, groupName, groupRois, imp, hasDeconvolutedImages);
            }
        }
        
        // Save the results table to the file with appropriate separator
        saveTableWithSeparator(rt, averagesFile);
        Debug.log("Averages saved to: " + averagesFile.getAbsolutePath());
    }
    
    /**
     * Group ROIs by type (Central/Border and Nucleus/Cell/Cytoplasm/Vessel)
     */
    private Map<String, List<Roi>> groupRoisByType(Roi[] rois, RoiManager rm) {
        Map<String, List<Roi>> groups = new HashMap<>();
        
        // Initialize groups
        groups.put("Central Nuclei", new ArrayList<>());
        groups.put("Border Nuclei", new ArrayList<>());
        groups.put("All Nuclei", new ArrayList<>());
        groups.put("Central Cells", new ArrayList<>());
        groups.put("Border Cells", new ArrayList<>());
        groups.put("All Cells", new ArrayList<>());
        groups.put("Central Cytoplasms", new ArrayList<>());
        groups.put("Border Cytoplasms", new ArrayList<>());
        groups.put("All Cytoplasms", new ArrayList<>());
        groups.put("Central Vessels", new ArrayList<>());
        groups.put("Border Vessels", new ArrayList<>());
        groups.put("All Vessels", new ArrayList<>());
        
        // Group ROIs by type
        for (int i = 0; i < rois.length; i++) {
            Roi roi = rois[i];
            String roiName = rm.getName(i);
            boolean isBorder = roiName.contains("_Border");
            
            if (roiName.startsWith("Nucleus_")) {
                if (isBorder) {
                    groups.get("Border Nuclei").add(roi);
                } else {
                    groups.get("Central Nuclei").add(roi);
                }
                groups.get("All Nuclei").add(roi);
            } else if (roiName.startsWith("Cell_")) {
                if (isBorder) {
                    groups.get("Border Cells").add(roi);
                } else {
                    groups.get("Central Cells").add(roi);
                }
                groups.get("All Cells").add(roi);
            } else if (roiName.startsWith("Cytoplasm_")) {
                if (isBorder) {
                    groups.get("Border Cytoplasms").add(roi);
                } else {
                    groups.get("Central Cytoplasms").add(roi);
                }
                groups.get("All Cytoplasms").add(roi);
            } else if ((roiName.startsWith("Vessel_") && (!ignoreBorderVessels || !roiName.contains("_Border")))
                      || roiName.startsWith("Border_")) {
                if (isBorder) {
                    groups.get("Border Vessels").add(roi);
                } else {
                    groups.get("Central Vessels").add(roi);
                }
                groups.get("All Vessels").add(roi);
            }
        }
        
        return groups;
    }
    
    /**
     * Calculate and add averages for a group of ROIs to the results table
     */
    private void addAverageForGroup(ResultsTable rt, String groupName, List<Roi> rois, 
                                   ImagePlus imp, boolean hasDeconvolutedImages) {
        if (rois.isEmpty()) {
            return;
        }
        
        // Variables to store sums
        Map<String, Double> sums = new HashMap<>();
        
        // Initialize sums for all measurements
        initializeSumMap(sums);
        
        // Process each ROI in the group
        for (Roi roi : rois) {
            // Measure original image
            imp.setRoi(roi);
            imp.getProcessor().setRoi(roi);
            ImageStatistics stats = imp.getStatistics(ORIGINAL_MEASUREMENTS);
            
            // Calculate perimeter and other measurements
            double perimeter = roi.getLength();
            double[] feretValues = roi.getFeretValues();
            
            // Calculate convex hull for solidity
            ShapeRoi convexHullRoi = getConvexHullRoi(roi);
            
            imp.setRoi(roi);
            double originalArea = imp.getStatistics(Measurements.AREA).area;
            
            imp.setRoi(convexHullRoi);
            double convexHullArea = imp.getStatistics(Measurements.AREA).area;
            
            // Calculate solidity
            double solidity = convexHullArea > 0 ? originalArea / convexHullArea : 1.0;
            
            // Calculate circularity and other shape descriptors
            double circularity = (4 * Math.PI * stats.area) / (perimeter * perimeter);
            double aspectRatio = stats.major / stats.minor;
            double roundness = (4 * stats.area) / (Math.PI * stats.major * stats.major);
            
            // Add to sums
            addToSumMap(sums, stats, perimeter, feretValues, circularity, aspectRatio, roundness, solidity);
            
            // Hematoxylin and Eosin measurements if available
            if (hasDeconvolutedImages) {
                addStainMeasurementsToSums(sums, roi, hematoxylinImage, "Hema");
                addStainMeasurementsToSums(sums, roi, eosinImage, "Eosin");
            }
        }
        
        // Calculate averages
        int count = rois.size();
        Map<String, Double> averages = calculateAverages(sums, count);
        
        // Add row to results table
        rt.incrementCounter();
        rt.addValue("Group", groupName);
        rt.addValue("Count", count);
        
        // Add all calculated averages in the same order as in the individual CSV file
        // This ensures that the columns in the averages CSV file are in the same order as in the individual CSV file
        rt.addValue("Area", averages.get("Area"));
        rt.addValue("Perim.", averages.get("Perim."));
        rt.addValue("Major", averages.get("Major"));
        rt.addValue("Minor", averages.get("Minor"));
        rt.addValue("Angle", averages.get("Angle"));
        rt.addValue("Circ.", averages.get("Circ."));
        rt.addValue("Feret", averages.get("Feret"));
        rt.addValue("FeretAngle", averages.get("FeretAngle"));
        rt.addValue("MinFeret", averages.get("MinFeret"));
        rt.addValue("AR", averages.get("AR"));
        rt.addValue("Round", averages.get("Round"));
        rt.addValue("Solidity", averages.get("Solidity"));
        rt.addValue("Mean", averages.get("Mean"));
        rt.addValue("StdDev", averages.get("StdDev"));
        rt.addValue("Mode", averages.get("Mode"));
        rt.addValue("Min", averages.get("Min"));
        rt.addValue("Max", averages.get("Max"));
        rt.addValue("Median", averages.get("Median"));
        rt.addValue("Skew", averages.get("Skew"));
        rt.addValue("Kurt", averages.get("Kurt"));
        
        // Add Hematoxylin and Eosin averages if available
        if (hasDeconvolutedImages) {
            addStainAveragesToTable(rt, sums, count, "Hema");
            addStainAveragesToTable(rt, sums, count, "Eosin");
        } else {
            // Add placeholder values if deconvoluted images are not available
            addPlaceholderStainValues(rt, "Hema");
            addPlaceholderStainValues(rt, "Eosin");
        }
    }
    
    /**
     * Initialize the sum map with all measurement keys
     */
    private void initializeSumMap(Map<String, Double> sums) {
        sums.put("Area", 0.0);
        sums.put("Perim.", 0.0);
        sums.put("Major", 0.0);
        sums.put("Minor", 0.0);
        sums.put("Angle", 0.0);
        sums.put("Circ.", 0.0);
        sums.put("Feret", 0.0);
        sums.put("FeretAngle", 0.0);
        sums.put("MinFeret", 0.0);
        sums.put("AR", 0.0);
        sums.put("Round", 0.0);
        sums.put("Solidity", 0.0);
        sums.put("Mean", 0.0);
        sums.put("StdDev", 0.0);
        sums.put("Mode", 0.0);
        sums.put("Min", 0.0);
        sums.put("Max", 0.0);
        sums.put("Median", 0.0);
        sums.put("Skew", 0.0);
        sums.put("Kurt", 0.0);
    }
    
    /**
     * Add measurements to the sum map
     */
    private void addToSumMap(Map<String, Double> sums, ImageStatistics stats, double perimeter, 
                            double[] feretValues, double circularity, double aspectRatio, 
                            double roundness, double solidity) {
        sums.put("Area", sums.get("Area") + stats.area);
        sums.put("Perim.", sums.get("Perim.") + perimeter);
        sums.put("Major", sums.get("Major") + stats.major);
        sums.put("Minor", sums.get("Minor") + stats.minor);
        sums.put("Angle", sums.get("Angle") + stats.angle);
        sums.put("Circ.", sums.get("Circ.") + circularity);
        sums.put("Feret", sums.get("Feret") + feretValues[0]);
        sums.put("FeretAngle", sums.get("FeretAngle") + feretValues[1]);
        sums.put("MinFeret", sums.get("MinFeret") + feretValues[2]);
        sums.put("AR", sums.get("AR") + aspectRatio);
        sums.put("Round", sums.get("Round") + roundness);
        sums.put("Solidity", sums.get("Solidity") + solidity);
        sums.put("Mean", sums.get("Mean") + stats.mean);
        sums.put("StdDev", sums.get("StdDev") + stats.stdDev);
        sums.put("Mode", sums.get("Mode") + stats.mode);
        sums.put("Min", sums.get("Min") + stats.min);
        sums.put("Max", sums.get("Max") + stats.max);
        sums.put("Median", sums.get("Median") + stats.median);
        sums.put("Skew", sums.get("Skew") + stats.skewness);
        sums.put("Kurt", sums.get("Kurt") + stats.kurtosis);
    }
    
    /**
     * Add stain measurements to the sum map
     */
    private void addStainMeasurementsToSums(Map<String, Double> sums, Roi roi, ImagePlus stainImage, String prefix) {
        stainImage.setRoi(roi);
        stainImage.getProcessor().setRoi(roi);
        ImageStatistics stainStats = stainImage.getStatistics(STAIN_MEASUREMENTS);
        
        // Initialize keys if they don't exist
        if (!sums.containsKey(prefix + "_Mean")) {
            sums.put(prefix + "_Mean", 0.0);
            sums.put(prefix + "_StdDev", 0.0);
            sums.put(prefix + "_Mode", 0.0);
            sums.put(prefix + "_Min", 0.0);
            sums.put(prefix + "_Max", 0.0);
            sums.put(prefix + "_Median", 0.0);
            sums.put(prefix + "_Skew", 0.0);
            sums.put(prefix + "_Kurt", 0.0);
        }
        
        // Add to sums
        sums.put(prefix + "_Mean", sums.get(prefix + "_Mean") + stainStats.mean);
        sums.put(prefix + "_StdDev", sums.get(prefix + "_StdDev") + stainStats.stdDev);
        sums.put(prefix + "_Mode", sums.get(prefix + "_Mode") + stainStats.mode);
        sums.put(prefix + "_Min", sums.get(prefix + "_Min") + stainStats.min);
        sums.put(prefix + "_Max", sums.get(prefix + "_Max") + stainStats.max);
        sums.put(prefix + "_Median", sums.get(prefix + "_Median") + stainStats.median);
        sums.put(prefix + "_Skew", sums.get(prefix + "_Skew") + stainStats.skewness);
        sums.put(prefix + "_Kurt", sums.get(prefix + "_Kurt") + stainStats.kurtosis);
    }
    
    /**
     * Calculate averages from sums
     */
    private Map<String, Double> calculateAverages(Map<String, Double> sums, int count) {
        Map<String, Double> averages = new HashMap<>();
        
        for (String key : sums.keySet()) {
            averages.put(key, count > 0 ? sums.get(key) / count : 0);
        }
        
        return averages;
    }
    
    /**
     * Add stain averages to the results table
     */
    private void addStainAveragesToTable(ResultsTable rt, Map<String, Double> sums, int count, String prefix) {
        if (sums.containsKey(prefix + "_Mean")) {
            rt.addValue(prefix + "_Mean", count > 0 ? sums.get(prefix + "_Mean") / count : 0);
            rt.addValue(prefix + "_StdDev", count > 0 ? sums.get(prefix + "_StdDev") / count : 0);
            rt.addValue(prefix + "_Mode", count > 0 ? sums.get(prefix + "_Mode") / count : 0);
            rt.addValue(prefix + "_Min", count > 0 ? sums.get(prefix + "_Min") / count : 0);
            rt.addValue(prefix + "_Max", count > 0 ? sums.get(prefix + "_Max") / count : 0);
            rt.addValue(prefix + "_Median", count > 0 ? sums.get(prefix + "_Median") / count : 0);
            rt.addValue(prefix + "_Skew", count > 0 ? sums.get(prefix + "_Skew") / count : 0);
            rt.addValue(prefix + "_Kurt", count > 0 ? sums.get(prefix + "_Kurt") / count : 0);
        }
    }
    
    /**
     * Save all ROIs to a zip file
     */
    private void saveRois(File file) {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null || rm.getCount() == 0) {
            Debug.log("No ROIs to save");
            return;
        }
        
        rm.runCommand("Save", file.getAbsolutePath());
        Debug.log("ROIs saved to: " + file.getAbsolutePath());
    }
    
    /**
     * Save a ResultsTable to a file with the appropriate separator
     */
    private void saveTableWithSeparator(ResultsTable rt, File file) {
        try {
            if (useSemicolon) {
                // Save with semicolon separator
                saveTableWithSemicolon(rt, file);
            } else {
                // Default save with comma separator
                rt.save(file.getAbsolutePath());
            }
        } catch (Exception e) {
            Debug.log("Error saving table: " + e.getMessage());
        }
    }
    
    /**
     * Save a ResultsTable to a file using semicolon as separator
     */
    private void saveTableWithSemicolon(ResultsTable rt, File file) {
        try {
            // Get the column headings and row count
            int columnCount = rt.getLastColumn() + 1;
            int rowCount = rt.getCounter();
            String[] headings = rt.getHeadings();
            
            // Create a StringBuilder to build the CSV content
            StringBuilder sb = new StringBuilder();
            
            // Add the column headings
            for (int i = 0; i < columnCount; i++) {
                if (i > 0) {
                    sb.append(";");
                }
                sb.append(headings[i]);
            }
            sb.append("\n");
            
            // Add the data rows
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < columnCount; col++) {
                    if (col > 0) {
                        sb.append(";");
                    }
                    
                    // Get the value as string, handling both numeric and string values
                    String value;
                    if (rt.columnExists(col)) {
                        double doubleValue = rt.getValueAsDouble(col, row);
                        if (Double.isNaN(doubleValue)) {
                            value = rt.getStringValue(col, row);
                        } else {
                            // Format with US locale to ensure point as decimal separator
                            value = String.format(DECIMAL_LOCALE, "%.6f", doubleValue);
                            // Remove trailing zeros and decimal point if it's a whole number
                            if (value.contains(".")) {
                                value = value.replaceAll("0+$", "").replaceAll("\\.$", "");
                                
                                // Replace decimal point with comma if requested
                                if (useCommaForDecimals) {
                                    value = value.replace(".", ",");
                                }
                            }
                        }
                    } else {
                        value = rt.getStringValue(col, row);
                    }
                    
                    // Add quotes if the value contains semicolons or newlines
                    if (value.contains(";") || value.contains("\n") || value.contains("\"")) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }
                    
                    sb.append(value);
                }
                sb.append("\n");
            }
            
            // Write the content to the file
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(sb.toString());
            }
            
        } catch (Exception e) {
            Debug.log("Error saving table with semicolon separator: " + e.getMessage());
            // Fallback to default save method
            rt.save(file.getAbsolutePath());
        }
    }
    
    /**
     * Helper class to store vessel data for distance calculations
     */
    private static class VesselData {
        String name;
        double x;
        double y;
        double radius;
        int gridX;
        int gridY;
        
        VesselData(String name, double x, double y, double radius) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.gridX = (int)(x / GRID_CELL_SIZE);
            this.gridY = (int)(y / GRID_CELL_SIZE);
        }
    }
    
    /**
     * Helper class to store nucleus position data for spatial indexing
     */
    private static class NucleusData {
        String name;
        int index;
        int nucleusNumber;
        double x;
        double y;
        int gridX;
        int gridY;
        double major;
        double minor;
        double angle;
        
        // Cache for measurements
        double vesselDistance = Double.NaN;
        String closestVessel = "N/A";
        int neighborCount = 0;
        double closestNeighborDistance = Double.NaN;
        String closestNeighbor = "N/A";
        
        NucleusData(String name, int index, int nucleusNumber, double x, double y,
                   double major, double minor, double angle) {
            this.name = name;
            this.index = index;
            this.nucleusNumber = nucleusNumber;
            this.x = x;
            this.y = y;
            this.gridX = (int)(x / GRID_CELL_SIZE);
            this.gridY = (int)(y / GRID_CELL_SIZE);
            this.major = major;
            this.minor = minor;
            this.angle = angle;
        }
        
        /**
         * Calculate the distance from this nucleus border to another nucleus border,
         * assuming both are ellipses
         */
        public double calculateBorderDistance(NucleusData other) {
            // First calculate center-to-center distance
            double dx = other.x - this.x;
            double dy = other.y - this.y;
            double centerDistance = Math.sqrt(dx*dx + dy*dy);
            
            // If centers are at the same point, return 0
            if (centerDistance < 0.001) {
                return 0;
            }
            
            // Calculate the approximate radius in the direction of the other nucleus
            // for both ellipses
            double angleToOther = Math.atan2(dy, dx);
            
            // Convert to degrees for consistency with ImageJ angle
            double angleToOtherDegrees = Math.toDegrees(angleToOther);
            
            // Calculate the radius of this ellipse in the direction of the other nucleus
            double thisRadius = calculateEllipseRadius(
                this.major / 2, this.minor / 2,
                angleToOtherDegrees - this.angle
            );
            
            // Calculate the radius of the other ellipse in the direction of this nucleus
            // (opposite direction)
            double otherRadius = calculateEllipseRadius(
                other.major / 2, other.minor / 2,
                (angleToOtherDegrees + 180) - other.angle
            );
            
            // Calculate the distance between borders
            double borderDistance = centerDistance - thisRadius - otherRadius;
            
            // Return 0 if borders overlap
            return Math.max(0, borderDistance);
            
            // Note: Scale is applied when this value is used, not here,
            // to maintain consistency with the spatial grid calculations
        }
        
        /**
         * Calculate the radius of an ellipse at a given angle
         */
        private double calculateEllipseRadius(double a, double b, double angleDegrees) {
            // Convert angle to radians
            double angleRadians = Math.toRadians(angleDegrees);
            
            // Formula for radius at angle t: r(t) = (a*b) / sqrt((b*cos(t))^2 + (a*sin(t))^2)
            double cosAngle = Math.cos(angleRadians);
            double sinAngle = Math.sin(angleRadians);
            double denominator = Math.sqrt((b*cosAngle)*(b*cosAngle) + (a*sinAngle)*(a*sinAngle));
            
            if (denominator < 0.001) {
                return Math.max(a, b); // Avoid division by zero
            }
            
            return (a*b) / denominator;
        }
    }
    
    /**
     * Spatial grid for fast nearest neighbor searches
     */
    private static class SpatialGrid<T> {
        private Map<String, List<T>> grid = new HashMap<>();
        private java.util.function.Function<T, Integer> getGridX;
        private java.util.function.Function<T, Integer> getGridY;
        
        public SpatialGrid(java.util.function.Function<T, Integer> getGridX,
                          java.util.function.Function<T, Integer> getGridY) {
            this.getGridX = getGridX;
            this.getGridY = getGridY;
        }
        
        public void add(T item) {
            int gridX = getGridX.apply(item);
            int gridY = getGridY.apply(item);
            String key = gridX + "," + gridY;
            
            if (!grid.containsKey(key)) {
                grid.put(key, new ArrayList<>());
            }
            grid.get(key).add(item);
        }
        
        public List<T> getNearby(T item, int radius) {
            List<T> result = new ArrayList<>();
            int gridX = getGridX.apply(item);
            int gridY = getGridY.apply(item);
            
            // Search in surrounding grid cells
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    String key = (gridX + dx) + "," + (gridY + dy);
                    if (grid.containsKey(key)) {
                        result.addAll(grid.get(key));
                    }
                }
            }
            
            return result;
        }
    }
    
    /**
     * ShapeRoi class for ROI operations
     */
    private static class ShapeRoi extends Roi {
        public ShapeRoi(Roi roi) {
            super(roi.getBounds());
            setName(roi.getName());
            setStrokeColor(roi.getStrokeColor());
            setFillColor(roi.getFillColor());
        }
    }
}
