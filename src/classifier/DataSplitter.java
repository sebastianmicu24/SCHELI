package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import com.sebastianmicu.scheli.Debug;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Utility class to:
 * 1. Create a DMatrix containing only selected features for labeled training data.
 * 2. Split this DMatrix into training and testing sets using stratified sampling.
 * 3. Create and apply the label mapping required by XGBoost (based on training data only).
 * 4. Save the label mapping for later use during classification.
 */
public class DataSplitter {

    // Stores original float label to index mapping for XGBoost, created from training data
    private static Map<Float, Integer> labelToIndex = new HashMap<>();
    // Stores index to original float label mapping for reverse lookup if needed
    private static Map<Integer, Float> indexToLabel = new HashMap<>();

    /**
     * Creates the primary DMatrix for training/testing (containing only selected features
     * for labeled cells) and splits it into training and testing sets.
     *
     * @param dataReader The ReadData instance containing all features and training labels.
     * @param trainRatio The ratio of data to use for training (e.g., 0.8 for 80%).
     * @return An array containing [trainingDMatrix, testingDMatrix], or null if an error occurs.
     * @throws XGBoostError If there's an error creating the DMatrix objects.
     */
    public static DMatrix[] createAndSplitTrainingData(ReadData dataReader, float trainRatio) throws XGBoostError {
        Debug.log("--- Preparing and Splitting Training Data ---");

        // 1. Get required data from ReadData
        Map<String, float[]> allFeatures = dataReader.getAllCellFeatures(); // Key: "file:id", Value: full combined feature vector
        Map<String, Float> trainingLabels = dataReader.getTrainingLabels(); // Key: "file:id", Value: original class ID

        if (trainingLabels.isEmpty()) {
            Debug.log("Error: No training labels found in ReadData instance. Cannot create training matrix.");
            return null;
        }
        if (allFeatures.isEmpty()) {
            Debug.log("Error: No features found in ReadData instance. Cannot create training matrix.");
            return null;
        }

        // 2. Get selected feature indices from FeatureManager
        FeatureManager.FeatureSelectionResult featureSelection = FeatureManager.getSelectedFeatures();
        List<Integer> selectedIndices = featureSelection.selectedIndices;
        int numSelectedFeatures = selectedIndices.size();

        if (numSelectedFeatures == 0) {
            Debug.log("Error: No features selected based on current configuration. Cannot train model.");
            return null;
        }
        Debug.log("Using " + numSelectedFeatures + " selected features for training DMatrix.");

        // 3. Prepare data arrays for DMatrix creation (only for labeled cells)
        List<float[]> selectedFeatureDataList = new ArrayList<>();
        List<Float> labelsList = new ArrayList<>();
        List<String> cellKeysForMatrix = new ArrayList<>(); // Keep track of which cells are included

        for (Map.Entry<String, Float> labelEntry : trainingLabels.entrySet()) {
            String cellKey = labelEntry.getKey();
            Float label = labelEntry.getValue();
            float[] fullFeatures = allFeatures.get(cellKey);

            if (fullFeatures != null && label != null) {
                // Extract selected features
                float[] selectedFeatures = new float[numSelectedFeatures];
                int nanCount = 0;
                for (int i = 0; i < numSelectedFeatures; i++) {
                    int originalIndex = selectedIndices.get(i);
                    if (originalIndex < fullFeatures.length) {
                        selectedFeatures[i] = fullFeatures[originalIndex];
                        if(Float.isNaN(selectedFeatures[i])) nanCount++;
                    } else {
                        // This indicates a mismatch between FeatureManager and ReadData feature vector length
                        Debug.log("FATAL ERROR: Selected index " + originalIndex + " out of bounds for cell " + cellKey + " (feature vector length: " + fullFeatures.length + ")");
                        throw new XGBoostError("Feature index mismatch during DMatrix creation.");
                    }
                }
                 if (nanCount > 0) {
                     // Debug.log("Debug: Cell " + cellKey + " has " + nanCount + " NaN values in selected features.");
                 }

                selectedFeatureDataList.add(selectedFeatures);
                labelsList.add(label);
                cellKeysForMatrix.add(cellKey);
            } else {
                 // Debug.log("Warning: Skipping cell " + cellKey + " for training matrix (missing features or label).");
            }
        }

        if (selectedFeatureDataList.isEmpty()) {
            Debug.log("Error: No valid cells with both features and labels found to create training matrix.");
            return null;
        }

        // Convert lists to arrays for DMatrix constructor
        int numRows = selectedFeatureDataList.size();
        float[] finalFeatureData = new float[numRows * numSelectedFeatures];
        float[] finalLabels = new float[numRows];

        for (int i = 0; i < numRows; i++) {
            System.arraycopy(selectedFeatureDataList.get(i), 0, finalFeatureData, i * numSelectedFeatures, numSelectedFeatures);
            finalLabels[i] = labelsList.get(i);
        }

        // 4. Create the single DMatrix containing selected features and original labels
        Debug.log("Creating primary DMatrix for training/testing with " + numRows + " rows and " + numSelectedFeatures + " columns.");
        DMatrix primaryMatrix;
        try {
             primaryMatrix = new DMatrix(finalFeatureData, numRows, numSelectedFeatures, Float.NaN);
             primaryMatrix.setLabel(finalLabels); // Set original labels first
        } catch (XGBoostError e) {
             Debug.log("Error creating primary DMatrix: " + e.getMessage());
             throw e; // Re-throw
        }

        // 5. Split this primary DMatrix
        Debug.log("Splitting data into " + (trainRatio * 100) + "% training and " + ((1 - trainRatio) * 100) + "% testing...");
        float[] originalLabelsForSplit = primaryMatrix.getLabel(); // Get original labels back
        Map<Float, List<Integer>> indicesByClass = new HashMap<>();

        // Group indices by original class label
        for (int i = 0; i < numRows; i++) {
            float label = originalLabelsForSplit[i];
            indicesByClass.computeIfAbsent(label, k -> new ArrayList<>()).add(i);
        }

        // Log class distribution before split
        Debug.log("Class distribution in dataset before split:");
        for (Map.Entry<Float, List<Integer>> entry : indicesByClass.entrySet()) {
            Debug.log("  Original Class ID " + entry.getKey() + ": " + entry.getValue().size() + " samples");
        }

        List<Integer> trainIndices = new ArrayList<>();
        List<Integer> testIndices = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for reproducibility

        // Perform stratified sampling
        for (Map.Entry<Float, List<Integer>> entry : indicesByClass.entrySet()) {
            List<Integer> classIndices = entry.getValue();
            Collections.shuffle(classIndices, random);
            int trainSize = (int) Math.round(classIndices.size() * trainRatio); // Use Math.round for better distribution

            for (int i = 0; i < classIndices.size(); i++) {
                if (i < trainSize) {
                    trainIndices.add(classIndices.get(i));
                } else {
                    testIndices.add(classIndices.get(i));
                }
            }
        }

        // Convert lists to arrays
        int[] trainIndicesArray = trainIndices.stream().mapToInt(i -> i).toArray();
        int[] testIndicesArray = testIndices.stream().mapToInt(i -> i).toArray();

        // 6. Create training DMatrix slice
        DMatrix trainMatrix = primaryMatrix.slice(trainIndicesArray);

        // 7. Create label mapping ONLY from training data
        createLabelMapping(trainMatrix); // Uses original labels present in trainMatrix

        // Log the label mapping
        Debug.log("Label mapping for XGBoost (created from training data only):");
        // Sort by mapped index for clarity
        List<Map.Entry<Float, Integer>> sortedMapping = new ArrayList<>(labelToIndex.entrySet());
        sortedMapping.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (Map.Entry<Float, Integer> entry : sortedMapping) {
            Debug.log("  Original label ID " + entry.getKey() + " -> Mapped index " + entry.getValue());
        }

        // 8. Apply label transformations to training data
        applyLabelTransformation(trainMatrix);

        // 9. Create testing DMatrix slice and apply the same mapping
        DMatrix testMatrix = primaryMatrix.slice(testIndicesArray);
        applyLabelTransformation(testMatrix); // Use mapping derived from training data

        // Log class distribution in train and test sets (using mapped indices)
        logClassDistribution("Training", trainMatrix);
        logClassDistribution("Testing", testMatrix);

        Debug.log("Data split complete. Training set: " + trainMatrix.rowNum() + " samples, Testing set: " + testMatrix.rowNum() + " samples");

        // 10. Save the label mapping to a file for use during classification
        saveLabelMapping(ConfigVariables.getString(ConfigVariables.ConfigKey.PATH_OUTPUT_DIR));

        Debug.log("--- Data Preparation and Splitting Complete ---");
        return new DMatrix[] {trainMatrix, testMatrix};
    }

    /**
     * Creates a label mapping from the training data only to prevent data leakage.
     * Maps original float labels to consecutive zero-based integer indices.
     *
     * @param trainData The training data DMatrix (with original labels).
     * @throws XGBoostError If there's an error accessing the labels.
     */
    private static void createLabelMapping(DMatrix trainData) throws XGBoostError {
        float[] originalLabels = trainData.getLabel();

        // Clear existing mappings
        labelToIndex.clear();
        indexToLabel.clear();

        // Find unique original labels in the training data
        Set<Float> uniqueLabelsSet = new HashSet<>();
        for (float label : originalLabels) {
            uniqueLabelsSet.add(label);
        }

        // Sort unique labels to ensure consistent mapping order
        List<Float> uniqueLabels = new ArrayList<>(uniqueLabelsSet);
        Collections.sort(uniqueLabels);

        // Create the mapping: Original Label -> XGBoost Index (0, 1, 2...)
        for (float label : uniqueLabels) {
             int index = labelToIndex.size(); // Assign index sequentially
             labelToIndex.put(label, index);
             indexToLabel.put(index, label);
        }
    }

    /**
     * Applies the label transformation to a DMatrix using the mapping created from training data.
     * Replaces original labels with the mapped XGBoost indices.
     *
     * @param data The DMatrix to transform labels for.
     * @throws XGBoostError If there's an error accessing or setting the labels.
     */
    private static void applyLabelTransformation(DMatrix data) throws XGBoostError {
        float[] originalLabels = data.getLabel();
        float[] transformedLabels = new float[originalLabels.length];

        for (int i = 0; i < originalLabels.length; i++) {
            // Use the mapping created from training data.
            // If a label from test set wasn't in train set (shouldn't happen with stratified split
            // unless a class had only 1 sample), map it to a default index (e.g., 0) or handle as error.
            // Here, we default to index 0 if unseen, though it's unlikely.
            transformedLabels[i] = labelToIndex.getOrDefault(originalLabels[i], 0);
        }

        // Set the transformed labels (now XGBoost indices)
        data.setLabel(transformedLabels);
    }

    /**
     * Log the class distribution in a dataset (using mapped indices).
     *
     * @param setName Name of the dataset (e.g., "Training", "Testing").
     * @param data The DMatrix to analyze (must have transformed labels).
     * @throws XGBoostError If there's an error accessing the labels.
     */
    private static void logClassDistribution(String setName, DMatrix data) throws XGBoostError {
        float[] mappedLabels = data.getLabel(); // These are the XGBoost indices
        Map<Integer, Integer> counts = new HashMap<>();

        // Count occurrences of each mapped index
        for (float mappedLabelFloat : mappedLabels) {
            int mappedIndex = (int) mappedLabelFloat;
            counts.put(mappedIndex, counts.getOrDefault(mappedIndex, 0) + 1);
        }

        // Log the distribution
        Debug.log(setName + " set class distribution (mapped indices):");
        // Sort by index for clarity
        List<Integer> sortedIndices = new ArrayList<>(counts.keySet());
        Collections.sort(sortedIndices);
        for (int mappedIndex : sortedIndices) {
            int count = counts.get(mappedIndex);
            // Look up original label ID for context
            Float originalLabel = indexToLabel.get(mappedIndex);
            Debug.log("  Mapped Index " + mappedIndex + " (Original ID: " + originalLabel + "): " + count + " samples");
        }
    }

    /**
     * Returns the mapping from original float labels to the zero-based integer indices
     * used by XGBoost. This mapping is created based *only* on the training data.
     *
     * @return An unmodifiable map where keys are original labels and values are mapped indices.
     */
    public static Map<Float, Integer> getLabelToIndexMapping() {
        return Collections.unmodifiableMap(labelToIndex);
    }

     /**
      * Returns the mapping from the zero-based integer indices used by XGBoost
      * back to the original float labels.
      *
      * @return An unmodifiable map where keys are mapped indices and values are original labels.
      */
     public static Map<Integer, Float> getIndexToLabelMapping() {
         return Collections.unmodifiableMap(indexToLabel);
     }


    /**
     * Saves the label mapping (Original Label ID = XGBoost Index) to a properties file.
     * This is crucial for the classification step to correctly interpret model output.
     *
     * @param outputDir The directory where the mapping file should be saved.
     */
    private static void saveLabelMapping(String outputDir) {
        if (labelToIndex.isEmpty()) {
            Debug.log("Warning: Label mapping is empty. Cannot save mapping file.");
            return;
        }
        String mappingPath = outputDir + File.separator + "xgboost_label_mapping.properties";
        Properties props = new Properties();

        for (Map.Entry<Float, Integer> entry : labelToIndex.entrySet()) {
            // Key = Original Label ID (float), Value = Mapped XGBoost Index (int)
            props.setProperty(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }

        try (FileOutputStream fos = new FileOutputStream(mappingPath)) {
            props.store(fos, "XGBoost Label Mapping (Original_Label_ID=XGBoost_Index)");
            Debug.log("Label mapping saved to: " + mappingPath);
        } catch (IOException e) {
            Debug.log("Error saving label mapping: " + e.getMessage());
        }
    }
}