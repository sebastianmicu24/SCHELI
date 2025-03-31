package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter; // Added import
import java.io.IOException;
import java.util.*; // Import all of java.util
import com.sebastianmicu.scheli.Debug;
// Import the ConfigKey enum
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey;

public class TrainModel {

    // Removed standalone main method as it's no longer representative

    /**
     * Retrieves the base XGBoost parameters from ConfigVariables.
     * @param numClasses The number of classes determined from the training data split.
     * @return A map of XGBoost parameter key-value pairs.
     */
    private static Map<String, Object> getBaseParams(int numClasses) {
        Map<String, Object> params = new HashMap<>();
        // Core parameters from ConfigVariables
        params.put("eta", ConfigVariables.getFloat(ConfigKey.LEARNING_RATE));
        params.put("max_depth", ConfigVariables.getInt(ConfigKey.MAX_DEPTH));
        params.put("min_child_weight", ConfigVariables.getInt(ConfigKey.MIN_CHILD_WEIGHT));
        params.put("subsample", ConfigVariables.getFloat(ConfigKey.CELLS_SUBSAMPLE));
        params.put("colsample_bytree", ConfigVariables.getFloat(ConfigKey.FEATURES_SUBSAMPLE));
        params.put("lambda", ConfigVariables.getFloat(ConfigKey.LAMBDA));
        params.put("alpha", ConfigVariables.getFloat(ConfigKey.ALPHA));
        params.put("gamma", ConfigVariables.getFloat(ConfigKey.GAMMA));

        // Fixed parameters for multi-class classification
        params.put("objective", "multi:softprob"); // Predict probabilities for each class
        params.put("num_class", numClasses);       // Number of unique classes in the training data
        params.put("eval_metric", "mlogloss");     // Logarithmic loss for multi-class evaluation
        // params.put("early_stopping_rounds", 50); // Optional: Stop early if eval metric doesn't improve

        // Other potentially useful parameters (can be added to ConfigVariables if needed)
        // params.put("tree_method", "hist"); // Often faster for larger datasets
        params.put("verbosity", 1); // 0=silent, 1=warning, 2=info, 3=debug

        return params;
    }

    /**
     * Trains the XGBoost model using the provided training data, evaluates it on the
     * test data, and saves the model and results.
     *
     * @param outputDir The directory to save the model and results.
     * @param dataLoader The ReadData instance (used to get feature names and class mappings).
     * @param trainMat The training DMatrix (already feature-selected and label-mapped).
     * @param testMat The testing DMatrix (already feature-selected and label-mapped).
     * @param trainRatio The original train/test split ratio (for logging purposes).
     */
    public static void trainTestAndEvaluate(
            String outputDir,
            ReadData dataLoader,
            DMatrix trainMat,
            DMatrix testMat,
            float trainRatio) { // Keep trainRatio for logging/saving results

        if (trainMat == null || testMat == null) {
            Debug.log("Error: Training or testing matrix is null. Cannot proceed with training.");
            return;
        }

        try {
            Debug.log("--- Starting Model Training and Evaluation ---");

            // Get number of classes from the mapping used during split
            int numClasses = DataSplitter.getLabelToIndexMapping().size();
            if (numClasses <= 1) {
                 Debug.log("Error: Training requires at least 2 classes. Found: " + numClasses + " in the training split mapping.");
                 return; // Exit if not enough classes
            }
            Debug.log("Training for " + numClasses + " classes (based on training split mapping).");

            // Configure XGBoost parameters using the determined number of classes
            Map<String, Object> params = getBaseParams(numClasses);

            // --- Class Balancing (using weights) ---
            Map<Integer, Float> weightsMap = null; // Store calculated weights if balancing is done
            Debug.log("Calculating class weights for balancing...");
            float[] mappedTrainLabels = trainMat.getLabel(); // Labels are already mapped indices
            Map<Integer, Integer> classCounts = new HashMap<>(); // Mapped Index -> Count

            // Count samples per mapped class index
            for (float mappedLabelFloat : mappedTrainLabels) {
                int mappedLabel = (int) mappedLabelFloat;
                classCounts.put(mappedLabel, classCounts.getOrDefault(mappedLabel, 0) + 1);
            }

            // Calculate weights inversely proportional to class frequencies
            if (!classCounts.isEmpty() && classCounts.size() == numClasses) { // Ensure all expected classes are present
                 int totalSamples = mappedTrainLabels.length;
                 weightsMap = new HashMap<>();
                 float[] sampleWeights = new float[totalSamples];

                 Debug.log("Class distribution in training set (mapped indices):");
                 for (Map.Entry<Integer, Integer> entry : classCounts.entrySet()) {
                     int classIndex = entry.getKey();
                     int count = entry.getValue();
                     Debug.log("  Index " + classIndex + ": " + count + " samples");
                     if (count > 0) {
                         // Common heuristic: weight = totalSamples / (numClasses * count)
                         float weight = (float) totalSamples / (numClasses * count);
                         weightsMap.put(classIndex, weight);
                         Debug.log("    Calculated weight: " + String.format("%.4f", weight));
                     } else {
                          Debug.log("    Warning: Class index " + classIndex + " has 0 samples. Assigning weight 1.0.");
                          weightsMap.put(classIndex, 1.0f);
                     }
                 }

                 // Apply weights to the training matrix
                 for (int i = 0; i < totalSamples; i++) {
                     int labelIndex = (int) mappedTrainLabels[i];
                     sampleWeights[i] = weightsMap.getOrDefault(labelIndex, 1.0f); // Default to 1 if class somehow missing
                 }
                 trainMat.setWeight(sampleWeights);
                 Debug.log("Applied per-sample weights to training matrix for balancing.");

            } else {
                 Debug.log("Warning: Could not calculate balancing weights. Class counts empty or mismatch with numClasses (" + classCounts.size() + " vs " + numClasses + "). Proceeding without weights.");
                 weightsMap = null; // Ensure weights map is null
            }
            // --- End Class Balancing ---


            // Get number of trees (boosting rounds) from ConfigVariables
            int nTrees = ConfigVariables.getInt(ConfigKey.N_TREES);

            // Log training parameters
            Debug.log("Training XGBoost model with parameters:");
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Debug.log("  " + entry.getKey() + ": " + entry.getValue());
            }
            Debug.log("Number of boosting rounds (n_estimators): " + nTrees);

            // Create a watch list to monitor performance during training
            Map<String, DMatrix> watches = new HashMap<>();
            watches.put("train", trainMat);
            watches.put("test", testMat); // Use test set for evaluation metric monitoring

            // Train the model
            Debug.log("Starting XGBoost training...");
            Booster booster = XGBoost.train(trainMat, params, nTrees, watches, null, null);
            Debug.log("Training complete.");

            // Evaluate model on test data
            Debug.log("\n--- Evaluating model on test data ---");
            float[][] predictions = booster.predict(testMat);

            // Calculate and log evaluation metrics using EvaluateResults class
            // Get the necessary mappings for evaluation
            Map<String, Integer> classNameToIdMap = dataLoader.getClassNameToIdMap(); // Name -> Original ID
            Map<Float, Integer> finalLabelToIndex = DataSplitter.getLabelToIndexMapping(); // Original Label ID -> Mapped Index

            Map<String, Double> metrics = EvaluateResults.evaluateModel(predictions, testMat, classNameToIdMap, finalLabelToIndex);

            // --- Feature Importance Logging ---
            try {
                Debug.log("\n--- Feature Importance (Gain) ---");
                // Get selected feature names in the order they appear in the DMatrix
                List<String> featureNames = dataLoader.getSelectedFeatureNames();
                Map<String, Double> importanceScores = booster.getScore("", "gain"); // Use "gain" or "weight" or "cover"

                if (featureNames == null || featureNames.isEmpty()) {
                    Debug.log("  Error: Selected feature names not available from ReadData.");
                } else if (importanceScores.isEmpty()) {
                    Debug.log("  No feature importance scores available from booster (model might be trivial or error occurred).");
                } else {
                    // Map generic "f0", "f1"... names from booster to original names
                    Map<String, Double> namedImportance = new HashMap<>();
                    double totalScore = 0; // For normalization (optional)

                    for(Map.Entry<String, Double> entry : importanceScores.entrySet()) {
                        String genericName = entry.getKey(); // e.g., "f0"
                        Double score = entry.getValue();
                        try {
                            int featureIndex = Integer.parseInt(genericName.substring(1)); // "f0" -> 0
                            if (featureIndex >= 0 && featureIndex < featureNames.size()) {
                                String originalName = featureNames.get(featureIndex);
                                namedImportance.put(originalName, score);
                                totalScore += score;
                            } else {
                                Debug.log("  Warning: Index " + featureIndex + " from importance score (" + genericName + ") is out of bounds for selected feature names (size " + featureNames.size() + ").");
                            }
                        } catch (NumberFormatException | IndexOutOfBoundsException e) {
                            Debug.log("  Warning: Could not parse or map feature importance key '" + genericName + "'. Skipping.");
                        }
                    }

                    if (namedImportance.isEmpty()) {
                         Debug.log("  Warning: Could not map any booster scores to feature names.");
                    } else {
                        // Sort by importance score (descending)
                        List<Map.Entry<String, Double>> sortedImportance = new ArrayList<>(namedImportance.entrySet());
                        sortedImportance.sort(Map.Entry.<String, Double>comparingByValue().reversed());

                        // Log top N features or all features
                        int topN = 20;
                        Debug.log("  Top " + Math.min(topN, sortedImportance.size()) + " features by gain:");
                        for (int i = 0; i < Math.min(topN, sortedImportance.size()); i++) {
                            Map.Entry<String, Double> entry = sortedImportance.get(i);
                            // Optional: Normalize score
                            // double normalizedScore = (totalScore > 0) ? entry.getValue() / totalScore : 0.0;
                            Debug.log(String.format("    %-50s: %.6f", entry.getKey(), entry.getValue()));
                        }
                        if (sortedImportance.size() > topN) {
                             Debug.log("    ... (" + (sortedImportance.size() - topN) + " more features)");
                        }
                    }
                }
            } catch (XGBoostError e) {
                Debug.log("  Error retrieving/processing feature importance: " + e.getMessage());
            } catch (Exception e) {
                Debug.log("  Unexpected error during feature importance processing: " + e.getMessage());
                e.printStackTrace(); // Log stack trace for unexpected errors
            }
            // --- End Feature Importance ---

            // --- Saving Results ---
            // Ensure output directory exists
            File dir = new File(outputDir);
            if (!dir.exists() && !dir.mkdirs()) {
                 Debug.log("Error: Could not create output directory: " + outputDir);
                 // Decide whether to continue without saving or throw error
            }

            // Save the trained model (JSON format for inspection/compatibility)
            String modelPath = outputDir + File.separator + "xgboost_model.json";
            booster.saveModel(modelPath);
            Debug.log("Model saved to: " + modelPath);

            // Save the list of selected features used for training
            saveSelectedFeatures(outputDir, dataLoader.getSelectedFeatureNames());

            // Save model configuration
            saveModelConfiguration(outputDir, params, nTrees, trainMat.rowNum(), numClasses);

            // Save class balancing weights if they were calculated
            if (weightsMap != null && !weightsMap.isEmpty()) {
                saveWeights(outputDir, weightsMap);
            }

            // Save evaluation results
            saveEvaluationResults(outputDir, metrics, trainMat.rowNum(), testMat.rowNum(), trainRatio);
            // --- End Saving Results ---

            Debug.log("--- Model Training and Evaluation Complete ---");

        } catch (XGBoostError | IOException e) { // Catch XGBoost and IO errors
            Debug.log("Error during training or saving results: " + e.getMessage());
            IJ.handleException(e); // Use ImageJ's handler
        } catch (Exception e) {
            Debug.log("Unexpected error during training: " + e.getMessage());
            IJ.handleException(e); // Use ImageJ's handler
        }
    }



    // Helper method to save model configuration
    private static void saveModelConfiguration(String outputDir, Map<String, Object> params, int nTrees, long trainSize, int numClasses) throws IOException {
        String configPath = outputDir + File.separator + "xgboost_config.txt";
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new FileWriter(configPath))) {
            writer.println("XGBoost Model Configuration");
            writer.println("---------------------------");
            writer.println("Training date: " + new java.util.Date());
            writer.println("Number of training instances: " + trainSize);
            writer.println("Number of classes (mapped): " + numClasses);
            writer.println("\nModel Parameters:");
            // Sort parameters for consistent output
            List<String> sortedKeys = new ArrayList<>(params.keySet());
            Collections.sort(sortedKeys);
            for (String key : sortedKeys) {
                writer.println("  " + key + ": " + params.get(key));
            }
            writer.println("  n_estimators (boosting rounds): " + nTrees); // Use consistent naming
        }
        Debug.log("Model configuration saved to: " + configPath);
    }

    // Helper method to save evaluation results
    private static void saveEvaluationResults(String outputDir, Map<String, Double> metrics, long trainSize, long testSize, float trainRatio) throws IOException {
        String evalPath = outputDir + File.separator + "evaluation_results.txt";
        try (java.io.PrintWriter evalWriter = new java.io.PrintWriter(new FileWriter(evalPath))) {
            evalWriter.println("XGBoost Model Evaluation Results");
            evalWriter.println("-------------------------------");
            evalWriter.println("Evaluation date: " + new java.util.Date());
            evalWriter.println("Training set size: " + trainSize + " samples (" + String.format("%.1f", trainRatio * 100) + "%)");
            evalWriter.println("Test set size: " + testSize + " samples (" + String.format("%.1f", (1 - trainRatio) * 100) + "%)");
            evalWriter.println("\nPerformance Metrics (on Test Set):");
            evalWriter.println("  Accuracy:           " + String.format("%.4f", metrics.getOrDefault("accuracy", 0.0)));
            evalWriter.println("  Macro Avg Precision:" + String.format("%.4f", metrics.getOrDefault("precision", 0.0)));
            evalWriter.println("  Macro Avg Recall:   " + String.format("%.4f", metrics.getOrDefault("recall", 0.0)));
            evalWriter.println("  Macro Avg F1 Score: " + String.format("%.4f", metrics.getOrDefault("f1", 0.0)));
            // Add other metrics if EvaluateResults calculates them (e.g., per-class metrics)
        }
        Debug.log("Evaluation results saved to: " + evalPath);
    }

    // Helper method to save class balancing weights
    private static void saveWeights(String outputDir, Map<Integer, Float> weights) throws IOException {
        String weightsPath = outputDir + File.separator + "xgboost_class_weights.properties";
        java.util.Properties props = new java.util.Properties();
        // Key = Mapped Class Index, Value = Calculated Weight
        for (Map.Entry<Integer, Float> entry : weights.entrySet()) {
            props.setProperty(String.valueOf(entry.getKey()), String.format(Locale.US, "%.6f", entry.getValue())); // Use US locale for decimal point
        }
        try (FileOutputStream fos = new FileOutputStream(weightsPath)) {
            props.store(fos, "XGBoost Class Balancing Weights (Mapped_Index=Weight)");
        }
        Debug.log("Class balancing weights saved to: " + weightsPath);
    }

    // Helper method to save the list of selected feature names
    private static void saveSelectedFeatures(String outputDir, List<String> featureNames) {
        if (featureNames == null || featureNames.isEmpty()) {
            Debug.log("Warning: Selected feature names list is null or empty. Cannot save features file.");
            return;
        }
        String featuresPath = outputDir + File.separator + "selected_features.txt";
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new FileWriter(featuresPath))) {
            for (String featureName : featureNames) {
                writer.println(featureName);
            }
            Debug.log("Selected features list saved to: " + featuresPath);
        } catch (IOException e) {
            Debug.log("Error saving selected features list: " + e.getMessage());
            // Optionally re-throw or handle differently
        }
    }
}
