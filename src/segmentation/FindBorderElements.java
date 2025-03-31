package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.awt.Color;
import java.awt.Rectangle;
import com.sebastianmicu.scheli.Debug;

public class FindBorderElements {
    private ImagePlus originalImage;
    private List<Roi> nucleiROIs;
    private List<Roi> cytoplasmROIs;
    private List<Roi> cellROIs;
    private List<Roi> vesselROIs;
    
    private List<Roi> borderNucleiROIs;
    private List<Roi> borderCytoplasmROIs;
    private List<Roi> borderCellROIs;
    
    private List<Roi> centralNucleiROIs;
    private List<Roi> centralCytoplasmROIs;
    private List<Roi> centralCellROIs;
    
    // Constructor
    public FindBorderElements(ImagePlus originalImage, List<Roi> nucleiROIs, 
                             List<Roi> cytoplasmROIs, List<Roi> cellROIs) {
        this.originalImage = originalImage;
        this.nucleiROIs = nucleiROIs;
        this.cytoplasmROIs = cytoplasmROIs;
        this.cellROIs = cellROIs;
        this.vesselROIs = new ArrayList<>();
        
        this.borderNucleiROIs = new ArrayList<>();
        this.borderCytoplasmROIs = new ArrayList<>();
        this.borderCellROIs = new ArrayList<>();
        
        this.centralNucleiROIs = new ArrayList<>();
        this.centralCytoplasmROIs = new ArrayList<>();
        this.centralCellROIs = new ArrayList<>();
    }
    
    // Additional constructor that accepts vessel ROIs
    public FindBorderElements(ImagePlus originalImage, List<Roi> nucleiROIs, 
                             List<Roi> cytoplasmROIs, List<Roi> cellROIs,
                             List<Roi> vesselROIs) {
        this(originalImage, nucleiROIs, cytoplasmROIs, cellROIs);
        if (vesselROIs != null) {
            this.vesselROIs = vesselROIs;
        }
    }
    
    // Set vessel ROIs if they weren't provided in the constructor
    public void setVesselROIs(List<Roi> vesselROIs) {
        if (vesselROIs != null) {
            this.vesselROIs = vesselROIs;
        }
    }
    
    // Find elements that touch or are near the image border
    public void findBorderElements() {
        // Debug.log("Finding ROIs that touch or are near the border...");
        
        if (originalImage == null) {
            // Debug.log("No image available for border detection");
            return;
        }
        
        // Get image dimensions
        int imageWidth = originalImage.getWidth();
        int imageHeight = originalImage.getHeight();
        
        // Create a map to store cell numbers and their border status
        HashMap<Integer, Boolean> cellBorderStatus = new HashMap<>();
        
        // First pass: check Vessel ROIs and Cell ROIs for border proximity
        // Process Vessel ROIs
        for (int i = 0; i < vesselROIs.size(); i++) {
            Roi roi = vesselROIs.get(i);
            String roiName = roi.getName();
            
            // Check if ROI touches or is near the border
            boolean touchesBorder = isTouchingOrNearBorder(roi, imageWidth, imageHeight, 1);
            
            if (touchesBorder) {
                // For Vessel ROIs that touch the border
                roi.setName(roiName + "_Border");
                roi.setStrokeColor(parseColor(ConfigVariables.getColorBorderVessels()));
                roi.setFillColor(parseColor(ConfigVariables.getColorBorderVessels()));
                // Debug.log("Set border vessel color for: " + roiName);
            } else {
                // For central Vessel ROIs (not touching the border)
                roi.setStrokeColor(parseColor(ConfigVariables.getColorCentralVessels()));
                roi.setFillColor(parseColor(ConfigVariables.getColorCentralVessels()));
                // Debug.log("Set central vessel color for: " + roiName);
            }
        }
        
        // Process Cell ROIs
        for (int i = 0; i < cellROIs.size(); i++) {
            Roi roi = cellROIs.get(i);
            String roiName = roi.getName();
            
            // Check if ROI touches or is near the border
            boolean touchesBorder = isTouchingOrNearBorder(roi, imageWidth, imageHeight, 1);
            
            if (touchesBorder) {
                try {
                    // Extract the cell number from the name (e.g., "Cell_5" -> 5)
                    int cellNumber = Integer.parseInt(roiName.split("_")[1]);
                    cellBorderStatus.put(cellNumber, true);
                    
                    // Rename the Cell ROI and change its color
                    roi.setName(roiName + "_Border");
                    roi.setStrokeColor(parseColor(ConfigVariables.getColorBorderCells()));
                    roi.setFillColor(parseColor(ConfigVariables.getColorBorderCells()));
                    
                    // Add to border cells list
                    borderCellROIs.add(roi);
                } catch (Exception e) {
                    // Debug.log("Could not parse cell number from: " + roiName);
                }
            } else {
                // Add to central cells list
                centralCellROIs.add(roi);
                roi.setStrokeColor(parseColor(ConfigVariables.getColorCentralCells()));
                roi.setFillColor(parseColor(ConfigVariables.getColorCentralCells()));
            }
        }
        
        // Second pass: update Nucleus and Cytoplasm ROIs based on cell border status
        if (!cellBorderStatus.isEmpty()) {
            // Process Nucleus ROIs
            for (int i = 0; i < nucleiROIs.size(); i++) {
                Roi roi = nucleiROIs.get(i);
                String roiName = roi.getName();
                
                try {
                    // Extract the number from the name
                    int number = Integer.parseInt(roiName.split("_")[1]);
                    
                    // Check if the corresponding cell touches the border
                    if (cellBorderStatus.containsKey(number) && cellBorderStatus.get(number)) {
                        // Rename and change color
                        roi.setName(roiName + "_Border");
                        roi.setStrokeColor(parseColor(ConfigVariables.getColorBorderNuclei()));
                        roi.setFillColor(parseColor(ConfigVariables.getColorBorderNuclei()));
                        
                        // Add to border nuclei list
                        borderNucleiROIs.add(roi);
                    } else {
                        // Add to central nuclei list
                        centralNucleiROIs.add(roi);
                        roi.setStrokeColor(parseColor(ConfigVariables.getColorCentralNuclei()));
                        roi.setFillColor(parseColor(ConfigVariables.getColorCentralNuclei()));
                    }
                } catch (Exception e) {
                    // Debug.log("Could not parse number from: " + roiName);
                }
            }
            
            // Process Cytoplasm ROIs
            for (int i = 0; i < cytoplasmROIs.size(); i++) {
                Roi roi = cytoplasmROIs.get(i);
                String roiName = roi.getName();
                
                try {
                    // Extract the number from the name
                    int number = Integer.parseInt(roiName.split("_")[1]);
                    
                    // Check if the corresponding cell touches the border
                    if (cellBorderStatus.containsKey(number) && cellBorderStatus.get(number)) {
                        // Rename and change color
                        roi.setName(roiName + "_Border");
                        roi.setStrokeColor(parseColor(ConfigVariables.getColorBorderCytoplasm()));
                        roi.setFillColor(parseColor(ConfigVariables.getColorBorderCytoplasm()));
                        
                        // Add to border cytoplasm list
                        borderCytoplasmROIs.add(roi);
                    } else {
                        // Add to central cytoplasm list
                        centralCytoplasmROIs.add(roi);
                        roi.setStrokeColor(parseColor(ConfigVariables.getColorCentralCytoplasm()));
                        roi.setFillColor(parseColor(ConfigVariables.getColorCentralCytoplasm()));
                    }
                } catch (Exception e) {
                    // Debug.log("Could not parse number from: " + roiName);
                }
            }
        }
        
        // Debug.log("Completed border element detection");
        // Debug.log("Found " + borderCellROIs.size() + " border cells and " + centralCellROIs.size() + " central cells");
    }
    
    // Check if a ROI is touching or near the border of the image
    private boolean isTouchingOrNearBorder(Roi roi, int imageWidth, int imageHeight, int borderDistance) {
        Rectangle bounds = roi.getBounds();
        
        // Check if the ROI is touching or near any of the image borders
        return bounds.x <= borderDistance || // Left border
               bounds.y <= borderDistance || // Top border
               bounds.x + bounds.width >= imageWidth - borderDistance || // Right border
               bounds.y + bounds.height >= imageHeight - borderDistance; // Bottom border
    }
    
    // Parse a color string from the config into a Color object
    private Color parseColor(String colorStr) {
        if (colorStr == null) return Color.BLACK;
        
        // Handle named colors
        switch (colorStr.toLowerCase()) {
            case "red": return Color.RED;
            case "blue": return Color.BLUE;
            case "green": return Color.GREEN;
            case "yellow": return Color.YELLOW;
            case "magenta": return Color.MAGENTA;
            case "cyan": return Color.CYAN;
            case "white": return Color.WHITE;
            case "black": return Color.BLACK;
            case "gray": return Color.GRAY;
            case "pink": return new Color(255, 175, 175);
            default:
                // Try to parse as hex color
                if (colorStr.startsWith("#")) {
                    try {
                        return Color.decode(colorStr);
                    } catch (NumberFormatException e) {
                        // Debug.log("Invalid color format: " + colorStr + ". Using black.");
                        return Color.BLACK;
                    }
                }
                return Color.BLACK;
        }
    }
    
    // Convert a Color object to hex string format for ROI Manager
    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
    // Get border nuclei ROIs
    public List<Roi> getBorderNucleiROIs() {
        return borderNucleiROIs;
    }
    
    // Get border cytoplasm ROIs
    public List<Roi> getBorderCytoplasmROIs() {
        return borderCytoplasmROIs;
    }
    
    // Get border cell ROIs
    public List<Roi> getBorderCellROIs() {
        return borderCellROIs;
    }
    
    // Get central nuclei ROIs
    public List<Roi> getCentralNucleiROIs() {
        return centralNucleiROIs;
    }
    
    // Get central cytoplasm ROIs
    public List<Roi> getCentralCytoplasmROIs() {
        return centralCytoplasmROIs;
    }
    
    // Get central cell ROIs
    public List<Roi> getCentralCellROIs() {
        return centralCellROIs;
    }
    
    // Get all nuclei ROIs (both border and central)
    public List<Roi> getAllNucleiROIs() {
        List<Roi> allROIs = new ArrayList<>(borderNucleiROIs);
        allROIs.addAll(centralNucleiROIs);
        return allROIs;
    }
    
    // Get all cytoplasm ROIs (both border and central)
    public List<Roi> getAllCytoplasmROIs() {
        List<Roi> allROIs = new ArrayList<>(borderCytoplasmROIs);
        allROIs.addAll(centralCytoplasmROIs);
        return allROIs;
    }
    
    // Get all cell ROIs (both border and central)
    public List<Roi> getAllCellROIs() {
        List<Roi> allROIs = new ArrayList<>(borderCellROIs);
        allROIs.addAll(centralCellROIs);
        return allROIs;
    }
}