package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ij.Prefs;
import com.sebastianmicu.scheli.Debug;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration variables for the XGBoost classifier part of SCHELI.
 * Handles loading, saving, and accessing all classifier settings using a more concise Enum/Map approach.
 *
 * @author Cristian Sebastian Micu (Original Author), Refactored by Roo
 */
public class ConfigVariables {

    // --- Configuration Key Enum ---
    public enum ConfigKey {
        // Path settings
        PATH_CSV_DATA("he_liver_class.path_csv_data", ""),
        PATH_CLASSIFIED_CELLS("he_liver_class.path_classified_cells", ""),
        PATH_OUTPUT_DIR("he_liver_class.path_output_dir", "C:\\Users\\sebas\\Desktop\\test\\OUTPUT\\individual"), // Added Output Dir
        PATH_JSON_DATA("he_liver_class.path_json_data", "C:\\Users\\sebas\\Downloads\\cell_classification.json"), // Added JSON Path
        PATH_MODEL_FILE("he_liver_class.path_model_file", ""), // Path to the saved model file (.bin) for classification
        // Classification/Training settings
        // NUM_CLASSES removed as requested
        TRAIN_RATIO("he_liver_class.train_ratio", 0.8f), // Added Train Ratio

        // XGBoost parameters
        LEARNING_RATE("he_liver_class.learning_rate", 0.1f),
        MAX_DEPTH("he_liver_class.max_depth", 6),
        N_TREES("he_liver_class.n_trees", 200),
        MIN_CHILD_WEIGHT("he_liver_class.min_child_weight", 2),
        CELLS_SUBSAMPLE("he_liver_class.cells_subsample", 0.8f),
        FEATURES_SUBSAMPLE("he_liver_class.features_subsample", 0.8f),
        LAMBDA("he_liver_class.lambda", 1.0f),
        ALPHA("he_liver_class.alpha", 1.0f),
        GAMMA("he_liver_class.gamma", 1.0f),
        BALANCE_CLASSES("he_liver_class.balance_classes", false),
        
        // CSV format settings
        USE_EU_FORMAT("he_liver_class.use_eu_format", true), // true = EU format (semicolon separator, comma decimal), false = US format (comma separator, dot decimal)

        // --- Feature selection keys ---
        // Default for all features is true (enabled)
        // Cell features
        FEATURE_CELL_VESSEL_DISTANCE("he_liver_class.feature_cell_vessel_distance", true),
        FEATURE_CELL_NEIGHBOR_COUNT("he_liver_class.feature_cell_neighbor_count", true),
        FEATURE_CELL_CLOSEST_NEIGHBOR_DISTANCE("he_liver_class.feature_cell_closest_neighbor_distance", true),
        FEATURE_CELL_AREA("he_liver_class.feature_cell_area", true),
        FEATURE_CELL_PERIM("he_liver_class.feature_cell_perim", true),
        FEATURE_CELL_WIDTH("he_liver_class.feature_cell_width", true),
        FEATURE_CELL_HEIGHT("he_liver_class.feature_cell_height", true),
        FEATURE_CELL_MAJOR("he_liver_class.feature_cell_major", true),
        FEATURE_CELL_MINOR("he_liver_class.feature_cell_minor", true),
        FEATURE_CELL_ANGLE("he_liver_class.feature_cell_angle", true),
        FEATURE_CELL_CIRC("he_liver_class.feature_cell_circ", true),
        FEATURE_CELL_INTDEN("he_liver_class.feature_cell_intden", true),
        FEATURE_CELL_FERET("he_liver_class.feature_cell_feret", true),
        FEATURE_CELL_FERETX("he_liver_class.feature_cell_feretx", true),
        FEATURE_CELL_FERETY("he_liver_class.feature_cell_ferety", true),
        FEATURE_CELL_FERETANGLE("he_liver_class.feature_cell_feretangle", true),
        FEATURE_CELL_MINFERET("he_liver_class.feature_cell_minferet", true),
        FEATURE_CELL_AR("he_liver_class.feature_cell_ar", true),
        FEATURE_CELL_ROUND("he_liver_class.feature_cell_round", true),
        FEATURE_CELL_SOLIDITY("he_liver_class.feature_cell_solidity", true),
        FEATURE_CELL_MEAN("he_liver_class.feature_cell_mean", true),
        FEATURE_CELL_STDDEV("he_liver_class.feature_cell_stddev", true),
        FEATURE_CELL_MODE("he_liver_class.feature_cell_mode", true),
        FEATURE_CELL_MIN("he_liver_class.feature_cell_min", true),
        FEATURE_CELL_MAX("he_liver_class.feature_cell_max", true),
        FEATURE_CELL_MEDIAN("he_liver_class.feature_cell_median", true),
        FEATURE_CELL_SKEW("he_liver_class.feature_cell_skew", true),
        FEATURE_CELL_KURT("he_liver_class.feature_cell_kurt", true),
        FEATURE_CELL_HEMA_MEAN("he_liver_class.feature_cell_hema_mean", true),
        FEATURE_CELL_HEMA_STDDEV("he_liver_class.feature_cell_hema_stddev", true),
        FEATURE_CELL_HEMA_MODE("he_liver_class.feature_cell_hema_mode", true),
        FEATURE_CELL_HEMA_MIN("he_liver_class.feature_cell_hema_min", true),
        FEATURE_CELL_HEMA_MAX("he_liver_class.feature_cell_hema_max", true),
        FEATURE_CELL_HEMA_MEDIAN("he_liver_class.feature_cell_hema_median", true),
        FEATURE_CELL_HEMA_SKEW("he_liver_class.feature_cell_hema_skew", true),
        FEATURE_CELL_HEMA_KURT("he_liver_class.feature_cell_hema_kurt", true),
        FEATURE_CELL_EOSIN_MEAN("he_liver_class.feature_cell_eosin_mean", true),
        FEATURE_CELL_EOSIN_STDDEV("he_liver_class.feature_cell_eosin_stddev", true),
        FEATURE_CELL_EOSIN_MODE("he_liver_class.feature_cell_eosin_mode", true),
        FEATURE_CELL_EOSIN_MIN("he_liver_class.feature_cell_eosin_min", true),
        FEATURE_CELL_EOSIN_MAX("he_liver_class.feature_cell_eosin_max", true),
        FEATURE_CELL_EOSIN_MEDIAN("he_liver_class.feature_cell_eosin_median", true),
        FEATURE_CELL_EOSIN_SKEW("he_liver_class.feature_cell_eosin_skew", true),
        FEATURE_CELL_EOSIN_KURT("he_liver_class.feature_cell_eosin_kurt", true),

        // Nucleus features
        FEATURE_NUCLEUS_VESSEL_DISTANCE("he_liver_class.feature_nucleus_vessel_distance", true),
        FEATURE_NUCLEUS_NEIGHBOR_COUNT("he_liver_class.feature_nucleus_neighbor_count", true),
        FEATURE_NUCLEUS_CLOSEST_NEIGHBOR_DISTANCE("he_liver_class.feature_nucleus_closest_neighbor_distance", true),
        FEATURE_NUCLEUS_AREA("he_liver_class.feature_nucleus_area", true),
        FEATURE_NUCLEUS_PERIM("he_liver_class.feature_nucleus_perim", true),
        FEATURE_NUCLEUS_WIDTH("he_liver_class.feature_nucleus_width", true),
        FEATURE_NUCLEUS_HEIGHT("he_liver_class.feature_nucleus_height", true),
        FEATURE_NUCLEUS_MAJOR("he_liver_class.feature_nucleus_major", true),
        FEATURE_NUCLEUS_MINOR("he_liver_class.feature_nucleus_minor", true),
        FEATURE_NUCLEUS_ANGLE("he_liver_class.feature_nucleus_angle", true),
        FEATURE_NUCLEUS_CIRC("he_liver_class.feature_nucleus_circ", true),
        FEATURE_NUCLEUS_INTDEN("he_liver_class.feature_nucleus_intden", true),
        FEATURE_NUCLEUS_FERET("he_liver_class.feature_nucleus_feret", true),
        FEATURE_NUCLEUS_FERETX("he_liver_class.feature_nucleus_feretx", true),
        FEATURE_NUCLEUS_FERETY("he_liver_class.feature_nucleus_ferety", true),
        FEATURE_NUCLEUS_FERETANGLE("he_liver_class.feature_nucleus_feretangle", true),
        FEATURE_NUCLEUS_MINFERET("he_liver_class.feature_nucleus_minferet", true),
        FEATURE_NUCLEUS_AR("he_liver_class.feature_nucleus_ar", true),
        FEATURE_NUCLEUS_ROUND("he_liver_class.feature_nucleus_round", true),
        FEATURE_NUCLEUS_SOLIDITY("he_liver_class.feature_nucleus_solidity", true),
        FEATURE_NUCLEUS_MEAN("he_liver_class.feature_nucleus_mean", true),
        FEATURE_NUCLEUS_STDDEV("he_liver_class.feature_nucleus_stddev", true),
        FEATURE_NUCLEUS_MODE("he_liver_class.feature_nucleus_mode", true),
        FEATURE_NUCLEUS_MIN("he_liver_class.feature_nucleus_min", true),
        FEATURE_NUCLEUS_MAX("he_liver_class.feature_nucleus_max", true),
        FEATURE_NUCLEUS_MEDIAN("he_liver_class.feature_nucleus_median", true),
        FEATURE_NUCLEUS_SKEW("he_liver_class.feature_nucleus_skew", true),
        FEATURE_NUCLEUS_KURT("he_liver_class.feature_nucleus_kurt", true),
        FEATURE_NUCLEUS_HEMA_MEAN("he_liver_class.feature_nucleus_hema_mean", true),
        FEATURE_NUCLEUS_HEMA_STDDEV("he_liver_class.feature_nucleus_hema_stddev", true),
        FEATURE_NUCLEUS_HEMA_MODE("he_liver_class.feature_nucleus_hema_mode", true),
        FEATURE_NUCLEUS_HEMA_MIN("he_liver_class.feature_nucleus_hema_min", true),
        FEATURE_NUCLEUS_HEMA_MAX("he_liver_class.feature_nucleus_hema_max", true),
        FEATURE_NUCLEUS_HEMA_MEDIAN("he_liver_class.feature_nucleus_hema_median", true),
        FEATURE_NUCLEUS_HEMA_SKEW("he_liver_class.feature_nucleus_hema_skew", true),
        FEATURE_NUCLEUS_HEMA_KURT("he_liver_class.feature_nucleus_hema_kurt", true),
        FEATURE_NUCLEUS_EOSIN_MEAN("he_liver_class.feature_nucleus_eosin_mean", true),
        FEATURE_NUCLEUS_EOSIN_STDDEV("he_liver_class.feature_nucleus_eosin_stddev", true),
        FEATURE_NUCLEUS_EOSIN_MODE("he_liver_class.feature_nucleus_eosin_mode", true),
        FEATURE_NUCLEUS_EOSIN_MIN("he_liver_class.feature_nucleus_eosin_min", true),
        FEATURE_NUCLEUS_EOSIN_MAX("he_liver_class.feature_nucleus_eosin_max", true),
        FEATURE_NUCLEUS_EOSIN_MEDIAN("he_liver_class.feature_nucleus_eosin_median", true),
        FEATURE_NUCLEUS_EOSIN_SKEW("he_liver_class.feature_nucleus_eosin_skew", true),
        FEATURE_NUCLEUS_EOSIN_KURT("he_liver_class.feature_nucleus_eosin_kurt", true),

        // Cytoplasm features
        FEATURE_CYTOPLASM_VESSEL_DISTANCE("he_liver_class.feature_cytoplasm_vessel_distance", true),
        FEATURE_CYTOPLASM_NEIGHBOR_COUNT("he_liver_class.feature_cytoplasm_neighbor_count", true),
        FEATURE_CYTOPLASM_CLOSEST_NEIGHBOR_DISTANCE("he_liver_class.feature_cytoplasm_closest_neighbor_distance", true),
        FEATURE_CYTOPLASM_AREA("he_liver_class.feature_cytoplasm_area", true),
        FEATURE_CYTOPLASM_PERIM("he_liver_class.feature_cytoplasm_perim", true),
        FEATURE_CYTOPLASM_WIDTH("he_liver_class.feature_cytoplasm_width", true),
        FEATURE_CYTOPLASM_HEIGHT("he_liver_class.feature_cytoplasm_height", true),
        FEATURE_CYTOPLASM_MAJOR("he_liver_class.feature_cytoplasm_major", true),
        FEATURE_CYTOPLASM_MINOR("he_liver_class.feature_cytoplasm_minor", true),
        FEATURE_CYTOPLASM_ANGLE("he_liver_class.feature_cytoplasm_angle", true),
        FEATURE_CYTOPLASM_CIRC("he_liver_class.feature_cytoplasm_circ", true),
        FEATURE_CYTOPLASM_INTDEN("he_liver_class.feature_cytoplasm_intden", true),
        FEATURE_CYTOPLASM_FERET("he_liver_class.feature_cytoplasm_feret", true),
        FEATURE_CYTOPLASM_FERETX("he_liver_class.feature_cytoplasm_feretx", true),
        FEATURE_CYTOPLASM_FERETY("he_liver_class.feature_cytoplasm_ferety", true),
        FEATURE_CYTOPLASM_FERETANGLE("he_liver_class.feature_cytoplasm_feretangle", true),
        FEATURE_CYTOPLASM_MINFERET("he_liver_class.feature_cytoplasm_minferet", true),
        FEATURE_CYTOPLASM_AR("he_liver_class.feature_cytoplasm_ar", true),
        FEATURE_CYTOPLASM_ROUND("he_liver_class.feature_cytoplasm_round", true),
        FEATURE_CYTOPLASM_SOLIDITY("he_liver_class.feature_cytoplasm_solidity", true),
        FEATURE_CYTOPLASM_MEAN("he_liver_class.feature_cytoplasm_mean", true),
        FEATURE_CYTOPLASM_STDDEV("he_liver_class.feature_cytoplasm_stddev", true),
        FEATURE_CYTOPLASM_MODE("he_liver_class.feature_cytoplasm_mode", true),
        FEATURE_CYTOPLASM_MIN("he_liver_class.feature_cytoplasm_min", true),
        FEATURE_CYTOPLASM_MAX("he_liver_class.feature_cytoplasm_max", true),
        FEATURE_CYTOPLASM_MEDIAN("he_liver_class.feature_cytoplasm_median", true),
        FEATURE_CYTOPLASM_SKEW("he_liver_class.feature_cytoplasm_skew", true),
        FEATURE_CYTOPLASM_KURT("he_liver_class.feature_cytoplasm_kurt", true),
        FEATURE_CYTOPLASM_HEMA_MEAN("he_liver_class.feature_cytoplasm_hema_mean", true),
        FEATURE_CYTOPLASM_HEMA_STDDEV("he_liver_class.feature_cytoplasm_hema_stddev", true),
        FEATURE_CYTOPLASM_HEMA_MODE("he_liver_class.feature_cytoplasm_hema_mode", true),
        FEATURE_CYTOPLASM_HEMA_MIN("he_liver_class.feature_cytoplasm_hema_min", true),
        FEATURE_CYTOPLASM_HEMA_MAX("he_liver_class.feature_cytoplasm_hema_max", true),
        FEATURE_CYTOPLASM_HEMA_MEDIAN("he_liver_class.feature_cytoplasm_hema_median", true),
        FEATURE_CYTOPLASM_HEMA_SKEW("he_liver_class.feature_cytoplasm_hema_skew", true),
        FEATURE_CYTOPLASM_HEMA_KURT("he_liver_class.feature_cytoplasm_hema_kurt", true),
        FEATURE_CYTOPLASM_EOSIN_MEAN("he_liver_class.feature_cytoplasm_eosin_mean", true),
        FEATURE_CYTOPLASM_EOSIN_STDDEV("he_liver_class.feature_cytoplasm_eosin_stddev", true),
        FEATURE_CYTOPLASM_EOSIN_MODE("he_liver_class.feature_cytoplasm_eosin_mode", true),
        FEATURE_CYTOPLASM_EOSIN_MIN("he_liver_class.feature_cytoplasm_eosin_min", true),
        FEATURE_CYTOPLASM_EOSIN_MAX("he_liver_class.feature_cytoplasm_eosin_max", true),
        FEATURE_CYTOPLASM_EOSIN_MEDIAN("he_liver_class.feature_cytoplasm_eosin_median", true),
        FEATURE_CYTOPLASM_EOSIN_SKEW("he_liver_class.feature_cytoplasm_eosin_skew", true),
        FEATURE_CYTOPLASM_EOSIN_KURT("he_liver_class.feature_cytoplasm_eosin_kurt", true)

        // Note: Add more keys here if needed in the future
        ; // End of enum constants

        private final String keyString;
        private final Object defaultValue;

        ConfigKey(String keyString, Object defaultValue) {
            this.keyString = keyString;
            this.defaultValue = defaultValue;
        }

        public String getKeyString() {
            return keyString;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        // Helper methods to get typed default values safely
        public String getDefaultString() { return (String) defaultValue; }
        public int getDefaultInt() { return (Integer) defaultValue; }
        public float getDefaultFloat() { return (Float) defaultValue; }
        public boolean getDefaultBoolean() { return (Boolean) defaultValue; }
    }

    // --- Class Members ---
    private static final String PROPERTIES_FILE = System.getProperty("user.home") + File.separator + ".he_liver_class.properties";
    private static Properties properties = new Properties();
    private static Map<ConfigKey, Object> currentValues = new EnumMap<>(ConfigKey.class);

    // Load preferences when class is loaded
    static {
        loadPreferences();
    }

    // --- Core Methods (Refactored) ---

    /**
     * Loads preferences from the properties file or ImageJ Prefs.
     * Iterates through all defined ConfigKeys.
     */
    public static synchronized void loadPreferences() {
        // Clear current values before loading
        currentValues.clear();
        properties.clear(); // Clear loaded properties as well

        boolean loadedFromProps = false;
        File propsFile = new File(PROPERTIES_FILE);
        if (propsFile.exists() && propsFile.isFile()) {
            try (FileInputStream in = new FileInputStream(propsFile)) {
                properties.load(in);
                loadedFromProps = true;
            } catch (IOException e) {
                Debug.log("Error loading properties file '" + PROPERTIES_FILE + "': " + e.getMessage());
                // Continue to try loading from Prefs or defaults
            }
        }

        for (ConfigKey key : ConfigKey.values()) {
            Object value = null;
            String keyString = key.getKeyString();
            Object defaultValue = key.getDefaultValue();

            if (loadedFromProps) {
                String propValue = properties.getProperty(keyString);
                if (propValue != null) {
                    try {
                        // Parse based on the type of the default value
                        if (defaultValue instanceof String) value = propValue;
                        else if (defaultValue instanceof Integer) value = Integer.parseInt(propValue);
                        else if (defaultValue instanceof Float) value = Float.parseFloat(propValue);
                        else if (defaultValue instanceof Boolean) value = Boolean.parseBoolean(propValue);
                        else value = defaultValue; // Fallback for unknown types
                    } catch (NumberFormatException e) {
                         Debug.log("Error parsing property for key " + keyString + ": '" + propValue + "'. Using default.");
                         value = defaultValue; // Use default on parse error
                    }
                } else {
                    value = defaultValue; // Use default if key missing in props file
                }
            } else {
                // Load from ImageJ Prefs as fallback if props file didn't exist or failed to load
                try {
                    if (defaultValue instanceof String) value = Prefs.get(keyString, key.getDefaultString());
                    else if (defaultValue instanceof Integer) value = Prefs.getInt(keyString, key.getDefaultInt());
                    // Prefs uses double for float storage
                    else if (defaultValue instanceof Float) value = (float)Prefs.getDouble(keyString, key.getDefaultFloat());
                    else if (defaultValue instanceof Boolean) value = Prefs.getBoolean(keyString, key.getDefaultBoolean());
                    else value = defaultValue; // Fallback for unknown types
                } catch (Exception e) {
                    Debug.log("Error reading ImageJ Pref for key " + keyString + ". Using default. Error: " + e.getMessage());
                    value = defaultValue;
                }
            }

            // Ensure value is not null before putting (should only happen if default is null)
            if (value == null) {
                Debug.log("Warning: Value for key " + keyString + " resolved to null. Using default.");
                value = defaultValue;
            }
            currentValues.put(key, value);
        }
         // Debug.log("Preferences loaded. Source: " + (loadedFromProps ? "Properties File" : "ImageJ Prefs/Defaults"));
    }

    /**
     * Saves all current preferences to both the properties file and ImageJ Prefs.
     * Iterates through all defined ConfigKeys.
     */
    public static synchronized void savePreferences() {
         try {
            // Prepare properties object from current values
            Properties propsToSave = new Properties();
            for (ConfigKey key : ConfigKey.values()) {
                String keyString = key.getKeyString();
                Object value = currentValues.get(key); // Get current value from map

                // Ensure value is not null before saving
                if (value == null) {
                    Debug.log("Warning: Trying to save null value for key " + keyString + ". Using default instead.");
                    value = key.getDefaultValue();
                    if (value == null) { // If default is also null, skip saving this key
                         Debug.log("Error: Default value for key " + keyString + " is also null. Skipping save.");
                         continue;
                    }
                }

                // Save to ImageJ Prefs
                if (value instanceof String) Prefs.set(keyString, (String)value);
                else if (value instanceof Integer) Prefs.set(keyString, (Integer)value);
                // Prefs uses double for float storage
                else if (value instanceof Float) Prefs.set(keyString, (Float)value);
                else if (value instanceof Boolean) Prefs.set(keyString, (Boolean)value);
                // Add handling for other types if necessary

                // Add to properties object for file saving
                propsToSave.setProperty(keyString, String.valueOf(value));
            }

            // Save ImageJ Prefs to disk (ImageJ handles the actual writing)
            Prefs.savePreferences();
             // Force preferences to be written to disk immediately (optional, might impact performance)
             // java.util.prefs.Preferences.userRoot().flush(); // Consider if this is needed

            // Save Properties object to file
            try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
                propsToSave.store(out, "SCHELI Classifier Settings");
            }
            // Debug.log("Preferences saved.");

        } catch (Exception e) {
            // Log error but don't crash the application
            Debug.log("Error saving preferences: " + e.getMessage());
            IJ.handleException(e); // Use standard ImageJ exception handling
        }
    }

    /**
     * Resets all configuration values to their defaults in memory,
     * saves these defaults to persistent storage, and deletes the properties file.
     */
    public static synchronized void resetAllPreferences() {
        currentValues.clear();
        for (ConfigKey key : ConfigKey.values()) {
            currentValues.put(key, key.getDefaultValue());
        }
        Debug.log("All preferences reset to defaults in memory.");

        // Save the reset defaults back to persistence
        savePreferences();
        Debug.log("Default preferences saved to persistence.");

         // Delete properties file if it exists
        try {
            File propsFile = new File(PROPERTIES_FILE);
            if (propsFile.exists()) {
                if (propsFile.delete()) {
                    Debug.log("Properties file deleted: " + PROPERTIES_FILE);
                } else {
                     Debug.log("Could not delete properties file: " + PROPERTIES_FILE);
                }
            }
        } catch (SecurityException e) {
            Debug.log("Error deleting properties file (SecurityException): " + e.getMessage());
        } catch (Exception e) {
             Debug.log("Error deleting properties file: " + e.getMessage());
        }
    }

    /**
     * Prints all current configuration values to the ImageJ log.
     */
     public static synchronized void printAllValues() {
        Debug.log("--- Classification Configuration Values ---");
        // Sort keys for consistent output? Optional.
        // List<ConfigKey> sortedKeys = Arrays.asList(ConfigKey.values());
        // Collections.sort(sortedKeys, Comparator.comparing(Enum::name));
        // for (ConfigKey key : sortedKeys) {
        for (ConfigKey key : ConfigKey.values()) {
             // Make the output more readable, maybe group by type or prefix?
             String readableName = key.name() // FEATURE_CELL_AREA
                 .replaceFirst("^FEATURE_", "") // CELL_AREA
                 .replace('_', ' ') // CELL AREA
                 .toLowerCase(); // cell area
             // Capitalize first letter?
             readableName = Character.toUpperCase(readableName.charAt(0)) + readableName.substring(1);

             Object value = currentValues.get(key);
             Debug.log(readableName + " (" + key.name() + "): " + (value != null ? value.toString() : "null"));
        }
         Debug.log("-----------------------------------------");
    }

    // --- Generic Accessors ---

    /**
     * Gets the current value for the given configuration key.
     * Returns the default value if the key is not found (shouldn't happen with EnumMap).
     *
     * @param key The ConfigKey enum constant.
     * @return The current value as an Object.
     */
    public static synchronized Object getValue(ConfigKey key) {
        // Default value is handled during loading if missing
        return currentValues.get(key);
    }

    /**
     * Sets the current value for the given configuration key and saves all preferences.
     *
     * @param key   The ConfigKey enum constant.
     * @param value The new value to set. Should match the type expected for the key.
     */
    public static synchronized void setValue(ConfigKey key, Object value) {
        if (key == null || value == null) {
             Debug.log("Error: Attempted to set null key or value in ConfigVariables.");
             return;
        }
        // Optional: Add type checking based on key.getDefaultValue().getClass()
        // if (!key.getDefaultValue().getClass().isInstance(value)) {
        //     Debug.log("Warning: Setting value of type " + value.getClass().getName() +
        //            " for key " + key.name() + " which expects type " +
        //            key.getDefaultValue().getClass().getName());
        // }
        currentValues.put(key, value);
        // Save preferences immediately after setting a value (matches original behavior)
        savePreferences();
    }

    // --- Typed Getters (Convenience) ---

    public static String getString(ConfigKey key) {
        Object value = getValue(key);
        if (value instanceof String) {
            return (String) value;
        }
        Debug.log("Warning: Config key " + key.name() + " is not a String. Returning default.");
        return key.getDefaultString(); // Return default if type mismatch
    }

    public static int getInt(ConfigKey key) {
        Object value = getValue(key);
         if (value instanceof Integer) {
            return (Integer) value;
        }
        // Handle potential loading as Float/Double from Prefs/Properties if needed
        if (value instanceof Number) {
            Debug.log("Warning: Config key " + key.name() + " loaded as Number, converting to int.");
            return ((Number)value).intValue();
        }
        Debug.log("Warning: Config key " + key.name() + " is not an Integer. Returning default.");
        return key.getDefaultInt(); // Return default if type mismatch
    }

    public static float getFloat(ConfigKey key) {
        Object value = getValue(key);
        if (value instanceof Float) {
            return (Float) value;
        }
         // Handle potential loading as Double from Prefs/Properties
        if (value instanceof Number) {
             Debug.log("Warning: Config key " + key.name() + " loaded as Number, converting to float.");
            return ((Number)value).floatValue();
        }
        Debug.log("Warning: Config key " + key.name() + " is not a Float. Returning default.");
        return key.getDefaultFloat(); // Return default if type mismatch
    }

    public static boolean getBoolean(ConfigKey key) {
        Object value = getValue(key);
         if (value instanceof Boolean) {
            return (Boolean) value;
        }
        Debug.log("Warning: Config key " + key.name() + " is not a Boolean. Returning default.");
        return key.getDefaultBoolean(); // Return default if type mismatch
    }
}
