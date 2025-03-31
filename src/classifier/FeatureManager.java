package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey;
import com.sebastianmicu.scheli.Debug;
import java.util.*;

/**
 * Manages the definition and selection of features used in the classifier.
 * Ensures consistency between training and classification phases.
 */
public class FeatureManager {

    // Use LinkedHashMap to maintain insertion order, which corresponds to CSV column order (after ROI)
    private static final Map<String, String> FEATURE_TO_ENUM_MAP = new LinkedHashMap<>();
    private static final String[] FIXED_FEATURE_ORDER;
    private static final int FEATURES_PER_SEGMENT;

    static {
        // Populate the map in the exact order they appear in the CSV header (excluding ROI)
        FEATURE_TO_ENUM_MAP.put("Vessel Distance", "VESSEL_DISTANCE");
        FEATURE_TO_ENUM_MAP.put("Closest Vessel", null); // No config key for this string feature
        FEATURE_TO_ENUM_MAP.put("Neighbor Count", "NEIGHBOR_COUNT");
        FEATURE_TO_ENUM_MAP.put("Closest Neighbor Distance", "CLOSEST_NEIGHBOR_DISTANCE");
        FEATURE_TO_ENUM_MAP.put("Closest Neighbor", null); // No config key for this string feature
        FEATURE_TO_ENUM_MAP.put("Area", "AREA");
        FEATURE_TO_ENUM_MAP.put("X", null); // Coordinate, typically not used as feature
        FEATURE_TO_ENUM_MAP.put("Y", null); // Coordinate, typically not used as feature
        FEATURE_TO_ENUM_MAP.put("XM", null); // Coordinate, typically not used as feature
        FEATURE_TO_ENUM_MAP.put("YM", null); // Coordinate, typically not used as feature
        FEATURE_TO_ENUM_MAP.put("Perim.", "PERIM");
        FEATURE_TO_ENUM_MAP.put("BX", null); // Coordinate, typically not used as feature
        FEATURE_TO_ENUM_MAP.put("BY", null); // Coordinate, typically not used as feature
        FEATURE_TO_ENUM_MAP.put("Width", "WIDTH");
        FEATURE_TO_ENUM_MAP.put("Height", "HEIGHT");
        FEATURE_TO_ENUM_MAP.put("Major", "MAJOR");
        FEATURE_TO_ENUM_MAP.put("Minor", "MINOR");
        FEATURE_TO_ENUM_MAP.put("Angle", "ANGLE");
        FEATURE_TO_ENUM_MAP.put("Circ.", "CIRC");
        FEATURE_TO_ENUM_MAP.put("IntDen", "INTDEN");
        FEATURE_TO_ENUM_MAP.put("Feret", "FERET");
        FEATURE_TO_ENUM_MAP.put("FeretX", "FERETX");
        FEATURE_TO_ENUM_MAP.put("FeretY", "FERETY");
        FEATURE_TO_ENUM_MAP.put("FeretAngle", "FERETANGLE");
        FEATURE_TO_ENUM_MAP.put("MinFeret", "MINFERET");
        FEATURE_TO_ENUM_MAP.put("AR", "AR");
        FEATURE_TO_ENUM_MAP.put("Round", "ROUND");
        FEATURE_TO_ENUM_MAP.put("Solidity", "SOLIDITY");
        FEATURE_TO_ENUM_MAP.put("Mean", "MEAN");
        FEATURE_TO_ENUM_MAP.put("StdDev", "STDDEV");
        FEATURE_TO_ENUM_MAP.put("Mode", "MODE");
        FEATURE_TO_ENUM_MAP.put("Min", "MIN");
        FEATURE_TO_ENUM_MAP.put("Max", "MAX");
        FEATURE_TO_ENUM_MAP.put("Median", "MEDIAN");
        FEATURE_TO_ENUM_MAP.put("Skew", "SKEW");
        FEATURE_TO_ENUM_MAP.put("Kurt", "KURT");
        FEATURE_TO_ENUM_MAP.put("Hema_Mean", "HEMA_MEAN");
        FEATURE_TO_ENUM_MAP.put("Hema_StdDev", "HEMA_STDDEV");
        FEATURE_TO_ENUM_MAP.put("Hema_Mode", "HEMA_MODE");
        FEATURE_TO_ENUM_MAP.put("Hema_Min", "HEMA_MIN");
        FEATURE_TO_ENUM_MAP.put("Hema_Max", "HEMA_MAX");
        FEATURE_TO_ENUM_MAP.put("Hema_Median", "HEMA_MEDIAN");
        FEATURE_TO_ENUM_MAP.put("Hema_Skew", "HEMA_SKEW");
        FEATURE_TO_ENUM_MAP.put("Hema_Kurt", "HEMA_KURT");
        FEATURE_TO_ENUM_MAP.put("Eosin_Mean", "EOSIN_MEAN");
        FEATURE_TO_ENUM_MAP.put("Eosin_StdDev", "EOSIN_STDDEV");
        FEATURE_TO_ENUM_MAP.put("Eosin_Mode", "EOSIN_MODE");
        FEATURE_TO_ENUM_MAP.put("Eosin_Min", "EOSIN_MIN");
        FEATURE_TO_ENUM_MAP.put("Eosin_Max", "EOSIN_MAX");
        FEATURE_TO_ENUM_MAP.put("Eosin_Median", "EOSIN_MEDIAN");
        FEATURE_TO_ENUM_MAP.put("Eosin_Skew", "EOSIN_SKEW");
        FEATURE_TO_ENUM_MAP.put("Eosin_Kurt", "EOSIN_KURT");

        FIXED_FEATURE_ORDER = FEATURE_TO_ENUM_MAP.keySet().toArray(new String[0]);
        FEATURES_PER_SEGMENT = FIXED_FEATURE_ORDER.length;

        // Sanity check: Ensure all feature ConfigKeys exist
        for (Map.Entry<String, String> entry : FEATURE_TO_ENUM_MAP.entrySet()) {
            String baseEnumName = entry.getValue();
            if (baseEnumName != null) {
                try {
                    ConfigKey.valueOf("FEATURE_NUCLEUS_" + baseEnumName);
                    ConfigKey.valueOf("FEATURE_CYTOPLASM_" + baseEnumName);
                    ConfigKey.valueOf("FEATURE_CELL_" + baseEnumName);
                } catch (IllegalArgumentException e) {
                    Debug.log("FATAL ERROR in FeatureManager: ConfigKey mapping error for feature '" + entry.getKey() + "' (Base: " + baseEnumName + "). Check ConfigVariables.java. " + e.getMessage());
                    // Consider throwing an exception here to halt execution
                }
            }
        }
         Debug.log("[FeatureManager] Initialized with " + FEATURES_PER_SEGMENT + " features per segment.");
    }

    /**
     * Gets the fixed order of feature names as they appear in the CSV columns (after ROI).
     * @return An array of feature names.
     */
    public static String[] getFixedFeatureOrder() {
        return Arrays.copyOf(FIXED_FEATURE_ORDER, FIXED_FEATURE_ORDER.length);
    }

    /**
     * Gets the number of features expected per segment (Nucleus, Cytoplasm, Cell).
     * @return The number of features per segment.
     */
    public static int getFeaturesPerSegment() {
        return FEATURES_PER_SEGMENT;
    }

    /**
     * Result class to hold both selected indices and their corresponding names.
     */
    public static class FeatureSelectionResult {
        public final List<Integer> selectedIndices; // Indices relative to the combined (N+C+Cell) feature vector
        public final List<String> selectedFeatureNames; // Names like "Nucleus_Area", "Cytoplasm_Mean"

        FeatureSelectionResult(List<Integer> indices, List<String> names) {
            this.selectedIndices = Collections.unmodifiableList(indices);
            this.selectedFeatureNames = Collections.unmodifiableList(names);
        }
    }

    /**
     * Determines which features are selected based on the current ConfigVariables settings.
     *
     * @return A FeatureSelectionResult containing the indices and names of selected features.
     */
    public static FeatureSelectionResult getSelectedFeatures() {
        // Use TreeMap to ensure indices are sorted correctly before creating the final lists
        Map<Integer, String> selectedFeaturesMap = new TreeMap<>();

        for (int i = 0; i < FEATURES_PER_SEGMENT; i++) {
            String featureName = FIXED_FEATURE_ORDER[i];
            String baseEnumName = FEATURE_TO_ENUM_MAP.get(featureName);

            // Skip features that don't have a corresponding ConfigKey (e.g., coordinates, string IDs)
            if (baseEnumName == null) {
                continue;
            }

            // Check Nucleus feature
            checkAndAddFeature(selectedFeaturesMap, i, "Nucleus", featureName, baseEnumName);

            // Check Cytoplasm feature
            checkAndAddFeature(selectedFeaturesMap, i + FEATURES_PER_SEGMENT, "Cytoplasm", featureName, baseEnumName);

            // Check Cell feature
            checkAndAddFeature(selectedFeaturesMap, i + 2 * FEATURES_PER_SEGMENT, "Cell", featureName, baseEnumName);
        }

        // Extract sorted indices and corresponding names from the map
        List<Integer> finalIndices = new ArrayList<>(selectedFeaturesMap.keySet());
        List<String> finalNames = new ArrayList<>(selectedFeaturesMap.values());

        Debug.log("[FeatureManager] Calculated selection: " + finalIndices.size() + " features selected.");
        // Optional: Log the first few selected features for debugging
        // for(int k=0; k < Math.min(5, finalIndices.size()); k++) {
        //     Debug.log("  - " + finalNames.get(k) + " (Index: " + finalIndices.get(k) + ")");
        // }

        return new FeatureSelectionResult(finalIndices, finalNames);
    }

    /**
     * Helper method to check if a specific feature (e.g., Nucleus_Area) is enabled
     * in ConfigVariables and add it to the selection map if it is.
     *
     * @param selectionMap The map to add the feature to (Index -> Name).
     * @param index The index of this feature in the combined feature vector.
     * @param segmentPrefix "Nucleus", "Cytoplasm", or "Cell".
     * @param featureName The base name of the feature (e.g., "Area").
     * @param baseEnumName The corresponding part of the ConfigKey enum name (e.g., "AREA").
     */
    private static void checkAndAddFeature(Map<Integer, String> selectionMap, int index, String segmentPrefix, String featureName, String baseEnumName) {
        String configKeyName = "FEATURE_" + segmentPrefix.toUpperCase() + "_" + baseEnumName;
        try {
            ConfigKey key = ConfigKey.valueOf(configKeyName);
            if (ConfigVariables.getBoolean(key)) {
                selectionMap.put(index, segmentPrefix + "_" + featureName);
            }
        } catch (IllegalArgumentException e) {
            // This should not happen due to the static initializer check, but log just in case.
            Debug.log("Warning in FeatureManager: ConfigKey '" + configKeyName + "' not found during selection check.");
        }
    }
}