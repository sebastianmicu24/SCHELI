package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import com.sebastianmicu.scheli.Debug;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Overlay;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.plugin.ImageCalculator;
import ij.plugin.frame.RoiManager;
import ij.io.FileSaver;
import java.util.ArrayList;
import java.util.List;
import java.awt.Rectangle;
import java.awt.Color;
import java.io.File;

public class CytoplasmSegmentation {
    private ImagePlus originalImage;
    private List<Roi> nucleiROIs;
    private List<Roi> vesselROIs;
    private List<Roi> cytoplasmROIs;
    private List<Roi> cellROIs;
    private ImagePlus backgroundMask;
    private String outputPath;
    private String baseName;
    
    // Constructor
    public CytoplasmSegmentation(ImagePlus originalImage, List<Roi> nucleiROIs) {
        this.originalImage = originalImage;
        this.nucleiROIs = nucleiROIs;
        this.vesselROIs = new ArrayList<>();
        this.cytoplasmROIs = new ArrayList<>();
        this.cellROIs = new ArrayList<>();
        this.outputPath = ConfigVariables.getPathOutput();
        
        // Create base name from original image
        String title = originalImage.getTitle();
        this.baseName = title.contains(".") ? title.substring(0, title.lastIndexOf('.')) : title;
        
        // Ensure output directory exists
        ensureOutputDirectory();
    }
    
    // Set vessel ROIs from BackgroundSegmentation
    public void setVesselROIs(List<Roi> vesselROIs) {
        this.vesselROIs = vesselROIs;
        // Create background mask from vessel ROIs
        this.backgroundMask = createBackgroundMaskFromVessels();
    }
    
    // Create a background mask from vessel ROIs
    private ImagePlus createBackgroundMaskFromVessels() {
        if (vesselROIs == null || vesselROIs.isEmpty()) {
            Debug.log("No vessel ROIs provided. Cannot create background mask.");
            return null;
        }
        
        // Get image dimensions
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        // Create a new binary processor
        ByteProcessor bp = new ByteProcessor(width, height);
        bp.setValue(0); // Set background to white
        bp.fill();
        
        // Draw vessels as white
        bp.setValue(255);
        
        // Add all vessel ROIs to the mask
        for (Roi roi : vesselROIs) {
            bp.fill(roi);
        }
        
        // Create and return the mask image
        ImagePlus mask = new ImagePlus("Vessel Mask", bp);
        return mask;
    }
    
    // Ensure output directory exists
    private void ensureOutputDirectory() {
        if (outputPath != null && !outputPath.isEmpty()) {
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
        } else {
            Debug.log("Warning: Output path is not set. Debug images will not be saved.");
        }
    }
    
    // Segment cytoplasm using Voronoi
    public void segmentCytoplasm() {
        Debug.log("Creating cytoplasm ROIs using Voronoi tesselation...");
        
        if (nucleiROIs == null || nucleiROIs.isEmpty()) {
            Debug.log("No nuclei ROIs found. Cannot create cytoplasm ROIs.");
            return;
        }
        
        // Clear any existing ROIs
        cytoplasmROIs.clear();
        cellROIs.clear();
        
        try {
            // Get image dimensions
            int imageWidth = originalImage.getWidth();
            int imageHeight = originalImage.getHeight();
            
            // Create a binary mask of nuclei
            ImagePlus nucleiMask = createNucleiMask();
            nucleiMask.show();
            // Move window off-screen
            if (nucleiMask.getWindow() != null) {
                nucleiMask.getWindow().setLocation(-1000, -1000);
            }
            
            // Create a duplicate for Voronoi
            String maskTitle = "DUP_" + nucleiMask.getTitle();
            ImagePlus voronoiImage = nucleiMask.duplicate();
            voronoiImage.setTitle(maskTitle);
            voronoiImage.show();
            // Move window off-screen
            if (voronoiImage.getWindow() != null) {
                voronoiImage.getWindow().setLocation(-1000, -1000);
            }
            
            // Ensure the image is binary before applying Voronoi
            // First set threshold (1-255 to include all non-black pixels)
            IJ.setThreshold(voronoiImage, 1, 255);
            // Convert to binary mask
            IJ.run(voronoiImage, "Convert to Mask", "");
            
            
            // Get ROI Manager
            RoiManager rm = RoiManager.getInstance();
            if (rm == null) {
                rm = new RoiManager();
            }
            
            // Apply Voronoi to the binary image
            IJ.run(voronoiImage, "Voronoi", "");
            
            
            // Threshold the Voronoi image to make borders completely black
            // First convert to 8-bit if needed
            if (voronoiImage.getBitDepth() != 8) {
                IJ.run(voronoiImage, "8-bit", "");
            }
            
            // Apply threshold to make borders black
            IJ.setThreshold(voronoiImage, 1, 255);
            IJ.run(voronoiImage, "Convert to Mask", "");
            
            // Add a 1-pixel black border around the image to prevent selection issues
            // Set foreground color to black
            IJ.setForegroundColor(255, 255, 255);
            
            // Left border
            voronoiImage.setRoi(0, 0, 1, imageHeight);
            IJ.run(voronoiImage, "Fill", "slice");
            
            // Right border
            voronoiImage.setRoi(imageWidth - 1, 0, 1, imageHeight);
            IJ.run(voronoiImage, "Fill", "slice");
            
            // Top border
            voronoiImage.setRoi(0, 0, imageWidth, 1);
            IJ.run(voronoiImage, "Fill", "slice");
            
            // Bottom border
            voronoiImage.setRoi(0, imageHeight - 1, imageWidth, 1);
            IJ.run(voronoiImage, "Fill", "slice");
            
            // Clear the ROI
            voronoiImage.deleteRoi();
            voronoiImage.updateAndDraw();
            
            
            // Create a mask that excludes background if available
            ImagePlus cytoplasmImage;
            if (backgroundMask != null) {
                // Find the maximum between Voronoi and background threshold
                ImageCalculator ic = new ImageCalculator();
                cytoplasmImage = ic.run("Max create", voronoiImage, backgroundMask);
                cytoplasmImage.setTitle("Cytoplasm");
                cytoplasmImage.show();
                // Move window off-screen
                if (cytoplasmImage.getWindow() != null) {
                    cytoplasmImage.getWindow().setLocation(-1000, -1000);
                }
                
                
            } else {
                cytoplasmImage = voronoiImage;
            }
            
            // Create a debug image to show all nucleus centers
            ImagePlus centerPointsImage = originalImage.duplicate();
            centerPointsImage.setTitle("Nucleus Centers");
            Overlay centerOverlay = new Overlay();
            
            // Process each nucleus to create cell and cytoplasm ROIs
            for (int i = 0; i < nucleiROIs.size(); i++) {
                Roi nucleusRoi = nucleiROIs.get(i);
                
                // Extract the nucleus number from the name (e.g., "Nucleus_5" -> 5)
                String nucleusName = nucleusRoi.getName();
                int nucleusNumber;
                
                try {
                    nucleusNumber = Integer.parseInt(nucleusName.split("_")[1]);
                } catch (Exception e) {
                    // If name parsing fails, use the index
                    nucleusNumber = i + 1;
                    Debug.log("Could not parse nucleus number from: " + nucleusName + ". Using " + nucleusNumber);
                }
                
                // Get the center of the nucleus
                Rectangle bounds = nucleusRoi.getBounds();
                int x = bounds.x + bounds.width/2;
                int y = bounds.y + bounds.height/2;
                
                // Add a point ROI to mark the center
                Roi pointRoi = new Roi(x-2, y-2, 5, 5);
                pointRoi.setStrokeColor(Color.RED);
                centerOverlay.add(pointRoi);
                
                // Check if the center is on or one pixel away from a border and move it away from the border
                if (x == 0 || x == 1) {
                    // Left border or one pixel away
                    x = 2;
                } else if (x == imageWidth || x == imageWidth - 1) {
                    // Right border or one pixel away
                    x = imageWidth - 2;
                }
                
                if (y == 0 || y == 1) {
                    // Top border or one pixel away
                    y = 2;
                } else if (y == imageHeight || y == imageHeight - 1) {
                    // Bottom border or one pixel away
                    y = imageHeight - 2;
                }
                
                // Make sure cytoplasmImage is the active image
                cytoplasmImage.setActivated();
                
                // Use doWand to select the cell region
                IJ.doWand(x, y);
                Roi cellRoi = cytoplasmImage.getRoi();
                
                // Save an image showing the doWand result for this nucleus
                if (cellRoi != null) {
                    ImagePlus wandResultImage = cytoplasmImage.duplicate();
                    wandResultImage.setTitle("DoWand Result for Nucleus " + nucleusNumber);
                    wandResultImage.setRoi(cellRoi);
                    wandResultImage.close();
                }
                
                // Check if a valid ROI was created
                if (cellRoi != null) {
                    // Create a copy of the cell ROI and add it to our list
                    Roi cellRoiCopy = (Roi)cellRoi.clone();
                    cellRoiCopy.setName("Cell_" + nucleusNumber);
                    cellROIs.add(cellRoiCopy);
                    
                    // Set color for cell ROI
                    cellRoiCopy.setStrokeColor(parseColor(ConfigVariables.getColorCentralCells()));
                    
                    // Create cytoplasm ROI by XOR operation between cell and nucleus
                    ShapeRoi cellShapeRoi = new ShapeRoi(cellRoi);
                    ShapeRoi nucleusShapeRoi = new ShapeRoi(nucleusRoi);
                    
                    // XOR operation: cell - nucleus = cytoplasm
                    ShapeRoi cytoplasmRoi = cellShapeRoi.xor(nucleusShapeRoi);
                    
                    // Add the cytoplasm ROI to our list
                    cytoplasmRoi.setName("Cytoplasm_" + nucleusNumber);
                    cytoplasmRoi.setStrokeColor(parseColor(ConfigVariables.getColorCentralCytoplasm()));
                    cytoplasmROIs.add(cytoplasmRoi);
                } else {
                    Debug.log("Warning: Could not create cell ROI for nucleus " + nucleusNumber);
                }
            }
            
            // Save the center points image
            centerPointsImage.setOverlay(centerOverlay);
            centerPointsImage.close();
            
            // Clean up
            if (nucleiMask != null) {
                nucleiMask.changes = false;
                nucleiMask.close();
            }
            
            if (voronoiImage != null) {
                voronoiImage.changes = false;
                voronoiImage.close();
            }
            
            if (cytoplasmImage != null && cytoplasmImage != voronoiImage) {
                cytoplasmImage.changes = false;
                cytoplasmImage.close();
            }
            
            Debug.log("Created " + cellROIs.size() + " cell ROIs and " + cytoplasmROIs.size() + " cytoplasm ROIs");
            
        } catch (Exception e) {
            Debug.log("Error in cytoplasm segmentation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Create a binary mask of nuclei
    private ImagePlus createNucleiMask() {
        // Get image dimensions
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        // Create a new binary processor
        ByteProcessor bp = new ByteProcessor(width, height);
        bp.setValue(0); // Set background to black
        bp.fill();
        
        // Draw nuclei as white
        bp.setValue(255);
        
        // Add all nuclei ROIs to the mask
        for (Roi roi : nucleiROIs) {
            bp.fill(roi);
        }
        
        // Create and return the mask image
        ImagePlus mask = new ImagePlus("Nuclei Mask", bp);
        return mask;
    }
    
    /**
     * Parse a color string from the config into a Color object
     * @param colorStr Color string (e.g., "red", "blue", "#FF0000")
     * @return Color object
     */
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
                        Debug.log("Invalid color format: " + colorStr + ". Using black.");
                        return Color.BLACK;
                    }
                }
                return Color.BLACK;
        }
    }
    
    /**
     * Convert a Color object to hex string format for ROI Manager
     */
    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
    // Get the cytoplasm ROIs
    public List<Roi> getCytoplasmROIs() {
        return cytoplasmROIs;
    }
    
    // Get the cell ROIs (nucleus + cytoplasm)
    public List<Roi> getCellROIs() {
        return cellROIs;
    }
}
