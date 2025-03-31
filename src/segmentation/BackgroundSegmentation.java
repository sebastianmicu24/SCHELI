package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.filter.ParticleAnalyzer;
import ij.measure.ResultsTable;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.Binary;
import ij.plugin.filter.GaussianBlur;
import com.sebastianmicu.scheli.Debug;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BackgroundSegmentation {
    private ImagePlus originalImage;
    private ImagePlus backgroundMask;
    private List<Roi> vesselROIs;
    private String outputPath;
    private String baseName;
    
    // Constructor
    public BackgroundSegmentation(ImagePlus originalImage) {
        this.originalImage = originalImage;
        this.vesselROIs = new ArrayList<>();
        this.outputPath = ConfigVariables.getPathOutput();
        
        // Create base name from original image
        String title = originalImage.getTitle();
        this.baseName = title.contains(".") ? title.substring(0, title.lastIndexOf('.')) : title;
    }
    
    // Segment the background
    public void segmentBackground() {
        // Get the background threshold from config and convert from percentage (0-100) to 8-bit value (0-255)
        int thresholdPercentage = ConfigVariables.getThresholdBackground();
        int threshold = (int)(thresholdPercentage * 2.55); // Convert from 0-100% to 0-255
        
        Debug.log("Segmenting background with threshold: " + thresholdPercentage + "% (" + threshold + " in 8-bit)");
        
        // Create a duplicate of the original image for processing
        ImagePlus duplicate = originalImage.duplicate();
        
        // Convert to 8-bit grayscale if needed
        if (duplicate.getType() != ImagePlus.GRAY8) {
            IJ.run(duplicate, "8-bit", "");
        }
        
        // Apply Gaussian blur to reduce noise
        GaussianBlur gaussianBlur = new GaussianBlur();
        double sigma = 2.0; // Adjust sigma value as needed (higher = more blur)
        gaussianBlur.blurGaussian(duplicate.getProcessor(), sigma);
        Debug.log("Applied Gaussian blur with sigma: " + sigma);
        
        // Apply thresholding
        ImageProcessor ip = duplicate.getProcessor();
        ip.threshold(threshold);
        
     
        
        Debug.log("Thresholded and inverted image for vessel detection");
        
        // Create the background mask
        backgroundMask = new ImagePlus("Background Mask", ip);
        
        // Perform particle analysis to find vessels
        findVessels(duplicate);
        
        // Clean up
        duplicate.changes = false;
        duplicate.close();
    }
    
    // Process the vessel binary mask to make it smoother and fill holes
    private void vesselProcessing(ImagePlus image) {
        Debug.log("Processing vessel binary mask...");
        ImageProcessor ip = image.getProcessor();
        
        // Fill holes
        Binary binary = new Binary();
        binary.setup("fill", image);
        binary.run(ip);
        

        Debug.log("Vessel binary mask processed: fill holes");
    }
    
    // Find vessels using particle analysis
    private void findVessels(ImagePlus image) {
        // Get size constraints from config
        float minSize = ConfigVariables.getSizeMinVessel();
        float maxSize = ConfigVariables.getSizeMaxVessel();
        
        // Process the binary mask before analysis
        vesselProcessing(image);
        
        // Prepare ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager(false); // Hidden ROI Manager
        }
        rm.reset();
        
        // Set up particle analyzer
        int options = ParticleAnalyzer.ADD_TO_MANAGER |
                      ParticleAnalyzer.SHOW_NONE;
        
        int measurements = 0; // No measurements needed for now
        
        ResultsTable rt = new ResultsTable();
        ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements, rt, minSize, maxSize);
        
        try {
            // Show the image first (required for particle analysis)
            image.show();
            
            // Then try to hide it by moving it off-screen if possible
            if (image.getWindow() != null) {
                image.getWindow().setLocation(-1000, -1000);
            }
            
            Debug.log("Running particle analysis to find vessels...");
            
            // Run particle analysis
            pa.analyze(image);
            
            // Collect and rename ROIs
            vesselROIs.clear();
            Roi[] rois = rm.getRoisAsArray();
            
            for (int i = 0; i < rois.length; i++) {
                Roi roi = rois[i];
                // Rename ROIs as Vessel_1, Vessel_2, etc.
                String newName = "Vessel_" + (i + 1);
                roi.setName(newName);
                vesselROIs.add(roi);
            }
            
            Debug.log("Found " + vesselROIs.size() + " vessels");
            
        } catch (Exception e) {
            Debug.log("ERROR in vessel segmentation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close the image to clean up
            if (image != null && image.getWindow() != null) {
                image.changes = false; // Prevent "save changes" dialog
                image.close();
            }
        }
    }
    
    // Get the background mask
    public ImagePlus getBackgroundMask() {
        return backgroundMask;
    }
    
    // Get a ROI representing the background
    public Roi getBackgroundROI() {
        if (backgroundMask == null) {
            return null;
        }
        
        // Convert the mask to a selection
        ThresholdToSelection tts = new ThresholdToSelection();
        return tts.convert(backgroundMask.getProcessor());
    }
    
    // Get the vessel ROIs
    public List<Roi> getVesselROIs() {
        return vesselROIs;
    }
    
    // Add vessel ROIs to ROI Manager
    public void addVesselsToRoiManager() {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager(false); // Hidden ROI Manager
        }
        
        rm.reset();
        for (Roi roi : vesselROIs) {
            rm.addRoi(roi);
        }
    }

}
