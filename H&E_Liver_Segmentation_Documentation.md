# H&E Liver Segmentation Plugin Documentation

## Overview

The H&E Liver Segmentation Plugin is an ImageJ plugin designed to segment H&E (Hematoxylin and Eosin) stained images of liver tissue. The plugin performs several key operations:

1. Deconvolutes the H&E image into separate Hematoxylin and Eosin channels
2. Segments nuclei using StarDist
3. Segments vessels
4. Creates cell and cytoplasm segmentations using Voronoi algorithm on nuclei
5. Identifies elements that touch the image border
6. Saves measurements of segmented objects to CSV files
7. Generates preview images of the segmentation

## Architecture

The plugin follows a modular architecture with specialized classes for each step of the workflow:

```
Main (entry point)
 ├── Input (image acquisition)
 ├── SettingsUI (configuration)
 ├── Progress (progress tracking)
 ├── Deconvolution (H&E separation)
 ├── BackgroundSegmentation (tissue/background separation)
 ├── NucleusSegmentation (nuclei detection)
 ├── CytoplasmSegmentation (cytoplasm detection)
 ├── FindBorderElements (border detection)
 ├── SavePreview (visualization)
 └── SaveData (measurements and export)
```

All classes interact with the `ConfigVariables` class to access configuration settings.

## Workflow Sequence

1. User runs the plugin
2. Input image is acquired (from open image or file selection)
3. Settings dialog is shown for configuration
4. Progress bar is initialized
5. H&E image is deconvoluted into Hematoxylin and Eosin channels
6. Background is segmented
7. Nuclei are segmented using StarDist
8. Cytoplasm is segmented using Voronoi algorithm on nuclei
9. Border elements are identified
10. Preview is created and saved (if enabled)
11. Measurements are made and saved to CSV files

## Class Descriptions

### Main

The entry point for the plugin that implements the `PlugIn` interface and orchestrates the workflow.

**Key Methods:**
- `run(String arg)`: Entry point method called by ImageJ
- `processImage(ImagePlus inputImage, Progress progress)`: Main processing workflow

**Interactions:**
- Initializes and coordinates all other classes
- Handles exceptions and error reporting

### ConfigVariables

Stores and manages all configuration variables using ImageJ's Preferences system.

**Key Variables:**
- **Percentiles**: Low, High, Tiles
- **Thresholds**: Background, Nuclei
- **Paths**: Input, Output
- **Scale**: Scale factor
- **Size**: Min/Max vessel, Min/Max nucleus
- **Color**: Colors for different ROI types
- **Booleans**: UseSemicolons, saveRoi, savePreview, saveAverages, saveIndividualCells

**Key Methods:**
- Getters and setters for all variables
- `savePreferences()`: Saves all preferences to disk
- `resetAllPreferences()`: Resets all preferences to default values

**Interactions:**
- Used by all other classes to access configuration settings

### Input

Handles getting the input image from the user.

**Key Methods:**
- `getInputImage()`: Gets an image from the current open image or prompts the user to select a file
- `isValidImage(ImagePlus imp)`: Validates if the image is suitable for processing

**Interactions:**
- Used by `Main` to get the input image
- Uses `ConfigVariables` to store/retrieve the input path

### SettingsUI

Creates and displays a dialog for changing settings.

**Key Methods:**
- `showDialog()`: Shows the settings dialog and returns true if the user clicked OK
- `updateSettings(GenericDialog gd)`: Updates the configuration variables based on user input

**Interactions:**
- Used by `Main` to show the settings dialog
- Updates `ConfigVariables` based on user input

### Progress

Shows a progress bar and status updates during processing.

**Key Methods:**
- `showProgress()`: Shows the progress dialog
- `updateStatus(String status)`: Updates the status message and increments progress
- `incrementProgress()`: Increments the progress bar
- `complete(String message)`: Completes the progress with a final message
- `close()`: Closes the progress dialog

**Interactions:**
- Used by `Main` to show progress and status updates

### Deconvolution

Performs color deconvolution on H&E images to separate the Hematoxylin and Eosin channels.

**Key Methods:**
- `deconvolveHE()`: Performs the deconvolution
- `getHematoxylinImage()`: Returns the Hematoxylin image
- `getEosinImage()`: Returns the Eosin image

**Interactions:**
- Used by `Main` to get deconvoluted images
- Results used by `NucleusSegmentation` and `SaveData`

### BackgroundSegmentation

Segments the background in the image.

**Key Methods:**
- `segmentBackground()`: Performs the background segmentation
- `getBackgroundMask()`: Returns the background mask
- `getBackgroundROI()`: Returns a ROI representing the background

**Interactions:**
- Used by `Main` to segment the background
- Uses `ConfigVariables` to get the background threshold

### NucleusSegmentation

Calls StarDist to segment nuclei.

**Key Methods:**
- `segmentNuclei()`: Performs the nucleus segmentation
- `getNucleiROIs()`: Returns the nuclei ROIs
- `addToRoiManager()`: Adds the nuclei ROIs to the ROI Manager

**Interactions:**
- Used by `Main` to segment nuclei
- Results used by `CytoplasmSegmentation`, `FindBorderElements`, `SavePreview`, and `SaveData`
- Uses `ConfigVariables` to get size constraints

### CytoplasmSegmentation

Uses Voronoi algorithm on nuclei to segment cytoplasm.

**Key Methods:**
- `segmentCytoplasm()`: Performs the cytoplasm segmentation
- `getCytoplasmROIs()`: Returns the cytoplasm ROIs
- `getCellROIs()`: Returns the cell ROIs (nucleus + cytoplasm)

**Interactions:**
- Used by `Main` to segment cytoplasm
- Takes nuclei ROIs from `NucleusSegmentation`
- Results used by `FindBorderElements`, `SavePreview`, and `SaveData`

### FindBorderElements

Identifies cells, nuclei, and cytoplasm that touch the image border.

**Key Methods:**
- `findBorderElements()`: Identifies elements that touch the image border
- `getBorderNucleiROIs()`, `getBorderCytoplasmROIs()`, `getBorderCellROIs()`: Returns border ROIs
- `getCentralNucleiROIs()`, `getCentralCytoplasmROIs()`, `getCentralCellROIs()`: Returns central ROIs

**Interactions:**
- Used by `Main` to identify border elements
- Takes ROIs from `NucleusSegmentation` and `CytoplasmSegmentation`
- Uses `ConfigVariables` to get colors for ROIs

### SavePreview

Creates a visual preview of the segmentation.

**Key Methods:**
- `createPreview()`: Creates a preview image with ROI overlays
- `savePreviewImage()`: Saves the preview image
- `getPreviewImage()`: Returns the preview image

**Interactions:**
- Used by `Main` to create and save a preview
- Takes ROIs from `NucleusSegmentation` and `CytoplasmSegmentation`
- Uses `ConfigVariables` to get the output path

### SaveData

Measures properties of all ROIs and saves the data to CSV files.

**Key Methods:**
- `measureROIs()`: Measures properties of all ROIs on original and deconvoluted images
- `saveToCSV()`: Saves the results to CSV files
- `saveResultsTable(ResultsTable results, String filePath)`: Saves a ResultsTable to a CSV file
- `saveAverages(String filePath)`: Calculates and saves averages

**Interactions:**
- Used by `Main` to measure and save data
- Takes ROIs from `NucleusSegmentation` and `CytoplasmSegmentation`
- Takes images from `Deconvolution`
- Uses `ConfigVariables` to get output settings

## Configuration Variables

### Percentiles
- **Low** (float): Lower percentile for intensity normalization
- **High** (float): Upper percentile for intensity normalization
- **Tiles** (integer): Number of tiles for processing

### Thresholds
- **Background** (integer): Threshold for background segmentation
- **Nuclei** (integer): Threshold for nuclei segmentation

### Paths
- **Input** (string): Path to the input image
- **Output** (string): Path to save output files

### Scale
- **Scale** (float): Scale factor for image processing

### Size
- **Min vessel** (float): Minimum size for vessels
- **Max vessel** (float): Maximum size for vessels
- **Min nucleus** (float): Minimum size for nuclei
- **Max nucleus** (float): Maximum size for nuclei

### Color
- **Central Nuclei** (string): Color for central nuclei
- **Central Vessels** (string): Color for central vessels
- **Border Nuclei** (string): Color for border nuclei
- **Border Vessels** (string): Color for border vessels
- **Central Cytoplasm** (string): Color for central cytoplasm
- **Border Cytoplasm** (string): Color for border cytoplasm
- **Central Cells** (string): Color for central cells
- **Border Cells** (string): Color for border cells

### Booleans
- **UseSemicolons** (boolean): Use semicolons as separators in CSV files
- **saveRoi** (boolean): Save ROIs to ROI Manager
- **savePreview** (boolean): Save preview image
- **saveAverages** (boolean): Save average measurements
- **saveIndividualCells** (boolean): Save measurements for individual cells

## Key Interactions

1. **Main → Input**: Gets the input image
2. **Main → SettingsUI**: Shows settings dialog
3. **SettingsUI → ConfigVariables**: Updates settings
4. **Main → Progress**: Shows progress and status updates
5. **Main → Deconvolution**: Performs color deconvolution
6. **Main → BackgroundSegmentation**: Segments background
7. **Main → NucleusSegmentation**: Segments nuclei
8. **NucleusSegmentation → StarDist**: Calls StarDist for nuclei segmentation
9. **Main → CytoplasmSegmentation**: Segments cytoplasm
10. **CytoplasmSegmentation → NucleusSegmentation**: Uses nuclei ROIs
11. **Main → FindBorderElements**: Identifies border elements
12. **FindBorderElements → NucleusSegmentation, CytoplasmSegmentation**: Uses ROIs
13. **Main → SavePreview**: Creates and saves preview
14. **SavePreview → NucleusSegmentation, CytoplasmSegmentation**: Uses ROIs
15. **Main → SaveData**: Measures and saves data
16. **SaveData → Deconvolution, NucleusSegmentation, CytoplasmSegmentation**: Uses images and ROIs

## Implementation Notes

The current implementation provides the structure and interaction between classes, with placeholders for the actual logic. The actual implementation of the segmentation algorithms, measurements, and data saving will need to be filled in.

Key areas for implementation:
1. Actual color deconvolution in `Deconvolution`
2. StarDist integration in `NucleusSegmentation`
3. Voronoi algorithm in `CytoplasmSegmentation`
4. ROI measurements in `SaveData`

## Building and Running

The plugin is built using Maven. To build the plugin:

1. Run `mvn clean package` to build the JAR file
2. Copy the JAR file to the `plugins` directory of ImageJ/Fiji
3. Restart ImageJ/Fiji
4. The plugin will be available in the `Plugins > Analyze` menu as "H&E Liver Segmentation"