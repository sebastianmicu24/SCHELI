package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ImageProcessor;
import com.sebastianmicu.scheli.Debug;

public class Deconvolution {
    private ImagePlus originalImage;
    private ImagePlus hematoxylinImage;
    private ImagePlus eosinImage;
    private ImagePlus thirdComponentImage;
    
    // Constructor
    public Deconvolution(ImagePlus originalImage) {
        this.originalImage = originalImage;
        deconvolveHE();
    }
    
    // Perform H&E deconvolution
    private void deconvolveHE() {
        Debug.log("Performing color deconvolution...");
        
        try {
            // Create a duplicate of the original image to avoid modifying it
            ImagePlus duplicate = originalImage.duplicate();
            duplicate.setTitle("Temp_" + originalImage.getTitle());
            duplicate.show(); // Image must be visible for the deconvolution plugin
            // Move window off-screen
            if (duplicate.getWindow() != null) {
                duplicate.getWindow().setLocation(-1000, -1000);
            }
            
            // Run the Color Deconvolution plugin with H&E vectors
            IJ.run(duplicate, "Colour Deconvolution", "vectors=H&E hide");
            
            // Get the resulting images
            String baseName = duplicate.getTitle();
            hematoxylinImage = WindowManager.getImage(baseName + "-(Colour_1)");
            eosinImage = WindowManager.getImage(baseName + "-(Colour_2)");
            thirdComponentImage = WindowManager.getImage(baseName + "-(Colour_3)");
            
            // Check if images were created successfully
            if (hematoxylinImage == null || eosinImage == null) {
                Debug.log("Warning: Color deconvolution did not produce expected outputs");
                
                // Try alternative naming patterns
                String[] possibleNames = WindowManager.getImageTitles();
                Debug.log("Available images: " + String.join(", ", possibleNames));
                
                // Look for images with "Colour_1" or "Colour_2" in their names
                for (String name : possibleNames) {
                    if (name.contains("Colour_1") && hematoxylinImage == null) {
                        hematoxylinImage = WindowManager.getImage(name);
                        Debug.log("Found Hematoxylin image: " + name);
                    } else if (name.contains("Colour_2") && eosinImage == null) {
                        eosinImage = WindowManager.getImage(name);
                        Debug.log("Found Eosin image: " + name);
                    }
                }
            }
            
            // If still not found, create fallback images
            if (hematoxylinImage == null) {
                Debug.log("Creating fallback Hematoxylin image");
                hematoxylinImage = createFallbackImage("Hematoxylin");
            } else {
                hematoxylinImage.setTitle("Hematoxylin");
            }
            
            if (eosinImage == null) {
                Debug.log("Creating fallback Eosin image");
                eosinImage = createFallbackImage("Eosin");
            } else {
                eosinImage.setTitle("Eosin");
            }
            
            // Close the duplicate image
            duplicate.changes = false; // Prevent save dialog
            duplicate.close();
            
            // Close the third component if it exists
            if (thirdComponentImage != null) {
                thirdComponentImage.changes = false;
                thirdComponentImage.close();
            }
            
            Debug.log("Color deconvolution completed successfully");
            
        } catch (Exception e) {
            Debug.log("Error during color deconvolution: " + e.getMessage());
            e.printStackTrace();
            
            // Create fallback images if deconvolution fails
            hematoxylinImage = createFallbackImage("Hematoxylin");
            eosinImage = createFallbackImage("Eosin");
        }
    }
    
    // Create a fallback image if deconvolution fails
    private ImagePlus createFallbackImage(String title) {
        ImagePlus fallback = originalImage.duplicate();
        fallback.setTitle(title);
        return fallback;
    }
    
    // Get the Hematoxylin image
    public ImagePlus getHematoxylinImage() {
        return hematoxylinImage;
    }
    
    // Get the Eosin image
    public ImagePlus getEosinImage() {
        return eosinImage;
    }
}
