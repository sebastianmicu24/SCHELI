package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import com.sebastianmicu.scheli.Debug;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Classifies new data using a trained XGBoost model.
 * Aligned with the refactored ReadData/FeatureManager approach.
 */
public class ClassifyData {

    // Decimal format will be configured based on locale setting
    private static final String FLOAT_FORMAT_PATTERN = "#.######";
    // Separator determined by config
    private String csvSeparator;
    private char decimalSeparator;

    // Class to store details about each class (name, id, color) loaded from JSON
    private static class ClassDetails {
        final String name;
        final int id;
        final String color;

        ClassDetails(String name, int id, String color) {
            this.name = name;
            this.id = id;
            this.color = color;
        }
    }

    // Map from original class ID to class details (loaded from JSON specified in config)
    private Map<Integer, ClassDetails> classIdToDetails = new HashMap<>();
    // Map from XGBoost index (0, 1, ...) to original class ID (loaded from properties file)
    private Map<Integer, Integer> xgbIndexToClassId = new HashMap<>();
    // Selected features loaded from file saved during training
    private List<String> loadedSelectedFeatureNames;

    /**
     * Classifies all CSV files in a directory using a pre-trained model and its associated files.
     *
     * @param modelPath Path to the saved XGBoost model file (.json).
     * @param selectedFeaturesPath Path to the text file listing features used during training.
     * @param labelMappingPath Path to the properties file mapping original labels to XGBoost indices.
     * @param csvInputDir Path to the directory containing CSV files to classify.
     * @param outputDir Path to the directory where the classification results CSV will be saved.
     * @param jsonClassDetailsPath Path to the JSON file containing class names and colors for output formatting.
     * @throws IOException If there are errors reading files.
     * @throws XGBoostError If there are errors loading the model or predicting.
     */
    public void classifyDirectory(String modelPath, String selectedFeaturesPath, String labelMappingPath, String csvInputDir, String outputDir, String jsonClassDetailsPath) throws IOException, XGBoostError {
        Debug.log("\n--- Starting Classification Workflow ---");
        Debug.log("Model Path: " + modelPath);
        Debug.log("Selected Features Path: " + selectedFeaturesPath);
        Debug.log("Label Mapping Path: " + labelMappingPath);
        Debug.log("CSV Input Directory: " + csvInputDir);
        Debug.log("Output Directory: " + outputDir);
        Debug.log("Class Details JSON Path: " + jsonClassDetailsPath);

        // 1. Load the Trained Model (.json format)
        Booster booster;
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                throw new IOException("Model file not found: " + modelPath);
            }
            booster = XGBoost.loadModel(modelPath);
            Debug.log("XGBoost model loaded successfully from " + modelPath);
        } catch (XGBoostError e) {
            Debug.log("Error loading XGBoost model: " + e.getMessage());
            throw e; // Re-throw to be handled by the caller (ClassifyWorkflowMain)
        }

        // 2. Load the Selected Feature List
        if (!loadSelectedFeatures(selectedFeaturesPath)) {
            throw new IOException("Failed to load the required selected features file: " + selectedFeaturesPath);
        }

        // 3. Load the Label Mapping (XGBoost Index -> Original Class ID)
        if (!loadSavedLabelMapping(labelMappingPath)) {
            throw new IOException("Failed to load the required label mapping file: " + labelMappingPath);
        }

        // 4. Load Class Details (Name, Color) from JSON
        if (!loadClassDetailsFromJSON(jsonClassDetailsPath)) {
             // Log error but maybe proceed with default names/colors? For now, treat as error.
             throw new IOException("Failed to load class details (names/colors) from JSON: " + jsonClassDetailsPath);
        }

        // 5. Read Data to Classify using ReadData
        Debug.log("Reading CSV data to classify from: " + csvInputDir);
        ReadData dataReader = new ReadData(csvInputDir, null); // Pass null for JSON path (no training labels needed)
        Map<String, float[]> allFeaturesMap = dataReader.getAllCellFeatures(); // Key: "file:id", Value: full combined feature vector
        List<String> combinedHeaders = dataReader.getCombinedHeaders(); // Get COMBINED headers (e.g., Nucleus_Area)

        if (allFeaturesMap.isEmpty()) {
            Debug.log("Warning: No valid cells with combined features found in the input CSV directory: " + csvInputDir);
            IJ.showMessage("Classification Info", "No valid cell data found in the input directory:\n" + csvInputDir);
            return; // Nothing to classify
        }
        Debug.log("Found " + allFeaturesMap.size() + " cells with combined features to classify.");

        // 6. Prepare Data for DMatrix (extract selected features based on loaded list)
        List<String> cellKeysForMatrix = new ArrayList<>(allFeaturesMap.keySet());
        Collections.sort(cellKeysForMatrix); // Ensure consistent order

        // Create a mapping from the original header names to their indices
        Map<String, Integer> headerToIndexMap = new HashMap<>();
        if (combinedHeaders != null) {
            for (int i = 0; i < combinedHeaders.size(); i++) {
                headerToIndexMap.put(combinedHeaders.get(i), i);
            }
        } else {
             throw new IOException("Could not retrieve combined headers from ReadData. Cannot map selected features.");
        }

        // Find the indices corresponding to the loaded selected feature names
        List<Integer> indicesToExtract = new ArrayList<>();
        for (String featureName : this.loadedSelectedFeatureNames) {
            Integer index = headerToIndexMap.get(featureName);
            if (index != null) {
                indicesToExtract.add(index);
            } else {
                throw new IOException("Feature '" + featureName + "' listed in selected_features.txt was not found in the combined headers generated by ReadData.");
            }
        }
        Debug.log("Mapped " + indicesToExtract.size() + " loaded feature names to indices in combined headers.");

        int numRows = cellKeysForMatrix.size();
        int numCols = indicesToExtract.size(); // Use size of extracted indices
        float[] classificationData = new float[numRows * numCols];
        int nanCountInData = 0;

        for (int i = 0; i < numRows; i++) {
            String cellKey = cellKeysForMatrix.get(i);
            float[] fullFeatures = allFeaturesMap.get(cellKey);
            if (fullFeatures == null) {
                 Debug.log("Warning: Null features found for cell key: " + cellKey + ". Skipping row " + i);
                 Arrays.fill(classificationData, i * numCols, (i + 1) * numCols, Float.NaN);
                 nanCountInData += numCols;
                 continue;
            }

            for (int j = 0; j < numCols; j++) {
                int originalIndex = indicesToExtract.get(j); // Get index from the mapped list
                if (originalIndex < fullFeatures.length) {
                    float val = fullFeatures[originalIndex];
                    classificationData[i * numCols + j] = val;
                    if (Float.isNaN(val)) nanCountInData++;
                } else {
                    // This indicates a mismatch between ReadData headers and feature vector length
                    Debug.log("FATAL ERROR: Mapped index " + originalIndex + " (for feature '" + this.loadedSelectedFeatureNames.get(j) + "') is out of bounds for combined feature vector of cell " + cellKey + " (vector length: " + fullFeatures.length + ")");
                    throw new IOException("Feature index mismatch during classification DMatrix creation.");
                }
            }
        }
        if (nanCountInData > 0) {
             Debug.log("Warning: Input data for classification contains " + nanCountInData + " NaN values (" + String.format("%.2f%%", 100.0 * nanCountInData / classificationData.length) + "). XGBoost might handle these.");
        }

        // 7. Create DMatrix for Classification
        DMatrix classificationMatrix;
        try {
            Debug.log("Creating DMatrix for classification (" + numRows + " rows, " + numCols + " columns)...");
            classificationMatrix = new DMatrix(classificationData, numRows, numCols, Float.NaN);
        } catch (XGBoostError e) {
            Debug.log("Error creating DMatrix for classification: " + e.getMessage());
            throw e;
        }

        // 8. Classify using the Loaded Model
        float[][] predictions;
        try {
            Debug.log("Predicting classes...");
            predictions = booster.predict(classificationMatrix);

             // Sanity check prediction dimensions
            if (predictions.length != numRows) {
                 throw new XGBoostError("Prediction output rows (" + predictions.length + ") does not match input rows (" + numRows + ").");
            }
            if (predictions.length > 0 && predictions[0].length != xgbIndexToClassId.size()) {
                 throw new XGBoostError("Prediction output columns (" + predictions[0].length + ") does not match number of classes in mapping (" + xgbIndexToClassId.size() + ").");
            }

        } catch (XGBoostError e) {
            Debug.log("Error during prediction: " + e.getMessage());
            throw e;
        }

        // 9. Determine Output Format (using ConfigVariables)
        boolean useEuFormat = ConfigVariables.getBoolean(ConfigKey.USE_EU_FORMAT);
        this.csvSeparator = useEuFormat ? ";" : ",";
        this.decimalSeparator = useEuFormat ? ',' : '.';
        Debug.log("Output CSV Format: " + (useEuFormat ? "EU (Separator=';', Decimal=',')": "US (Separator=',', Decimal='.')"));

        // 10. Write Results to Output File
        String outputFilePath = outputDir + File.separator + "classification_results.csv";
        Debug.log("Writing classification results to: " + outputFilePath);
        try {
            // Pass the combined headers for writing
            writeClassificationResults(outputFilePath, cellKeysForMatrix, allFeaturesMap, combinedHeaders, predictions, this.csvSeparator, this.decimalSeparator);
            Debug.log("Successfully wrote classification results.");
        } catch (IOException e) {
             Debug.log("Error writing classification results: " + e.getMessage());
             throw e;
        }

        Debug.log("--- Classification Workflow Completed ---");
    }

    // --- Helper Methods ---

    /**
     * Loads the saved label mapping (XGBoost Index -> Original Class ID) from the properties file.
     * @param mappingPath Path to the xgboost_label_mapping.properties file.
     * @return true if loading was successful, false otherwise.
     */
    private boolean loadSavedLabelMapping(String mappingPath) {
        Properties props = new Properties();
        File mappingFile = new File(mappingPath);
        if (!mappingFile.exists()) {
            Debug.log("Error: Label mapping file not found: " + mappingPath);
            return false;
        }
        try (FileInputStream fis = new FileInputStream(mappingFile)) {
            props.load(fis);
            xgbIndexToClassId.clear();
            for (String originalLabelStr : props.stringPropertyNames()) {
                try {
                    // Properties file stores: Original_Label_ID=XGBoost_Index
                    float originalLabel = Float.parseFloat(originalLabelStr);
                    int xgbIndex = Integer.parseInt(props.getProperty(originalLabelStr));
                    xgbIndexToClassId.put(xgbIndex, (int)originalLabel); // Store: Index -> Original ID
                } catch (NumberFormatException e) {
                    Debug.log("Warning: Could not parse mapping entry in " + mappingPath + ": " + originalLabelStr + "=" + props.getProperty(originalLabelStr));
                }
            }
            Debug.log("Loaded label mapping (XGBoost Index -> Original Class ID) from " + mappingPath + " for " + xgbIndexToClassId.size() + " classes.");
            // Removed detailed logging of the mapping itself for production
            if (xgbIndexToClassId.isEmpty() && props.size() > 0) {
                 Debug.log("Warning: Properties file loaded but resulted in empty mapping. Check file format/content.");
                 return false;
            }
             if (xgbIndexToClassId.isEmpty() && props.size() == 0) {
                 Debug.log("Warning: Properties file seems empty. No mapping loaded.");
                 return false;
            }
            return true;
        } catch (IOException e) {
            Debug.log("Error loading label mapping file '" + mappingPath + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads the list of selected feature names from the specified file.
     * Populates the loadedSelectedFeatureNames list.
     * @param featuresPath Path to the selected_features.txt file.
     * @return true if loading was successful, false otherwise.
     */
    private boolean loadSelectedFeatures(String featuresPath) {
        File featuresFile = new File(featuresPath);
        if (!featuresFile.exists()) {
            Debug.log("Error: Selected features file not found: " + featuresPath);
            return false;
        }
        try {
            this.loadedSelectedFeatureNames = Files.readAllLines(Paths.get(featuresPath), StandardCharsets.UTF_8);
            // Remove empty lines if any
            this.loadedSelectedFeatureNames.removeIf(String::isEmpty);
            if (this.loadedSelectedFeatureNames.isEmpty()) {
                Debug.log("Error: Selected features file is empty: " + featuresPath);
                return false;
            }
            Debug.log("Loaded " + this.loadedSelectedFeatureNames.size() + " selected feature names from " + featuresPath);
            return true;
        } catch (IOException e) {
             Debug.log("Error loading selected features file '" + featuresPath + "': " + e.getMessage());
             return false;
        }
    }

    /**
     * Loads class details (name, color) from the specified JSON file.
     * Populates the classIdToDetails map.
     * @param jsonPath Path to the JSON file (typically the same one used for training).
     * @return true if loading was successful, false otherwise.
     */
    private boolean loadClassDetailsFromJSON(String jsonPath) {
        Debug.log("Loading class details from JSON: " + jsonPath);
        classIdToDetails.clear(); // Clear previous details

        try (BufferedReader br = new BufferedReader(new FileReader(jsonPath))) {
            String json = br.lines().collect(Collectors.joining());
            int classesIndex = json.indexOf("\"classes\":");
            if (classesIndex == -1) { Debug.log("No 'classes' section in JSON."); return false; }

            int classesStart = json.indexOf("{", classesIndex);
            int classesEnd = findMatchingBrace(json, classesStart);
            if (classesStart == -1 || classesEnd == -1) { Debug.log("Malformed 'classes' section in JSON."); return false; }
            String classesContent = json.substring(classesStart + 1, classesEnd);

            Pattern classPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{(.*?)\\}(,|$)", Pattern.DOTALL);
            Matcher classMatcher = classPattern.matcher(classesContent);

            while (classMatcher.find()) {
                String className = classMatcher.group(1).trim();
                String classContent = classMatcher.group(2);

                Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(classContent);
                Matcher colorMatcher = Pattern.compile("\"color\"\\s*:\\s*\"([^\"]+)\"").matcher(classContent);

                if (idMatcher.find() && colorMatcher.find()) {
                    int classId = Integer.parseInt(idMatcher.group(1).trim());
                    String color = colorMatcher.group(1).trim();
                    classIdToDetails.put(classId, new ClassDetails(className, classId, color));
                } else {
                     Debug.log("Warning: Class '" + className + "' in JSON is missing 'id' or 'color'.");
                }
            }
            Debug.log("Loaded details for " + classIdToDetails.size() + " classes from JSON.");
            return !classIdToDetails.isEmpty();

        } catch (IOException e) {
            Debug.log("Error loading class details from JSON '" + jsonPath + "': " + e.getMessage());
            return false;
        }
    }


    /**
     * Writes classification results, including probabilities and original features, to a CSV file.
     *
     * @param outputPath Path for the output CSV file.
     * @param cellKeys The ordered list of cell keys ("file:id") corresponding to the prediction rows.
     * @param allFeaturesMap The map containing the full original feature vectors for each cell.
     * @param combinedHeaders The list of combined feature names (e.g., "Nucleus_Area").
     * @param predictions The raw prediction output from the XGBoost model (rows=cells, cols=classes).
     * @param csvSeparator The separator character (e.g., ';' or ',').
     * @param decimalSeparator The decimal separator character (e.g., ',' or '.').
     * @throws IOException If an error occurs during file writing.
     */
    private void writeClassificationResults(String outputPath, List<String> cellKeys, Map<String, float[]> allFeaturesMap, List<String> combinedHeaders, float[][] predictions, String csvSeparator, char decimalSeparator) throws IOException {
        // Configure DecimalFormat based on the required separator
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US); // Start with US locale (dot decimal) as base
        symbols.setDecimalSeparator(decimalSeparator);
        DecimalFormat df = new DecimalFormat(FLOAT_FORMAT_PATTERN, symbols);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // --- Write Header ---
            List<String> header = new ArrayList<>();
            // Replace "Cell_Key" with the new columns
            header.add("Original_File");
            header.add("ID");
            header.add("Border");
            header.add("Predicted_Class_Index"); // XGBoost index (0, 1, ...)
            header.add("Predicted_Class_Name");
            header.add("Predicted_Class_ID");   // Original ID from JSON
            header.add("Predicted_Class_Color");
            header.add("Max_Probability");

            // Add probability columns for each class, ordered by XGBoost index
            List<Integer> sortedXgbIndices = new ArrayList<>(xgbIndexToClassId.keySet());
            Collections.sort(sortedXgbIndices);
            for (int xgbIndex : sortedXgbIndices) {
                int classId = xgbIndexToClassId.getOrDefault(xgbIndex, -1);
                ClassDetails details = classIdToDetails.get(classId);
                String className = (details != null) ? details.name : "UnknownIndex" + xgbIndex;
                header.add("Prob_" + className.replaceAll("[^a-zA-Z0-9_]", "")); // Sanitize name for header
            }

            // Add all original feature columns using the headers read from input CSVs
            // Ensure these headers are consistent across input files (handled by ReadData)
            if (combinedHeaders != null) {
                 header.addAll(combinedHeaders); // Add the combined headers
            } else {
                 Debug.log("Warning: Combined headers were null, cannot add feature columns to output CSV header.");
                 // Consider adding a placeholder or throwing an error?
            }
            // Alternative: Add headers as they were in the input CSV?
            // header.addAll(originalHeaders); // This might be less clear than the N/C/Cell prefixed version

            writer.write(String.join(csvSeparator, header));
            writer.newLine();

            // --- Write Data Rows ---
            for (int i = 0; i < cellKeys.size(); i++) {
                String cellKey = cellKeys.get(i);
                float[] probs = predictions[i];
                float[] fullFeatures = allFeaturesMap.get(cellKey); // Get original combined features

                if (fullFeatures == null) continue; // Skip if features were missing

                // Find predicted class (highest probability)
                int predictedIndex = 0;
                float maxProb = -1.0f; // Initialize lower than possible probability
                 if (probs != null && probs.length > 0) {
                     maxProb = probs[0];
                     for (int j = 1; j < probs.length; j++) {
                         if (probs[j] > maxProb) {
                             maxProb = probs[j];
                             predictedIndex = j;
                         }
                     }
                 } else {
                      Debug.log("Warning: Null or empty probability array for cell " + cellKey + ". Assigning default prediction (index 0 or -1).");
                      predictedIndex = xgbIndexToClassId.isEmpty() ? -1 : 0; // Assign index 0 if possible, else -1
                      maxProb = Float.NaN;
                 }


                // Get original class ID and details using the loaded mapping
                int predictedClassId = xgbIndexToClassId.getOrDefault(predictedIndex, -1); // Default to -1 if index invalid
                ClassDetails details = classIdToDetails.get(predictedClassId); // May be null if ID invalid

                // Build row data
                List<String> row = new ArrayList<>();

                // --- Parse cellKey ---
                String originalFile = "UnknownFile";
                String idPart = "UnknownID";
                boolean isBorder = false;

                int separatorIndex = cellKey.lastIndexOf(":");
                if (separatorIndex != -1) {
                    String filePart = cellKey.substring(0, separatorIndex);
                    idPart = cellKey.substring(separatorIndex + 1);

                    int dataCsvIndex = filePart.lastIndexOf("_data.csv");
                    if (dataCsvIndex != -1) {
                        originalFile = filePart.substring(0, dataCsvIndex);
                    } else {
                        originalFile = filePart; // Fallback if pattern mismatch
                    }

                    if (idPart.endsWith("_Border")) {
                        isBorder = true;
                        idPart = idPart.substring(0, idPart.lastIndexOf("_Border"));
                    }
                } else {
                     idPart = cellKey; // Fallback if no colon
                }
                // --- End Parse cellKey ---

                // Add parsed components instead of original cellKey
                row.add(originalFile);
                row.add(idPart);
                row.add(String.valueOf(isBorder));

                row.add(String.valueOf(predictedIndex));
                row.add(details != null ? details.name : "Unknown");
                row.add(details != null ? String.valueOf(details.id) : "Unknown");
                row.add(details != null ? details.color : "#000000");
                row.add(Float.isNaN(maxProb) ? "NaN" : df.format(maxProb));

                // Add probabilities in the correct order (matching header)
                for (int xgbIndex : sortedXgbIndices) {
                     // Check probs is not null AND index is valid
                     if (probs != null && xgbIndex >= 0 && xgbIndex < probs.length) {
                         row.add(df.format(probs[xgbIndex]));
                     } else {
                         row.add("NaN"); // Should not happen if dimensions match
                     }
                }

                // Add all original feature values
                for (float featureVal : fullFeatures) {
                    // Use the configured DecimalFormat for features too
                    row.add(Float.isNaN(featureVal) ? "NaN" : df.format(featureVal));
                }

                writer.write(String.join(csvSeparator, row));
                writer.newLine();
            }
        }
    }

    // Helper method to find the matching closing brace (from ReadData)
    private int findMatchingBrace(String s, int start) {
        int count = 1;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') count++;
            else if (c == '}') count--;
            if (count == 0) return i;
        }
        return -1; // Indicate not found
    }
}
