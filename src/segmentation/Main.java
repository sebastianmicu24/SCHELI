package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.plugin.PlugIn;
import ij.ImagePlus;
import java.io.File;
import com.sebastianmicu.scheli.segmentation.ConfigVariables;
import com.sebastianmicu.scheli.segmentation.Deconvolution;
import com.sebastianmicu.scheli.segmentation.BackgroundSegmentation;
import com.sebastianmicu.scheli.segmentation.NucleusSegmentation;
import com.sebastianmicu.scheli.segmentation.CytoplasmSegmentation;
import com.sebastianmicu.scheli.segmentation.FindBorderElements;
import com.sebastianmicu.scheli.segmentation.SavePreview;
import com.sebastianmicu.scheli.segmentation.SaveData;
import com.sebastianmicu.scheli.Debug;

public class Main implements PlugIn {
    
    public void run(String arg) {
        try {
            
            // Show settings dialog second
            Debug.log("Configuring settings...");
            if (!SettingsUI.showDialog()) {
                Debug.log("Settings configuration canceled");
                return; // User canceled
            }

            // Get input directory first
            Debug.log("Selecting input directory...");
            if (!Input.getInputDirectory()) {
                Debug.log("Input directory selection canceled");
                return; // User canceled
            }
            
            // Create output directory if needed
            ensureOutputDirectory();
            
            // Perform initialization/warm-up to prevent the first-image issue
            Debug.log("Initializing plugin components...");
            performWarmup();
            
            // Initialize progress bar AFTER settings are confirmed
            Progress progress = new Progress("H&E Liver Segmentation");
            progress.showProgress();
            
            try {
                // Process all images
                int totalImages = Input.getTotalImages();
                progress.setTotalImages(totalImages);
                
                while (Input.hasMoreImages()) {
                    ImagePlus inputImage = Input.getNextImage();
                    if (inputImage != null) {
                        // Move the input image window off-screen if it's visible
                        if (inputImage.getWindow() != null) {
                            inputImage.getWindow().setLocation(-1000, -1000);
                        }
                        
                        progress.nextImage();
                        progress.updateStatus("Processing: " + inputImage.getTitle());
                        
                        processImage(inputImage, progress);
                        
                        inputImage.close();
                    }
                }
                
                progress.complete("Processing complete! Processed " + totalImages + " images.");
            } finally {
                progress.close();
            }
            
        } catch (Exception e) {
            IJ.error("Error", "An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Performs a warm-up initialization to prevent issues with the first image analysis.
     * This creates a small test image and runs the vessel detection on it to initialize
     * all necessary components before the actual analysis begins.
     */
    private void performWarmup() {
        try {
            // Create a small test image (100x100 white image)
            Debug.log("Creating test image for initialization...");
            ImagePlus testImage = IJ.createImage("Test", "RGB", 100, 100, 1);
            testImage.getProcessor().setColor(java.awt.Color.WHITE);
            testImage.getProcessor().fill();
            
            // Add some black areas to simulate vessels
            testImage.getProcessor().setColor(java.awt.Color.BLACK);
            testImage.getProcessor().fillOval(20, 20, 30, 30);
            testImage.getProcessor().fillOval(60, 60, 20, 20);
            
            // Run background segmentation on the test image
            Debug.log("Running initialization vessel detection...");
            BackgroundSegmentation bgSeg = new BackgroundSegmentation(testImage);
            bgSeg.segmentBackground();
            
            // Check if vessels were detected
            int vesselCount = bgSeg.getVesselROIs().size();
            Debug.log("Initialization complete. Detected " + vesselCount + " vessels in test image.");
            
            // Clean up
            testImage.close();
            
            // Force garbage collection to clean up resources
            System.gc();
            
            Debug.log("Plugin initialization completed successfully.");
        } catch (Exception e) {
            Debug.log("Warning: Initialization failed: " + e.getMessage());
            // Continue anyway, as this is just a warm-up step
        }
    }
    
    private void ensureOutputDirectory() {
        String outputPath = ConfigVariables.getPathOutput();
        if (!outputPath.isEmpty()) {
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
        }
    }
    
    private void processImage(ImagePlus inputImage, Progress progress) {
        try {
            // Perform color deconvolution
            progress.updateStatus("Performing color deconvolution...");
            Deconvolution deconv = new Deconvolution(inputImage);
            ImagePlus hematoxylinImage = deconv.getHematoxylinImage();
            ImagePlus eosinImage = deconv.getEosinImage();
            
            // Hide the hematoxylin and eosin images if they're visible
            if (hematoxylinImage.getWindow() != null) {
                hematoxylinImage.getWindow().setLocation(-1000, -1000);
            }
            if (eosinImage.getWindow() != null) {
                eosinImage.getWindow().setLocation(-1000, -1000);
            }
            
            // Segment background
            progress.updateStatus("Segmenting background...");
            BackgroundSegmentation bgSeg = new BackgroundSegmentation(inputImage);
            bgSeg.segmentBackground();
            
            // Segment nuclei
            progress.updateStatus("Segmenting nuclei...");
            Debug.log("Creating NucleusSegmentation instance...");
            NucleusSegmentation nucleusSeg = new NucleusSegmentation(inputImage);
            Debug.log("Calling segmentNuclei()...");
            nucleusSeg.segmentNuclei();
            Debug.log("Nuclei segmentation completed");
            
            // Segment cytoplasm
            progress.updateStatus("Segmenting cytoplasm...");
            CytoplasmSegmentation cytoplasmSeg = new CytoplasmSegmentation(
                inputImage, nucleusSeg.getNucleiROIs());
            cytoplasmSeg.setVesselROIs(bgSeg.getVesselROIs());
            cytoplasmSeg.segmentCytoplasm();
            
            // Find border elements
            progress.updateStatus("Finding border elements...");
            FindBorderElements borderFinder = new FindBorderElements(
                inputImage,
                nucleusSeg.getNucleiROIs(),
                cytoplasmSeg.getCytoplasmROIs(),
                cytoplasmSeg.getCellROIs(),
                bgSeg.getVesselROIs()
            );
            borderFinder.findBorderElements();
            
            // Get base filename
            String baseName = getBaseName(inputImage);
            
            // Save preview if enabled
            if (ConfigVariables.getSavePreview()) {
                progress.updateStatus("Saving preview...");
                SavePreview preview = new SavePreview(
                    inputImage,
                    nucleusSeg.getNucleiROIs(),
                    cytoplasmSeg.getCytoplasmROIs(),
                    cytoplasmSeg.getCellROIs(),
                    bgSeg.getVesselROIs()
                );
                preview.createPreview();
                preview.savePreviewImage(baseName);
            }
            
            // Save data
            progress.updateStatus("Saving data...");
            SaveData saveData = new SaveData(
                inputImage,
                hematoxylinImage,
                eosinImage,
                nucleusSeg.getNucleiROIs(),
                cytoplasmSeg.getCytoplasmROIs(),
                cytoplasmSeg.getCellROIs(),
                bgSeg.getVesselROIs()
            );
            saveData.measureROIs();
            saveData.saveToCSV(baseName);
            
            // Clean up
            hematoxylinImage.changes = false;
            hematoxylinImage.close();
            hematoxylinImage.changes = false;
            eosinImage.close();
            
        } catch (Exception e) {
            IJ.error("Processing Error", "Error processing " + inputImage.getTitle() + ": " + e.getMessage());
        }
    }
    
    private String getBaseName(ImagePlus image) {
        String title = image.getTitle();
        return title.contains(".") ? title.substring(0, title.lastIndexOf('.')) : title;
    }
}
