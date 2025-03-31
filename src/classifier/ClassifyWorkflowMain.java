package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ij.plugin.PlugIn;
import java.io.File;
import com.sebastianmicu.scheli.Debug;
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey;

/**
 * ImageJ Plugin entry point for the XGBoost Data Classification workflow.
 * Loads a pre-trained model, shows settings UI for input/output paths,
 * classifies new data, and saves the results.
 */
public class ClassifyWorkflowMain implements PlugIn {

    @Override
    public void run(String arg) {
        Debug.log("--- Starting SCHELI Classifier Classification Workflow ---");
        ProgressUI progress = new ProgressUI("Classifying Data...");

        // 1. Load configuration and show Classification Settings UI
        ConfigVariables.loadPreferences();
        // Use the new ClassifySettingsUI
        boolean settingsOk = ClassifySettingsUI.showDialog();

        if (!settingsOk) {
            Debug.log("Classification settings cancelled by user. Exiting plugin.");
            return;
        }
        Debug.log("Classification settings confirmed. Proceeding with classification workflow.");
        ConfigVariables.printAllValues(); // Log the final settings being used

        // Define classification steps for progress bar
        final int TOTAL_CLASSIFY_STEPS = 5; // 1: Load Model/Files, 2: Read Data, 3: Prepare Data, 4: Predict, 5: Save Results
        progress.setTotalSteps(TOTAL_CLASSIFY_STEPS);
        progress.showProgress();

        // 2. Get essential paths from configuration
        String modelPath = ConfigVariables.getString(ConfigKey.PATH_MODEL_FILE); // Path to the .bin model
        String csvInputDir = ConfigVariables.getString(ConfigKey.PATH_CSV_DATA); // Directory with CSVs to classify
        String outputDir = ConfigVariables.getString(ConfigKey.PATH_OUTPUT_DIR); // Where results will be saved
        String jsonPathForClassDetails = ConfigVariables.getString(ConfigKey.PATH_JSON_DATA); // For class names/colors in output

        // Derive paths for supporting files (features, mapping) based on model location
        File modelFile = new File(modelPath);
        String modelDir = modelFile.getParent(); // Directory containing the model file
        if (modelDir == null) {
             IJ.error("Classification Error", "Could not determine directory containing the model file: " + modelPath);
             return;
        }
        String selectedFeaturesPath = modelDir + File.separator + "selected_features.txt";
        String labelMappingPath = modelDir + File.separator + "xgboost_label_mapping.properties";


        // Validate essential paths
        if (modelPath == null || modelPath.isEmpty() || !modelFile.exists()) {
            IJ.error("Classification Error", "Model file path is not set or file not found: " + modelPath + ". Please configure it in the settings.");
            return;
        }
         if (!new File(selectedFeaturesPath).exists()) {
             IJ.error("Classification Error", "Selected features file not found in model directory: " + selectedFeaturesPath);
             return;
         }
         if (!new File(labelMappingPath).exists()) {
             IJ.error("Classification Error", "Label mapping file not found in model directory: " + labelMappingPath);
             return;
         }
        if (csvInputDir == null || csvInputDir.isEmpty()) {
            IJ.error("Classification Error", "Input CSV Directory path is not set. Please configure it in the settings.");
            return;
        }
        if (outputDir == null || outputDir.isEmpty()) {
            IJ.error("Classification Error", "Output Directory path is not set. Please configure it in the settings.");
            return;
        }
         if (jsonPathForClassDetails == null || jsonPathForClassDetails.isEmpty()) {
             IJ.error("Classification Error", "Class Details JSON path is not set. This is needed for output formatting. Please configure it in the settings.");
             return;
         }

        // Ensure output directory exists
        File outDirFile = new File(outputDir);
         if (!outDirFile.exists()) {
             Debug.log("Output directory does not exist, attempting to create: " + outputDir);
             if (!outDirFile.mkdirs()) {
                 IJ.error("Classification Error", "Could not create output directory: " + outputDir);
                 return;
             }
             Debug.log("Output directory created successfully.");
         }


        try {
            progress.updateStatus("Loading model and support files...");
            // 3. Instantiate ClassifyData
            ClassifyData classifier = new ClassifyData();

            // 4. Call the refactored classification method
            // This method will handle loading the model, features, mappings, reading data, classifying, and saving results.
            // Note: ClassifyData.classifyDirectory handles internal steps like reading, preparing, predicting, saving
            // We can't easily update progress granularly within that method without passing the progress object.
            // For now, we'll update before and after the main call.
            progress.updateStatus("Processing data and classifying...");
            Debug.log("\n--- Starting Classification Process ---");
            classifier.classifyDirectory(
                modelPath,
                selectedFeaturesPath,
                labelMappingPath,
                csvInputDir,
                outputDir,
                jsonPathForClassDetails
            );

            // 5. Workflow Complete
            Debug.log("\n--- SCHELI Classifier Classification Workflow Completed Successfully ---");
            progress.complete("Classification complete!");
            IJ.showMessage("Classification Complete", "Classification results saved successfully to:\n" + outputDir);

        } catch (Exception e) {
            // ClassifyData should handle its specific errors (e.g., XGBoostError, IOException)
            // Catch any unexpected errors here.
            Debug.log("Unexpected error during classification workflow: " + e.getMessage());
            IJ.handleException(e);
            IJ.error("Classification Error", "An unexpected error occurred during classification: " + e.getMessage());
        } finally {
            // Ensure progress bar window is closed
            if (progress != null) {
                progress.close();
            }
        }
    }
}