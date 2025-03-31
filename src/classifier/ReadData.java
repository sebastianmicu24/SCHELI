package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.sebastianmicu.scheli.Debug;



/**
 * Loads and preprocesses data for training and classification.
 * Reads all features from CSVs and ground truth labels from JSON.
 * Provides access to the full feature set and labels, allowing downstream
 * components (DataSplitter, ClassifyData) to perform feature selection
 * and DMatrix creation consistently.
 */
public class ReadData {

    // Separator and decimal format determined by config
    private String csvSeparator;
    private boolean useEuFormat; // true for comma decimal, false for dot

    private final String dataDirectory;
    private final String jsonFilePath; // Only used for training labels

    // Stores the full combined feature vector for EVERY cell found in CSVs
    private Map<String, float[]> allCellFeatures = new HashMap<>();

    // Stores the original label ID for cells designated in the JSON file
    private Map<String, Float> trainingLabels = new HashMap<>();

    // Stores the mapping from original class name to original class ID (from JSON)
    private Map<String, Integer> classNameToIdMap = new HashMap<>();

    // Stores the original headers from the CSV (excluding "ROI")
    private List<String> originalHeaders = new ArrayList<>(); // Headers as read from CSV (e.g., "Area")
    private List<String> combinedHeaders = new ArrayList<>(); // Combined headers (e.g., "Nucleus_Area", "Cytoplasm_Area", "Cell_Area")

    /**
     * Constructor. Loads data based on provided paths.
     * @param dataDirectory Path to the directory containing CSV feature files.
     * @param jsonFilePath Path to the JSON file containing ground truth labels (used only for training). Can be null or empty if only classifying.
     */
    public ReadData(String dataDirectory, String jsonFilePath) {
        this.dataDirectory = dataDirectory;
        this.jsonFilePath = jsonFilePath;

        // Determine CSV format based on configuration
        this.useEuFormat = ConfigVariables.getBoolean(ConfigVariables.ConfigKey.USE_EU_FORMAT);
        this.csvSeparator = useEuFormat ? ";" : ",";
        Debug.log("CSV Format: " + (useEuFormat ? "EU (Separator=';', Decimal=',')": "US (Separator=',', Decimal='.')"));

        Debug.log("--- Initializing ReadData ---");
        Debug.log("CSV Data Directory: " + dataDirectory);
        Debug.log("Ground Truth JSON: " + (jsonFilePath != null && !jsonFilePath.isEmpty() ? jsonFilePath : "Not provided (classification mode?)"));

        // 1. Load all features from CSVs
        processAllCSVs();

        // 2. Load training labels if JSON path is provided
        if (this.jsonFilePath != null && !this.jsonFilePath.isEmpty()) {
            loadTrainingLabelsFromJson();
        } else {
            Debug.log("Skipping JSON label loading.");
        }

        logSummary();
        Debug.log("--- ReadData Initialization Complete ---");
    }

    // --- Getters for Downstream Use ---

    /**
     * Gets the map containing the full combined feature vector for all cells found in the CSVs.
     * @return Map where Key is "filename.csv:cellId" and Value is float[] of all features.
     */
    public Map<String, float[]> getAllCellFeatures() {
        return Collections.unmodifiableMap(allCellFeatures);
    }

    /**
     * Gets the map containing the original class ID labels for cells specified in the JSON file.
     * @return Map where Key is "filename.csv:cellId" and Value is the original class ID as a Float.
     */
    public Map<String, Float> getTrainingLabels() {
        return Collections.unmodifiableMap(trainingLabels);
    }

    /**
     * Gets the mapping from class name to original class ID, loaded from the JSON.
     * @return Map where Key is class name (String) and Value is original class ID (Integer).
     */
    public Map<String, Integer> getClassNameToIdMap() {
        return Collections.unmodifiableMap(classNameToIdMap);
    }

    /**
     * Gets the names of the features selected by the FeatureManager based on the current configuration.
     * The order matches the columns in the DMatrix created by DataSplitter.
     * @return An unmodifiable list of selected feature names (e.g., "Nucleus_Area").
     */
    public List<String> getSelectedFeatureNames() {
        // Calculate fresh in case config changed, though ideally config is fixed for a run
        FeatureManager.FeatureSelectionResult selection = FeatureManager.getSelectedFeatures();
        return selection.selectedFeatureNames;
    }


    /**
     * Gets the original headers read from the CSV files (excluding the first "ROI" column).
     * The order matches the feature arrays stored in the allCellFeatures map.
     * @return An unmodifiable list of header strings.
     */
    public List<String> getOriginalHeaders() {
        return Collections.unmodifiableList(originalHeaders);
    }

    /**
     * Gets the combined headers corresponding to the full feature vector structure
     * (e.g., "Nucleus_Area", "Cytoplasm_Area", "Cell_Area").
     * This list is generated once based on the original headers.
     * @return An unmodifiable list of combined header strings.
     */
    public List<String> getCombinedHeaders() {
        return Collections.unmodifiableList(combinedHeaders);
    }


    // --- Data Loading Logic ---

    /**
     * Processes all CSV files in the data directory, reads features, combines them,
     * and stores the full feature vector for each cell.
     */
    private void processAllCSVs() {
        File dir = new File(dataDirectory);
        if (!dir.exists() || !dir.isDirectory()) {
            Debug.log("Error: CSV data directory not found: " + dataDirectory);
            return;
        }

        File[] csvFiles = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            Debug.log("Warning: No CSV files found in directory: " + dataDirectory);
            return;
        }

        boolean headersInitialized = false;
        int featuresPerSegment = FeatureManager.getFeaturesPerSegment(); // Get expected length

        for (File file : csvFiles) {
            Map<String, Map<String, float[]>> fileCellGroups = new HashMap<>(); // CellID -> Component -> Features
            Map<String, Integer> currentFileHeaderIndices = new HashMap<>();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                // Read header
                String headerLine = br.readLine();
                if (headerLine == null) {
                    Debug.log("Warning: Skipping empty CSV file: " + file.getName());
                    continue;
                }

                String[] headers = headerLine.split(this.csvSeparator);
                if (!headersInitialized) {
                    // Initialize originalHeaders and headerIndices from the first valid file
                    for (int i = 1; i < headers.length; i++) { // Skip ROI
                        originalHeaders.add(headers[i].trim());
                    }
                    if (originalHeaders.size() != featuresPerSegment) {
                         Debug.log("FATAL ERROR: CSV header count (" + originalHeaders.size() + ") does not match FeatureManager expected count (" + featuresPerSegment + "). Check CSV format and FeatureManager.java.");
                         // Consider throwing an exception or halting
                         return; // Stop processing
                    }
                    headersInitialized = true;
                    Debug.log("Initialized headers from " + file.getName() + ". Expecting " + featuresPerSegment + " features per segment.");
                    // Generate combined headers once
                    generateCombinedHeaders();
                }
                 // Map headers for the current file (needed for combining)
                for (int i = 1; i < headers.length; i++) {
                    currentFileHeaderIndices.put(headers[i].trim(), i - 1); // index in the feature array (value part)
                }


                // Read data rows
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(this.csvSeparator, -1); // Keep trailing empty strings
                    if (parts.length < 2) continue;

                    String rawId = parts[0].trim();
                    if (rawId.startsWith("Vessel_")) continue; // Skip vessel rows

                    int idx = rawId.indexOf('_');
                    if (idx == -1) continue;

                    String component = rawId.substring(0, idx);
                    String cellId = rawId.substring(idx + 1);

                    if (!component.equals("Nucleus") && !component.equals("Cytoplasm") && !component.equals("Cell")) {
                        continue;
                    }

                    // Parse features for this component row
                    float[] feats = new float[originalHeaders.size()]; // Use size from initialized headers
                    Arrays.fill(feats, Float.NaN); // Default to NaN
                    for (int i = 1; i < parts.length; i++) {
                        if (i - 1 < feats.length) { // Check bounds
                            String valueStr = parts[i].trim();
                            try {
                                // Replace decimal comma with dot ONLY if using EU format
                                if (useEuFormat) {
                                    valueStr = valueStr.replace(',', '.');
                                }
                                feats[i - 1] = Float.parseFloat(valueStr);
                            } catch (NumberFormatException e) {
                                feats[i - 1] = Float.NaN; // Keep as NaN on parse error
                            }
                        }
                    }
                    // Store features for this component of this cell
                    fileCellGroups.computeIfAbsent(cellId, k -> new HashMap<>()).put(component, feats);
                }

                // Combine features for cells in this file
                combineAndStoreFeatures(file.getName(), fileCellGroups, currentFileHeaderIndices);

            } catch (IOException e) {
                Debug.log("Error reading CSV " + file.getName() + ": " + e.getMessage());
            }
        }
         Debug.log("Finished processing all CSVs. Total cells with combined features: " + allCellFeatures.size());
    }

    /**
     * Combines features from Nucleus, Cytoplasm, and Cell components for each cell
     * and stores the full combined vector in the allCellFeatures map.
     *
     * @param filename The name of the CSV file being processed.
     * @param fileCellGroups Map of CellID -> Component -> Features for the current file.
     * @param headerIndices Map of Header Name -> Index for the current file.
     */
    private void combineAndStoreFeatures(String filename, Map<String, Map<String, float[]>> fileCellGroups, Map<String, Integer> headerIndices) {
        int featuresPerSegment = FeatureManager.getFeaturesPerSegment();
        String[] fixedOrder = FeatureManager.getFixedFeatureOrder(); // Get the canonical order

        for (Map.Entry<String, Map<String, float[]>> entry : fileCellGroups.entrySet()) {
            String cellId = entry.getKey();
            Map<String, float[]> components = entry.getValue();

            // Only proceed if all three components are present
            if (components.containsKey("Nucleus") && components.containsKey("Cytoplasm") && components.containsKey("Cell")) {
                float[] nucleusFeatures = components.get("Nucleus");
                float[] cytoplasmFeatures = components.get("Cytoplasm");
                float[] cellFeatures = components.get("Cell");

                // Create the combined feature array (N+C+Cell)
                float[] combined = new float[featuresPerSegment * 3];
                Arrays.fill(combined, Float.NaN); // Initialize with NaN

                // Fill the combined array based on the fixed order
                for (int i = 0; i < featuresPerSegment; i++) {
                    String featureName = fixedOrder[i];
                    Integer featureIndexInCSV = headerIndices.get(featureName); // Get index from *this file's* header map

                    if (featureIndexInCSV != null) {
                        // Check bounds before accessing component arrays
                        if (featureIndexInCSV < nucleusFeatures.length) {
                            combined[i] = nucleusFeatures[featureIndexInCSV];
                        }
                        if (featureIndexInCSV < cytoplasmFeatures.length) {
                            combined[i + featuresPerSegment] = cytoplasmFeatures[featureIndexInCSV];
                        }
                        if (featureIndexInCSV < cellFeatures.length) {
                            combined[i + 2 * featuresPerSegment] = cellFeatures[featureIndexInCSV];
                        }
                    } else {
                        // This should not happen if headers were checked correctly, but log if it does
                         Debug.log("Warning: Feature '" + featureName + "' not found in header map for cell " + cellId + " in file " + filename);
                    }
                }

                // Store the full combined feature vector
                String mapKey = filename + ":" + cellId;
                allCellFeatures.put(mapKey, combined);
            } else {
                 }
        }
    }

    /**
     * Generates the combined headers list (e.g., "Nucleus_Area", "Cytoplasm_Area", "Cell_Area")
     * based on the original headers read from the first CSV.
     * Should be called only once after originalHeaders is populated.
     */
    private void generateCombinedHeaders() {
        if (!combinedHeaders.isEmpty() || originalHeaders.isEmpty()) {
            return; // Already generated or no original headers yet
        }
        String[] prefixes = {"Nucleus", "Cytoplasm", "Cell"};
        for (String prefix : prefixes) {
            for (String header : originalHeaders) {
                combinedHeaders.add(prefix + "_" + header);
            }
        }
        Debug.log("Generated " + combinedHeaders.size() + " combined headers.");
    }


    /**
     * Loads the ground truth labels from the specified JSON file.
     * Populates the trainingLabels map.
     */
    private void loadTrainingLabelsFromJson() {
        Debug.log("Loading training labels from JSON: " + jsonFilePath);
        Map<String, String> cellFileToClassName = new HashMap<>(); // Temp map: "filename.csv:cellId" -> "ClassName"

        try (BufferedReader br = new BufferedReader(new FileReader(jsonFilePath))) {
            String json = br.lines().collect(Collectors.joining());
            int classesIndex = json.indexOf("\"classes\":");
            if (classesIndex == -1) { Debug.log("No 'classes' section in JSON."); return; }

            int classesStart = json.indexOf("{", classesIndex);
            int classesEnd = findMatchingBrace(json, classesStart);
            if (classesStart == -1 || classesEnd == -1) { Debug.log("Malformed 'classes' section in JSON."); return; }
            String classesContent = json.substring(classesStart + 1, classesEnd);

            Pattern classPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{(.*?)\\}(,|$)", Pattern.DOTALL);
            Matcher classMatcher = classPattern.matcher(classesContent);

            while (classMatcher.find()) {
                String className = classMatcher.group(1).trim();
                String classContent = classMatcher.group(2);

                Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(classContent);
                int classId = idMatcher.find() ? Integer.parseInt(idMatcher.group(1).trim()) : -1;

                if (classId != -1) {
                    classNameToIdMap.put(className, classId); // Store Name -> ID mapping

                    Matcher particlesMatcher = Pattern.compile("\"particles\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(classContent);
                    if (particlesMatcher.find()) {
                        String particlesContent = particlesMatcher.group(1);
                        Matcher particleEntryMatcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(particlesContent);
                        while (particleEntryMatcher.find()) {
                            String fileName = particleEntryMatcher.group(1).trim();
                            String[] cellIds = particleEntryMatcher.group(2).split(",");
                            for (String cellIdStr : cellIds) {
                                cellIdStr = cellIdStr.replaceAll("\"", "").trim();
                                if (!cellIdStr.isEmpty()) {
                                    String mapKey = fileName + ":" + cellIdStr;
                                    cellFileToClassName.put(mapKey, className);
                                }
                            }
                        }
                    }
                } else {
                     Debug.log("Warning: Class '" + className + "' in JSON is missing an 'id'.");
                }
            }

            // Convert class names to class IDs for the trainingLabels map
            int labelsFound = 0;
            for (Map.Entry<String, String> entry : cellFileToClassName.entrySet()) {
                String mapKey = entry.getKey(); // Key generated from JSON
                String className = entry.getValue();
                Integer classId = classNameToIdMap.get(className);

                if (classId != null) {
                    // IMPORTANT: Only add label if the cell *also* exists in the allCellFeatures map (i.e., was found in CSVs)
                    if (allCellFeatures.containsKey(mapKey)) {
                        trainingLabels.put(mapKey, classId.floatValue());
                        trainingLabels.put(mapKey, classId.floatValue());
                        labelsFound++;
                    } else {
                         // Debug.log("Debug: Cell " + mapKey + " from JSON not found in processed CSV features."); // Keep commented out unless needed
                    }
                    // No need for explicit else, debug logging above shows misses
                } else {
                    // Should not happen if classNameToIdMap is populated correctly
                    Debug.log("Warning: Could not find ID for class name '" + className + "' referenced by cell " + mapKey);
                }
            }
             Debug.log("Loaded " + labelsFound + " training labels from JSON corresponding to cells found in CSVs.");


        } catch (IOException e) {
            Debug.log("Error loading JSON labels: " + e.getMessage());
        }
    }

    /**
     * Logs a summary of the loaded data.
     */
    private void logSummary() {
        Debug.log("--- ReadData Summary ---");
        Debug.log("Total cells with full features loaded from CSVs: " + allCellFeatures.size());
        Debug.log("Total training labels loaded from JSON (matching CSV cells): " + trainingLabels.size());
        Debug.log("Number of unique classes defined in JSON: " + classNameToIdMap.size());
        if (!trainingLabels.isEmpty()) {
            Map<Float, Long> labelCounts = trainingLabels.values().stream()
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
            Debug.log("Training label distribution:");
            labelCounts.forEach((label, count) -> {
                // Find class name for this label ID
                String name = classNameToIdMap.entrySet().stream()
                                .filter(entry -> entry.getValue().intValue() == label.intValue())
                                .map(Map.Entry::getKey)
                                .findFirst().orElse("Unknown ID " + label.intValue());
                Debug.log("  Class '" + name + "' (ID: " + label.intValue() + "): " + count + " samples");
            });
        }
        Debug.log("------------------------");
    }

    // --- Utility methods ---
    private int findMatchingBrace(String s, int start) {
        int count = 1;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') count++;
            if (c == '}') count--;
            if (count == 0) return i;
        }
        return -1; // Indicate not found
    }
}
