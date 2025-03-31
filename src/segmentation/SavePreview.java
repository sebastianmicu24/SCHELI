package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.Overlay;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.io.FileSaver;
import java.util.List;
import java.io.File;
import java.awt.Color;
import com.sebastianmicu.scheli.Debug;

public class SavePreview {
    private ImagePlus originalImage;
    private List<Roi> nucleiROIs;
    private List<Roi> cytoplasmROIs;
    private List<Roi> cellROIs;
    private List<Roi> vesselROIs;
    private ImagePlus previewImage;
    private ImagePlus colorFilledImage;
    
    public SavePreview(ImagePlus originalImage, List<Roi> nucleiROIs,
                      List<Roi> cytoplasmROIs, List<Roi> cellROIs) {
        this.originalImage = originalImage;
        this.nucleiROIs = nucleiROIs;
        this.cytoplasmROIs = cytoplasmROIs;
        this.cellROIs = cellROIs;
        this.vesselROIs = null;
    }
    
    public SavePreview(ImagePlus originalImage, List<Roi> nucleiROIs,
                      List<Roi> cytoplasmROIs, List<Roi> cellROIs,
                      List<Roi> vesselROIs) {
        this(originalImage, nucleiROIs, cytoplasmROIs, cellROIs);
        this.vesselROIs = vesselROIs;
    }
    
    public void createPreview() {
        Debug.log("Creating segmentation preview...");
        
        // Create only the color-filled preview
        createColorFilledPreview();
    }
    
    private void createColorFilledPreview() {
        // Create a new RGB image with the same dimensions as the original
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        ColorProcessor cp = new ColorProcessor(width, height);
        
        // Start with a white background
        cp.setColor(Color.WHITE);
        cp.fill();
        
        // Fill ROIs in order: cells, cytoplasm, nuclei, vessels
        // This order ensures that smaller ROIs are drawn on top of larger ones
        
        // Fill cell ROIs
        if (cellROIs != null) {
            for (Roi roi : cellROIs) {
                Color fillColor;
                if (roi.getName() != null && roi.getName().contains("_Border")) {
                    fillColor = parseColor(ConfigVariables.getColorBorderCells());
                } else {
                    fillColor = parseColor(ConfigVariables.getColorCentralCells());
                }
                
                cp.setColor(fillColor);
                cp.fill(roi);
            }
        }
        
        // Fill cytoplasm ROIs
        if (cytoplasmROIs != null) {
            for (Roi roi : cytoplasmROIs) {
                Color fillColor;
                if (roi.getName() != null && roi.getName().contains("_Border")) {
                    fillColor = parseColor(ConfigVariables.getColorBorderCytoplasm());
                } else {
                    fillColor = parseColor(ConfigVariables.getColorCentralCytoplasm());
                }
                
                cp.setColor(fillColor);
                cp.fill(roi);
            }
        }
        
        // Fill nuclei ROIs
        if (nucleiROIs != null) {
            for (Roi roi : nucleiROIs) {
                Color fillColor;
                if (roi.getName() != null && roi.getName().contains("_Border")) {
                    fillColor = parseColor(ConfigVariables.getColorBorderNuclei());
                } else {
                    fillColor = parseColor(ConfigVariables.getColorCentralNuclei());
                }
                
                cp.setColor(fillColor);
                cp.fill(roi);
            }
        }
        
        // Fill vessel ROIs
        if (vesselROIs != null) {
            for (Roi roi : vesselROIs) {
                Color fillColor;
                if (roi.getName() != null && roi.getName().contains("_Border")) {
                    fillColor = parseColor(ConfigVariables.getColorBorderVessels());
                } else {
                    fillColor = parseColor(ConfigVariables.getColorCentralVessels());
                }
                
                cp.setColor(fillColor);
                cp.fill(roi);
            }
        }
        
        // Create the image from the processor
        colorFilledImage = new ImagePlus("Segmentation Preview (Color-filled)", cp);
        Debug.log("Created color-filled preview image");
    }
    
    public void savePreviewImage(String customBaseName) {
        if (colorFilledImage == null) {
            Debug.log("Error: Preview image not created yet. Call createPreview() first.");
            return;
        }
        
        // Get output path and create directory if needed
        String outputPath = getOutputPath();
        ensureOutputDirectory(outputPath);
        
        // Create preview subfolder
        String previewDir = outputPath + File.separator + "preview";
        ensureOutputDirectory(previewDir);
        
        // Generate filename
        String baseName = getBaseName(customBaseName);
        String colorFilledFilename = previewDir + File.separator + baseName + ".tif";
        
        // Save only the color-filled preview image
        FileSaver colorSaver = new FileSaver(colorFilledImage);
        boolean colorSuccess = colorSaver.saveAsTiff(colorFilledFilename);
        
        if (colorSuccess) {
            Debug.log("Preview image saved to: " + colorFilledFilename);
        } else {
            Debug.log("Error: Failed to save preview image.");
        }
    }
    
    private String getOutputPath() {
        String outputPath = ConfigVariables.getPathOutput();
        if (outputPath.isEmpty() && originalImage.getOriginalFileInfo() != null) {
            outputPath = new File(originalImage.getOriginalFileInfo().directory).getAbsolutePath();
        }
        return outputPath.isEmpty() ? System.getProperty("user.dir") : outputPath;
    }
    
    private void ensureOutputDirectory(String outputPath) {
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }
    
    private String getBaseName(String customBaseName) {
        if (customBaseName != null && !customBaseName.isEmpty()) {
            return customBaseName;
        }
        
        String title = originalImage.getTitle();
        return title.contains(".") ? title.substring(0, title.lastIndexOf('.')) : title;
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
                        Debug.log("Invalid color format: " + colorStr + ". Using black.");
                        return Color.BLACK;
                    }
                }
                return Color.BLACK;
        }
    }
    
    public ImagePlus getPreviewImage() {
        return previewImage;
    }
    
    public ImagePlus getColorFilledImage() {
        return colorFilledImage;
    }
}
