package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ij.plugin.PlugIn;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import java.io.File;
import com.sebastianmicu.scheli.Debug;
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey;

/**
 * ImageJ Plugin entry point for the XGBoost Model Training workflow.
 * Loads data, shows settings UI, trains the model, and saves it along with
 * necessary configuration files (features, mappings, etc.).
 */
public class TrainWorkflowMain implements PlugIn {

    @Override
    public void run(String arg) {
        Debug.log("--- Starting SCHELI Classifier Training Workflow ---");
        ProgressUI progress = new ProgressUI("Training Model...");

        // 1. Load configuration and show Training Settings UI
        ConfigVariables.loadPreferences();
        // Use the new TrainSettingsUI
        boolean settingsOk = TrainSettingsUI.showDialog();

        if (!settingsOk) {
            Debug.log("Training settings cancelled by user. Exiting plugin.");
            return;
        }
        Debug.log("Training settings confirmed. Proceeding with training workflow.");
        ConfigVariables.printAllValues(); // Log the final settings being used

        // Define training steps for progress bar
        final int TOTAL_TRAINING_STEPS = 4; // 1: Read, 2: Split, 3: Train/Eval, 4: Save (implicit in TrainModel)
        progress.setTotalSteps(TOTAL_TRAINING_STEPS);
        progress.showProgress();

        // 2. Get essential paths and parameters from configuration
        String csvDataDir = ConfigVariables.getString(ConfigKey.PATH_CSV_DATA);
        String jsonDataPath = ConfigVariables.getString(ConfigKey.PATH_JSON_DATA); // Ground truth for training
        String outputDir = ConfigVariables.getString(ConfigKey.PATH_OUTPUT_DIR);   // For model, features, mappings
        float trainRatio = ConfigVariables.getFloat(ConfigKey.TRAIN_RATIO);

        // Validate essential paths
        if (csvDataDir == null || csvDataDir.isEmpty()) {
            IJ.error("Training Error", "CSV Data Directory path is not set. Please configure it in the settings.");
            return;
        }
        if (jsonDataPath == null || jsonDataPath.isEmpty()) {
            IJ.error("Training Error", "Ground Truth JSON path is not set. This is required for training. Please configure it in the settings.");
            return;
        }
         if (outputDir == null || outputDir.isEmpty()) {
            IJ.error("Training Error", "Output Directory path is not set. Please configure it in the settings.");
            return;
        }
        // Ensure output directory exists
        File outDirFile = new File(outputDir);
        if (!outDirFile.exists()) {
             Debug.log("Output directory does not exist, attempting to create: " + outputDir);
             if (!outDirFile.mkdirs()) {
                 IJ.error("Training Error", "Could not create output directory: " + outputDir);
                 return;
             }
             Debug.log("Output directory created successfully.");
        }


        try {
            progress.updateStatus("Reading training data...");
            // 3. Initialize ReadData
            Debug.log("\n--- Reading Training Data ---");
            ReadData dataLoader = new ReadData(csvDataDir, jsonDataPath);

            // Check if any training labels were actually loaded
            if (dataLoader.getTrainingLabels().isEmpty()) {
                 IJ.error("Training Error", "No training labels were found matching cells in the CSV data. Check JSON paths and content, and CSV directory.");
                 return;
            }
            // Check if features were loaded
             if (dataLoader.getAllCellFeatures().isEmpty()) {
                 IJ.error("Training Error", "No features were found in the CSV data. Check CSV directory and content.");
                 return;
            }

            // 4. Create and Split Training Data using DataSplitter
            // This step also performs feature selection based on current config and creates the DMatrix
            progress.updateStatus("Splitting data...");
            Debug.log("\n--- Preparing and Splitting Training Data ---");
            DMatrix[] splitData = DataSplitter.createAndSplitTrainingData(dataLoader, trainRatio);

            if (splitData == null || splitData.length != 2 || splitData[0] == null || splitData[1] == null) {
                 // DataSplitter logs specific errors
                 IJ.error("Training Error", "Failed to create or split training data. Check logs for details.");
                 return;
            }
            DMatrix trainMat = splitData[0];
            DMatrix testMat = splitData[1];

            // 5. Train, Test, Evaluate, and Save the Model
            // TrainModel now saves the model (.json), features (.txt), config (.txt), weights (.properties)
            // DataSplitter saves the label mapping (.properties)
            progress.updateStatus("Training and evaluating model...");
            Debug.log("\n--- Training, Evaluating, and Saving Model ---");
            TrainModel.trainTestAndEvaluate(outputDir, dataLoader, trainMat, testMat, trainRatio);

            // 6. Workflow Complete
            Debug.log("\n--- SCHELI Classifier Training Workflow Completed Successfully ---");
            progress.complete("Training complete!");
            IJ.showMessage("Training Complete", "Model and associated files saved successfully to:\n" + outputDir);

        } catch (XGBoostError e) {
            Debug.log("XGBoostError during training workflow: " + e.getMessage());
            IJ.handleException(e);
            IJ.error("Training Error", "An XGBoost error occurred: " + e.getMessage());
        } catch (Exception e) {
            Debug.log("Unexpected error during training workflow: " + e.getMessage());
            IJ.handleException(e);
            IJ.error("Training Error", "An unexpected error occurred: " + e.getMessage());
        } finally {
            // Ensure progress bar window is closed
            if (progress != null) {
                progress.close();
            }
        }
    }
}