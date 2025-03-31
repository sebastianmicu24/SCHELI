package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.io.DirectoryChooser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import com.sebastianmicu.scheli.Debug;

public class Input {
    private static final String[] SUPPORTED_EXTENSIONS = {".tif", ".tiff", ".jpg", ".jpeg", ".png", ".bmp"};
    private static List<File> imageFiles = new ArrayList<>();
    private static int currentImageIndex = 0;
    
    public static boolean getInputDirectory() {
        // Reset state
        imageFiles.clear();
        currentImageIndex = 0;
        
        // Get directory from settings
        String inputPath = ConfigVariables.getPathInput();
        
        // If path is valid, use it
        if (inputPath != null && !inputPath.isEmpty()) {
            File dir = new File(inputPath);
            if (dir.exists() && dir.isDirectory()) {
                return loadImagesFromDirectory(dir);
            }
        }
        
        // If no valid path, ask user to select a directory
        DirectoryChooser dc = new DirectoryChooser("Select Directory with H&E Images");
        String directory = dc.getDirectory();
        
        if (directory == null) {
            return false; // User canceled
        }
        
        // Save and use the selected directory
        ConfigVariables.setPathInput(directory);
        return loadImagesFromDirectory(new File(directory));
    }
    
    private static boolean loadImagesFromDirectory(File directory) {
        if (!validateDirectory(directory)) {
            return false;
        }
        
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            IJ.error("Empty Directory", "No files found in the selected directory.");
            return false;
        }
        
        // Filter for image files
        Arrays.stream(files)
              .filter(Input::isImageFile)
              .forEach(imageFiles::add);
        
        if (imageFiles.isEmpty()) {
            IJ.error("No Images Found", "No valid image files found in the selected directory.");
            return false;
        }
        
        Debug.log("Found " + imageFiles.size() + " image files in " + directory.getAbsolutePath());
        return true;
    }
    
    private static boolean validateDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            IJ.error("Invalid Directory", "The selected path is not a valid directory.");
            return false;
        }
        return true;
    }
    
    private static boolean isImageFile(File file) {
        if (file.isDirectory()) return false;
        
        String name = file.getName().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    public static ImagePlus getNextImage() {
        if (imageFiles.isEmpty() || currentImageIndex >= imageFiles.size()) {
            return null;
        }
        
        File imageFile = imageFiles.get(currentImageIndex++);
        ImagePlus imp = IJ.openImage(imageFile.getAbsolutePath());
        
        // Skip invalid images
        if (imp == null || imp.getType() != ImagePlus.COLOR_RGB) {
            Debug.log("Skipping invalid image: " + imageFile.getName());
            return getNextImage();
        }
        
        Debug.log("Processing image " + currentImageIndex + "/" + imageFiles.size() + ": " + imageFile.getName());
        return imp;
    }
    
    public static boolean hasMoreImages() {
        return currentImageIndex < imageFiles.size();
    }
    
    public static int getTotalImages() {
        return imageFiles.size();
    }
    
    public static int getCurrentImageIndex() {
        return currentImageIndex;
    }
}
