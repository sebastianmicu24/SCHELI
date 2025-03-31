package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.sebastianmicu.scheli.Debug;

public class NucleusSegmentation {
    private ImagePlus originalImage;
    private List<Roi> nucleiROIs;
    
    public NucleusSegmentation(ImagePlus originalImage) {
        this.originalImage = originalImage;
        this.nucleiROIs = new ArrayList<>();
    }
    
    public void segmentNuclei() {
        Debug.log("Starting nuclei segmentation with StarDist...");
        
        // Get configuration values
        float percentileBottom = ConfigVariables.getPercentileLow();
        float percentileTop = ConfigVariables.getPercentileHigh();
        float probThresh = ConfigVariables.getThresholdNuclei() / 1000.0f; // Convert to 0-1 range
        int nTiles = ConfigVariables.getPercentileTiles();
        float minSize = ConfigVariables.getSizeMinNucleus();
        float maxSize = ConfigVariables.getSizeMaxNucleus();
        
        // Clear any existing ROIs
        nucleiROIs.clear();
        
        // Prepare ROI Manager
        RoiManager rm = prepareRoiManager();
        
        // Check if original image is valid
        if (originalImage == null) {
            Debug.log("ERROR: Original image is null!");
            return;
        }
        
        // Create a duplicate to work with
        ImagePlus workingImage = originalImage.duplicate();
        workingImage.setTitle(originalImage.getTitle());
        
        try {
            // Show the image first
            workingImage.show();
            
            // Then try to hide it by moving it off-screen if possible
            if (workingImage.getWindow() != null) {
                workingImage.getWindow().setLocation(-1000, -1000);
            }
            
            // Build StarDist command
            String command = buildStarDistCommand(percentileBottom, percentileTop, probThresh, nTiles);
            
            // Execute StarDist
            Debug.log("Executing StarDist on image: " + workingImage.getTitle());
            IJ.run(workingImage, "Command From Macro", command);
            
            // Get ROIs from ROI Manager
            collectRoisFromManager();
            
            // Filter ROIs based on size
            filterROIsBySize(minSize, maxSize);
            
            Debug.log("Segmented " + nucleiROIs.size() + " nuclei");
            
        } catch (Exception e) {
            Debug.log("ERROR executing StarDist: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close the working image to clean up
            if (workingImage != null) {
                workingImage.changes = false; // Prevent "save changes" dialog
                workingImage.close();
            }
        }
    }
    
    private RoiManager prepareRoiManager() {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager(false); // Hidden ROI Manager
        }
        rm.reset();
        return rm;
    }
    
    private String buildStarDistCommand(float percentileBottom, float percentileTop, float probThresh, int nTiles) {
        return "command=[de.csbdresden.stardist.StarDist2D], " +
               "args=['input':'" + originalImage.getTitle() + "', " +
               "'modelChoice':'Versatile (H&E nuclei)', " +
               "'normalizeInput':'true', " +
               "'percentileBottom':'" + percentileBottom + "', " +
               "'percentileTop':'" + percentileTop + "', " +
               "'probThresh':'" + probThresh + "', " +
               "'nmsThresh':'0.0', " +
               "'outputType':'ROI Manager', " +
               "'nTiles':'" + nTiles + "', " +
               "'excludeBoundary':'2', " +
               "'roiPosition':'Automatic', " +
               "'verbose':'false', " +
               "'showCsbdeepProgress':'false', " +
               "'showProbAndDist':'false'], " +
               "process=[false]";
    }
    
    private void collectRoisFromManager() {
        RoiManager rm = RoiManager.getInstance();
        if (rm != null) {
            Roi[] rois = rm.getRoisAsArray();
            for (int i = 0; i < rois.length; i++) {
                Roi roi = rois[i];
                // Rename ROIs as Nucleus_1, Nucleus_2, etc.
                String newName = "Nucleus_" + (i + 1);
                roi.setName(newName);
                nucleiROIs.add(roi);
            }
            Debug.log("Renamed " + rois.length + " nuclei ROIs");
        } else {
            Debug.log("ERROR: ROI Manager is null after StarDist execution");
        }
    }
    
    private void filterROIsBySize(float minSize, float maxSize) {
        int beforeCount = nucleiROIs.size();
        
        nucleiROIs = nucleiROIs.stream()
            .filter(roi -> {
                double area = roi.getStatistics().area;
                return area >= minSize && area <= maxSize;
            })
            .collect(Collectors.toList());
    }
    
    public List<Roi> getNucleiROIs() {
        return nucleiROIs;
    }
    
    public void addToRoiManager() {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager(false); // Hidden ROI Manager
        }
        
        rm.reset();
        nucleiROIs.forEach(rm::addRoi);
    }
}
