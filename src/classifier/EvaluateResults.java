package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ij.text.TextWindow;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import com.sebastianmicu.scheli.Debug;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
// import java.util.ArrayList; // Not directly used after move
// import java.util.Arrays; // Not directly used after move
// import java.util.Random; // Not used in evaluation
import java.util.Collections; // Needed for Collections.unmodifiableMap if used

/**
 * Evaluates XGBoost model performance using confusion matrix,
 * k-fold cross-validation, and feature importance analysis.
 * 
 * @author Cristian Sebastian Micu
 */
public class EvaluateResults {
    
    private Booster model;
    private float threshold = 0.5f;
    
    /**
     * Constructor with model
     * 
     * @param model Trained XGBoost model
     */
    public EvaluateResults(Booster model) {
        this.model = model;
    }
    
    /**
     * Set classification threshold
     * 
     * @param threshold Threshold for binary classification (0.0-1.0)
     */
    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }
    
    // Removed original generateConfusionMatrix method as evaluateModel covers it.

    /**
     * Evaluates model performance on a test dataset.
     * (Moved from DataSplitter)
     *
     * @param predictions The predicted probabilities for each class (output of multi:softprob)
     * @param testMatrix The test dataset with transformed labels
     * @param originalClassMapping A map from original class name (String) to original class ID (Integer) from ReadData
     * @param finalLabelToIndex A map from original float label to the final mapped index used by XGBoost
     * @return A map containing evaluation metrics (accuracy, macro-avg precision, recall, F1)
     * @throws XGBoostError If there's an error accessing the test data
     */
    public static Map<String, Double> evaluateModel(
            float[][] predictions,
            DMatrix testMatrix,
            Map<String, Integer> originalClassMapping,
            Map<Float, Integer> finalLabelToIndex) throws XGBoostError {
        
        // Create reverse mappings for convenience
        Map<Integer, String> indexToClassName = new HashMap<>();
        Map<Integer, Float> finalIndexToLabel = new HashMap<>();
        for (Map.Entry<Float, Integer> entry : finalLabelToIndex.entrySet()) {
            finalIndexToLabel.put(entry.getValue(), entry.getKey());
        }
        // Find the class name corresponding to the original label ID
        for (Map.Entry<String, Integer> classEntry : originalClassMapping.entrySet()) {
            String className = classEntry.getKey();
            float originalLabelId = classEntry.getValue().floatValue();
            if (finalLabelToIndex.containsKey(originalLabelId)) {
                int mappedIndex = finalLabelToIndex.get(originalLabelId);
                indexToClassName.put(mappedIndex, className);
            } else {
                 Debug.log("Warning: Class '" + className + "' (ID: " + originalLabelId + ") from JSON was not found in the training data labels.");
            }
        }

        float[] trueLabels = testMatrix.getLabel();
        int numSamples = predictions.length;
        int numClasses = predictions[0].length;
        
        Debug.log("Evaluating model performance on " + numSamples + " test samples with " + numClasses + " classes...");
        
        // Convert probability predictions to class labels
        int[] predictedLabels = new int[numSamples];
        
        // Always use the multi-class approach (find highest probability)
        // as multi:softprob outputs probabilities for all classes.
        for (int i = 0; i < numSamples; i++) {
            float maxProb = -1;
            int maxClassIndex = -1;
            for (int j = 0; j < numClasses; j++) {
                if (predictions[i][j] > maxProb) {
                    maxProb = predictions[i][j];
                    maxClassIndex = j;
                }
            }
            predictedLabels[i] = maxClassIndex; // Store the *index* of the predicted class

            // Log some predictions for debugging using the passed class name mapping
            if (i < 5) {
                int trueClassIndex = (int) trueLabels[i];
                String trueClassName = indexToClassName.getOrDefault(trueClassIndex, "Unknown Index " + trueClassIndex);
                String predClassName = indexToClassName.getOrDefault(predictedLabels[i], "Unknown Index " + predictedLabels[i]);
                
                // Build probability string
                StringBuilder probStr = new StringBuilder("[");
                for(int k=0; k<numClasses; k++) {
                    probStr.append(String.format("%.3f", predictions[i][k]));
                    if (k < numClasses - 1) probStr.append(", ");
                }
                probStr.append("]");

                Debug.log(String.format("Sample %d: True=%s (Idx %d), Pred=%s (Idx %d), MaxProb=%.4f, Probs=%s",
                        i, trueClassName, trueClassIndex, predClassName, predictedLabels[i], maxProb, probStr.toString()));
            }
        }
        
        // Initialize confusion matrix
        // rows = actual class, columns = predicted class
        int[][] confusionMatrix = new int[numClasses][numClasses];
        
        // Fill confusion matrix
        for (int i = 0; i < numSamples; i++) {
            int actualClass = (int) trueLabels[i];
            int predictedClass = predictedLabels[i];
            
            // Safety check to prevent array index out of bounds
            if (actualClass >= 0 && actualClass < numClasses &&
                predictedClass >= 0 && predictedClass < numClasses) {
                confusionMatrix[actualClass][predictedClass]++;
            } else {
                Debug.log("Warning: Invalid class index - Actual: " + actualClass +
                       ", Predicted: " + predictedClass +
                       " (numClasses: " + numClasses + ")");
            }
        }
        
        // Print confusion matrix with better formatting and class names
        Debug.log("Confusion Matrix:");
        Debug.log("----------------");
        
        // Header row
        StringBuilder header = new StringBuilder();
        header.append("               |");
        for (int j = 0; j < numClasses; j++) {
            String className = indexToClassName.getOrDefault(j, "Idx " + j);
            header.append(String.format(" Pred %-8s |", className.substring(0, Math.min(className.length(), 8)))); // Truncate name if too long
        }
        Debug.log(header.toString());
        
        // Separator row
        StringBuilder separator = new StringBuilder();
        separator.append("---------------|");
        for (int j = 0; j < numClasses; j++) {
            separator.append("--------------|");
        }
        Debug.log(separator.toString());
        
        // Data rows
        for (int i = 0; i < numClasses; i++) {
            StringBuilder row = new StringBuilder();
            String className = indexToClassName.getOrDefault(i, "Idx " + i);
            row.append(String.format(" Actual %-8s|", className.substring(0, Math.min(className.length(), 8)))); // Truncate name if too long
            for (int j = 0; j < numClasses; j++) {
                row.append(String.format("     %4d     |", confusionMatrix[i][j]));
            }
            Debug.log(row.toString());
            Debug.log(separator.toString());
        }
        
        // Calculate metrics
        double[] precision = new double[numClasses];
        double[] recall = new double[numClasses];
        double[] f1 = new double[numClasses];
        
        for (int i = 0; i < numClasses; i++) {
            // True positives for class i
            int tp = confusionMatrix[i][i];
            
            // Sum of column i (all samples predicted as class i)
            int sumCol = 0;
            for (int j = 0; j < numClasses; j++) {
                sumCol += confusionMatrix[j][i];
            }
            
            // Sum of row i (all samples actually in class i)
            int sumRow = 0;
            for (int j = 0; j < numClasses; j++) {
                sumRow += confusionMatrix[i][j];
            }
            
            // Calculate precision and recall
            precision[i] = (sumCol == 0) ? 0 : (double) tp / sumCol;
            recall[i] = (sumRow == 0) ? 0 : (double) tp / sumRow;
            
            // Calculate F1 score
            f1[i] = (precision[i] + recall[i] == 0) ? 0 : 2 * precision[i] * recall[i] / (precision[i] + recall[i]);
        }
        
        // Log per-class metrics with class names
        Debug.log("\nPer-class Metrics:");
        for (int i = 0; i < numClasses; i++) {
            String className = indexToClassName.getOrDefault(i, "Index " + i);
            Debug.log(String.format("Class '%s' (Index %d) - Precision: %.4f, Recall: %.4f, F1: %.4f",
                    className, i, precision[i], recall[i], f1[i]));
        }
        
        // Calculate overall metrics
        double accuracy = 0;
        double macroAvgPrecision = 0;
        double macroAvgRecall = 0;
        double macroAvgF1 = 0;
        
        int totalCorrect = 0;
        for (int i = 0; i < numClasses; i++) {
            totalCorrect += confusionMatrix[i][i]; // Sum of diagonal (correct predictions)
            macroAvgPrecision += precision[i];
            macroAvgRecall += recall[i];
            macroAvgF1 += f1[i];
        }
        
        accuracy = (double) totalCorrect / numSamples;
        macroAvgPrecision /= numClasses;
        macroAvgRecall /= numClasses;
        macroAvgF1 /= numClasses;
        
        // Log overall metrics
        Debug.log("\nOverall Metrics:");
        Debug.log(String.format("Accuracy: %.4f", accuracy));
        Debug.log(String.format("Macro-avg Precision: %.4f", macroAvgPrecision));
        Debug.log(String.format("Macro-avg Recall: %.4f", macroAvgRecall));
        Debug.log(String.format("Macro-avg F1: %.4f", macroAvgF1));
        
        // Return metrics in a map
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("accuracy", accuracy);
        metrics.put("precision", macroAvgPrecision);
        metrics.put("recall", macroAvgRecall);
        metrics.put("f1", macroAvgF1);
        
        return metrics;
    }
    
    /**
     * Perform k-fold cross-validation
     * 
     * @param data Complete dataset in DMatrix format
     * @param params XGBoost parameters
     * @param numRounds Number of training rounds
     * @param kFolds Number of folds for cross-validation
     * @return Average accuracy across all folds
     */
    public double performKFoldCV(DMatrix data, Map<String, Object> params, int numRounds, int kFolds) {
        // TODO: Implement k-fold cross-validation
        return 0.0;
    }
    
    /**
     * Calculate and display feature importance
     * 
     * @param featureNames List of feature names
     * @return Map of feature names to importance scores
     */
    public Map<String, Integer> calculateFeatureImportance(String[] featureNames) {
        Map<String, Integer> importance = new HashMap<>();
        
        try {
            Debug.log("Calculating feature importance...");
            
            // Get feature importance
            importance = model.getFeatureScore(featureNames);
            
            // If feature importance is empty, try another method
            if (importance.isEmpty()) {
                // Get feature importance using dump model
                String[] modelDump = model.getModelDump((String)null, false);
                
                // Parse the model dump to extract feature importance
                Map<String, Integer> featureCount = new HashMap<>();
                for (String feature : featureNames) {
                    featureCount.put(feature, 0);
                }
                
                for (String tree : modelDump) {
                    for (String feature : featureNames) {
                        // Count occurrences of feature in the model dump
                        int lastIndex = 0;
                        int count = 0;
                        while (lastIndex != -1) {
                            lastIndex = tree.indexOf("[" + feature + "<", lastIndex);
                            if (lastIndex != -1) {
                                count++;
                                lastIndex += feature.length();
                            }
                        }
                        featureCount.put(feature, featureCount.get(feature) + count);
                    }
                }
                
                importance = featureCount;
            }
            
            // Display feature importance
            StringBuilder sb = new StringBuilder();
            sb.append("Feature\tImportance\n");
            
            // Sort features by importance
            importance.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n"));
            
            TextWindow tw = new TextWindow("XGBoost Feature Importance",
                "Feature Importance (higher values indicate more important features)",
                sb.toString(),
                300, 200);
            
            Debug.log("Feature importance calculated successfully");
            
        } catch (XGBoostError e) {
            Debug.log("XGBoost error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Debug.log("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
        
        return importance;
    }
}