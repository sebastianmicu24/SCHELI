package com.sebastianmicu.scheli.segmentation;

import ij.Prefs;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class ConfigVariables {
    
    // Preference keys prefix
    private static final String KEY_PREFIX = "he_liver_seg.";
    
    // Keys for all preferences
    private static final String KEY_PERCENTILE_LOW = KEY_PREFIX + "percentile_low";
    private static final String KEY_PERCENTILE_HIGH = KEY_PREFIX + "percentile_high";
    private static final String KEY_TILES = KEY_PREFIX + "percentile_tiles";
    private static final String KEY_THRESHOLD_BACKGROUND = KEY_PREFIX + "threshold_background";
    private static final String KEY_THRESHOLD_NUCLEI = KEY_PREFIX + "threshold_nuclei";
    private static final String KEY_PATH_INPUT = KEY_PREFIX + "path_input";
    private static final String KEY_PATH_OUTPUT = KEY_PREFIX + "path_output";
    private static final String KEY_SCALE = KEY_PREFIX + "path_scale";
    private static final String KEY_SIZE_MIN_VESSEL = KEY_PREFIX + "size_min_vessel";
    private static final String KEY_SIZE_MAX_VESSEL = KEY_PREFIX + "size_max_vessel";
    private static final String KEY_SIZE_MIN_NUCLEUS = KEY_PREFIX + "size_min_nucleus";
    private static final String KEY_SIZE_MAX_NUCLEUS = KEY_PREFIX + "size_max_nucleus";
    private static final String KEY_COLOR_CENTRAL_NUCLEI = KEY_PREFIX + "color_central_nuclei";
    private static final String KEY_COLOR_CENTRAL_VESSELS = KEY_PREFIX + "color_central_vessels";
    private static final String KEY_COLOR_BORDER_NUCLEI = KEY_PREFIX + "color_border_nuclei";
    private static final String KEY_COLOR_BORDER_VESSELS = KEY_PREFIX + "color_border_vessels";
    private static final String KEY_COLOR_CENTRAL_CYTOPLASM = KEY_PREFIX + "color_central_cytoplasm";
    private static final String KEY_COLOR_BORDER_CYTOPLASM = KEY_PREFIX + "color_border_cytoplasm";
    private static final String KEY_COLOR_CENTRAL_CELLS = KEY_PREFIX + "color_central_cells";
    private static final String KEY_COLOR_BORDER_CELLS = KEY_PREFIX + "color_border_cells";
    private static final String KEY_BOOL_USE_SEMICOLONS = KEY_PREFIX + "bool_use_semicolons";
    private static final String KEY_BOOL_USE_COMMA_FOR_DECIMALS = KEY_PREFIX + "bool_use_comma_for_decimals";
    private static final String KEY_BOOL_SAVE_ROI = KEY_PREFIX + "bool_save_roi";
    private static final String KEY_BOOL_SAVE_PREVIEW = KEY_PREFIX + "bool_save_preview";
    private static final String KEY_BOOL_SAVE_AVERAGES = KEY_PREFIX + "bool_save_averages";
    private static final String KEY_BOOL_SAVE_INDIVIDUAL_CELLS = KEY_PREFIX + "bool_save_individual_cells";
    private static final String KEY_NEIGHBOR_RADIUS = KEY_PREFIX + "neighbor_radius";
    private static final String KEY_BOOL_IGNORE_BORDER_VESSELS = KEY_PREFIX + "bool_ignore_border_vessels";
    
    // Default values
    private static final float DEFAULT_PERCENTILE_LOW = 5.0f;
    private static final float DEFAULT_PERCENTILE_HIGH = 95.0f;
    private static final int DEFAULT_TILES = 1;
    private static final int DEFAULT_THRESHOLD_BACKGROUND = 83;
    private static final int DEFAULT_THRESHOLD_NUCLEI = 15;
    private static final String DEFAULT_PATH_INPUT = "";
    private static final String DEFAULT_PATH_OUTPUT = "";
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float DEFAULT_SIZE_MIN_VESSEL = 30.0f;
    private static final float DEFAULT_SIZE_MAX_VESSEL = 500000.0f;
    private static final float DEFAULT_SIZE_MIN_NUCLEUS = 1.0f;
    private static final float DEFAULT_SIZE_MAX_NUCLEUS = 100000.0f;
    private static final String DEFAULT_COLOR_CENTRAL_NUCLEI = "blue";
    private static final String DEFAULT_COLOR_CENTRAL_VESSELS = "red";
    private static final String DEFAULT_COLOR_BORDER_NUCLEI = "gray";
    private static final String DEFAULT_COLOR_BORDER_VESSELS = "black";
    private static final String DEFAULT_COLOR_CENTRAL_CYTOPLASM = "pink";
    private static final String DEFAULT_COLOR_BORDER_CYTOPLASM = "gray";
    private static final String DEFAULT_COLOR_CENTRAL_CELLS = "pink";
    private static final String DEFAULT_COLOR_BORDER_CELLS = "gray";
    private static final boolean DEFAULT_BOOL_USE_SEMICOLONS = true;
    private static final boolean DEFAULT_BOOL_USE_COMMA_FOR_DECIMALS = false;
    private static final boolean DEFAULT_BOOL_SAVE_ROI = true;
    private static final boolean DEFAULT_BOOL_SAVE_PREVIEW = false;
    private static final boolean DEFAULT_BOOL_SAVE_AVERAGES = true;
    private static final boolean DEFAULT_BOOL_SAVE_INDIVIDUAL_CELLS = true;
    private static final float DEFAULT_NEIGHBOR_RADIUS = 50.0f;
    private static final boolean DEFAULT_BOOL_IGNORE_BORDER_VESSELS = false;
    
    // In-memory storage for all values
    private static float currentPercentileLow = DEFAULT_PERCENTILE_LOW;
    private static float currentPercentileHigh = DEFAULT_PERCENTILE_HIGH;
    private static int currentTiles = DEFAULT_TILES;
    private static int currentThresholdBackground = DEFAULT_THRESHOLD_BACKGROUND;
    private static int currentThresholdNuclei = DEFAULT_THRESHOLD_NUCLEI;
    private static String currentPathInput = DEFAULT_PATH_INPUT;
    private static String currentPathOutput = DEFAULT_PATH_OUTPUT;
    private static float currentPathScale = DEFAULT_SCALE;
    private static float currentSizeMinVessel = DEFAULT_SIZE_MIN_VESSEL;
    private static float currentSizeMaxVessel = DEFAULT_SIZE_MAX_VESSEL;
    private static float currentSizeMinNucleus = DEFAULT_SIZE_MIN_NUCLEUS;
    private static float currentSizeMaxNucleus = DEFAULT_SIZE_MAX_NUCLEUS;
    private static String currentColorCentralNuclei = DEFAULT_COLOR_CENTRAL_NUCLEI;
    private static String currentColorCentralVessels = DEFAULT_COLOR_CENTRAL_VESSELS;
    private static String currentColorBorderNuclei = DEFAULT_COLOR_BORDER_NUCLEI;
    private static String currentColorBorderVessels = DEFAULT_COLOR_BORDER_VESSELS;
    private static String currentColorCentralCytoplasm = DEFAULT_COLOR_CENTRAL_CYTOPLASM;
    private static String currentColorBorderCytoplasm = DEFAULT_COLOR_BORDER_CYTOPLASM;
    private static String currentColorCentralCells = DEFAULT_COLOR_CENTRAL_CELLS;
    private static String currentColorBorderCells = DEFAULT_COLOR_BORDER_CELLS;
    private static boolean currentUseSemicolons = DEFAULT_BOOL_USE_SEMICOLONS;
    private static boolean currentUseCommaForDecimals = DEFAULT_BOOL_USE_COMMA_FOR_DECIMALS;
    private static boolean currentSaveRoi = DEFAULT_BOOL_SAVE_ROI;
    private static boolean currentSavePreview = DEFAULT_BOOL_SAVE_PREVIEW;
    private static boolean currentSaveAverages = DEFAULT_BOOL_SAVE_AVERAGES;
    private static boolean currentSaveIndividualCells = DEFAULT_BOOL_SAVE_INDIVIDUAL_CELLS;
    private static float currentNeighborRadius = DEFAULT_NEIGHBOR_RADIUS;
    private static boolean currentIgnoreBorderVessels = DEFAULT_BOOL_IGNORE_BORDER_VESSELS;
    
    // Properties file for persistent storage
    private static final String PROPERTIES_FILE = System.getProperty("user.home") + File.separator + ".he_liver_seg.properties";
    private static Properties properties = new Properties();
    
    // Load preferences when class is loaded
    static {
        loadPreferences();
    }
    
    // Load preferences from disk or properties file
    public static void loadPreferences() {
        try {
            // First try to load from properties file
            File propsFile = new File(PROPERTIES_FILE);
            if (propsFile.exists()) {
                properties.load(new FileInputStream(propsFile));
                
                // Load values from properties
                currentPercentileLow = Float.parseFloat(properties.getProperty(KEY_PERCENTILE_LOW, String.valueOf(DEFAULT_PERCENTILE_LOW)));
                currentPercentileHigh = Float.parseFloat(properties.getProperty(KEY_PERCENTILE_HIGH, String.valueOf(DEFAULT_PERCENTILE_HIGH)));
                currentTiles = Integer.parseInt(properties.getProperty(KEY_TILES, String.valueOf(DEFAULT_TILES)));
                currentThresholdBackground = Integer.parseInt(properties.getProperty(KEY_THRESHOLD_BACKGROUND, String.valueOf(DEFAULT_THRESHOLD_BACKGROUND)));
                currentThresholdNuclei = Integer.parseInt(properties.getProperty(KEY_THRESHOLD_NUCLEI, String.valueOf(DEFAULT_THRESHOLD_NUCLEI)));
                currentPathInput = properties.getProperty(KEY_PATH_INPUT, DEFAULT_PATH_INPUT);
                currentPathOutput = properties.getProperty(KEY_PATH_OUTPUT, DEFAULT_PATH_OUTPUT);
                currentPathScale = Float.parseFloat(properties.getProperty(KEY_SCALE, String.valueOf(DEFAULT_SCALE)));
                currentSizeMinVessel = Float.parseFloat(properties.getProperty(KEY_SIZE_MIN_VESSEL, String.valueOf(DEFAULT_SIZE_MIN_VESSEL)));
                currentSizeMaxVessel = Float.parseFloat(properties.getProperty(KEY_SIZE_MAX_VESSEL, String.valueOf(DEFAULT_SIZE_MAX_VESSEL)));
                currentSizeMinNucleus = Float.parseFloat(properties.getProperty(KEY_SIZE_MIN_NUCLEUS, String.valueOf(DEFAULT_SIZE_MIN_NUCLEUS)));
                currentSizeMaxNucleus = Float.parseFloat(properties.getProperty(KEY_SIZE_MAX_NUCLEUS, String.valueOf(DEFAULT_SIZE_MAX_NUCLEUS)));
                currentColorCentralNuclei = properties.getProperty(KEY_COLOR_CENTRAL_NUCLEI, DEFAULT_COLOR_CENTRAL_NUCLEI);
                currentColorCentralVessels = properties.getProperty(KEY_COLOR_CENTRAL_VESSELS, DEFAULT_COLOR_CENTRAL_VESSELS);
                currentColorBorderNuclei = properties.getProperty(KEY_COLOR_BORDER_NUCLEI, DEFAULT_COLOR_BORDER_NUCLEI);
                currentColorBorderVessels = properties.getProperty(KEY_COLOR_BORDER_VESSELS, DEFAULT_COLOR_BORDER_VESSELS);
                currentColorCentralCytoplasm = properties.getProperty(KEY_COLOR_CENTRAL_CYTOPLASM, DEFAULT_COLOR_CENTRAL_CYTOPLASM);
                currentColorBorderCytoplasm = properties.getProperty(KEY_COLOR_BORDER_CYTOPLASM, DEFAULT_COLOR_BORDER_CYTOPLASM);
                currentColorCentralCells = properties.getProperty(KEY_COLOR_CENTRAL_CELLS, DEFAULT_COLOR_CENTRAL_CELLS);
                currentColorBorderCells = properties.getProperty(KEY_COLOR_BORDER_CELLS, DEFAULT_COLOR_BORDER_CELLS);
                currentUseSemicolons = Boolean.parseBoolean(properties.getProperty(KEY_BOOL_USE_SEMICOLONS, String.valueOf(DEFAULT_BOOL_USE_SEMICOLONS)));
                currentUseCommaForDecimals = Boolean.parseBoolean(properties.getProperty(KEY_BOOL_USE_COMMA_FOR_DECIMALS, String.valueOf(DEFAULT_BOOL_USE_COMMA_FOR_DECIMALS)));
                currentSaveRoi = Boolean.parseBoolean(properties.getProperty(KEY_BOOL_SAVE_ROI, String.valueOf(DEFAULT_BOOL_SAVE_ROI)));
                currentSavePreview = Boolean.parseBoolean(properties.getProperty(KEY_BOOL_SAVE_PREVIEW, String.valueOf(DEFAULT_BOOL_SAVE_PREVIEW)));
                currentSaveAverages = Boolean.parseBoolean(properties.getProperty(KEY_BOOL_SAVE_AVERAGES, String.valueOf(DEFAULT_BOOL_SAVE_AVERAGES)));
                currentSaveIndividualCells = Boolean.parseBoolean(properties.getProperty(KEY_BOOL_SAVE_INDIVIDUAL_CELLS, String.valueOf(DEFAULT_BOOL_SAVE_INDIVIDUAL_CELLS)));
            } else {
                // If properties file doesn't exist, try to load from ImageJ Prefs
                currentPercentileLow = (float)Prefs.getDouble(KEY_PERCENTILE_LOW, DEFAULT_PERCENTILE_LOW);
                currentPercentileHigh = (float)Prefs.getDouble(KEY_PERCENTILE_HIGH, DEFAULT_PERCENTILE_HIGH);
                currentTiles = Prefs.getInt(KEY_TILES, DEFAULT_TILES);
                currentThresholdBackground = Prefs.getInt(KEY_THRESHOLD_BACKGROUND, DEFAULT_THRESHOLD_BACKGROUND);
                currentThresholdNuclei = Prefs.getInt(KEY_THRESHOLD_NUCLEI, DEFAULT_THRESHOLD_NUCLEI);
                currentPathInput = Prefs.get(KEY_PATH_INPUT, DEFAULT_PATH_INPUT);
                currentPathOutput = Prefs.get(KEY_PATH_OUTPUT, DEFAULT_PATH_OUTPUT);
                currentPathScale = (float)Prefs.getDouble(KEY_SCALE, DEFAULT_SCALE);
                currentSizeMinVessel = (float)Prefs.getDouble(KEY_SIZE_MIN_VESSEL, DEFAULT_SIZE_MIN_VESSEL);
                currentSizeMaxVessel = (float)Prefs.getDouble(KEY_SIZE_MAX_VESSEL, DEFAULT_SIZE_MAX_VESSEL);
                currentSizeMinNucleus = (float)Prefs.getDouble(KEY_SIZE_MIN_NUCLEUS, DEFAULT_SIZE_MIN_NUCLEUS);
                currentSizeMaxNucleus = (float)Prefs.getDouble(KEY_SIZE_MAX_NUCLEUS, DEFAULT_SIZE_MAX_NUCLEUS);
                currentColorCentralNuclei = Prefs.get(KEY_COLOR_CENTRAL_NUCLEI, DEFAULT_COLOR_CENTRAL_NUCLEI);
                currentColorCentralVessels = Prefs.get(KEY_COLOR_CENTRAL_VESSELS, DEFAULT_COLOR_CENTRAL_VESSELS);
                currentColorBorderNuclei = Prefs.get(KEY_COLOR_BORDER_NUCLEI, DEFAULT_COLOR_BORDER_NUCLEI);
                currentColorBorderVessels = Prefs.get(KEY_COLOR_BORDER_VESSELS, DEFAULT_COLOR_BORDER_VESSELS);
                currentColorCentralCytoplasm = Prefs.get(KEY_COLOR_CENTRAL_CYTOPLASM, DEFAULT_COLOR_CENTRAL_CYTOPLASM);
                currentColorBorderCytoplasm = Prefs.get(KEY_COLOR_BORDER_CYTOPLASM, DEFAULT_COLOR_BORDER_CYTOPLASM);
                currentColorCentralCells = Prefs.get(KEY_COLOR_CENTRAL_CELLS, DEFAULT_COLOR_CENTRAL_CELLS);
                currentColorBorderCells = Prefs.get(KEY_COLOR_BORDER_CELLS, DEFAULT_COLOR_BORDER_CELLS);
                currentUseSemicolons = Prefs.getBoolean(KEY_BOOL_USE_SEMICOLONS, DEFAULT_BOOL_USE_SEMICOLONS);
                currentUseCommaForDecimals = Prefs.getBoolean(KEY_BOOL_USE_COMMA_FOR_DECIMALS, DEFAULT_BOOL_USE_COMMA_FOR_DECIMALS);
                currentSaveRoi = Prefs.getBoolean(KEY_BOOL_SAVE_ROI, DEFAULT_BOOL_SAVE_ROI);
                currentSavePreview = Prefs.getBoolean(KEY_BOOL_SAVE_PREVIEW, DEFAULT_BOOL_SAVE_PREVIEW);
                currentSaveAverages = Prefs.getBoolean(KEY_BOOL_SAVE_AVERAGES, DEFAULT_BOOL_SAVE_AVERAGES);
                currentSaveIndividualCells = Prefs.getBoolean(KEY_BOOL_SAVE_INDIVIDUAL_CELLS, DEFAULT_BOOL_SAVE_INDIVIDUAL_CELLS);
                currentNeighborRadius = (float)Prefs.getDouble(KEY_NEIGHBOR_RADIUS, DEFAULT_NEIGHBOR_RADIUS);
                currentIgnoreBorderVessels = Prefs.getBoolean(KEY_BOOL_IGNORE_BORDER_VESSELS, DEFAULT_BOOL_IGNORE_BORDER_VESSELS);
            }
        } catch (Exception e) {
            // If loading fails, use defaults
        }
    }
    
    // Percentiles getters and setters
    public static float getPercentileLow() { return currentPercentileLow; }
    public static void setPercentileLow(float value) {
        currentPercentileLow = value;
        Prefs.set(KEY_PERCENTILE_LOW, value);
        savePreferences();
    }
    
    public static float getPercentileHigh() { return currentPercentileHigh; }
    public static void setPercentileHigh(float value) {
        currentPercentileHigh = value;
        Prefs.set(KEY_PERCENTILE_HIGH, value);
        savePreferences();
    }
    
    public static int getPercentileTiles() { return currentTiles; }
    public static void setPercentileTiles(int value) {
        currentTiles = value;
        Prefs.set(KEY_TILES, value);
        savePreferences();
    }
    
    // Thresholds getters and setters
    public static int getThresholdBackground() { return currentThresholdBackground; }
    public static void setThresholdBackground(int value) {
        currentThresholdBackground = value;
        Prefs.set(KEY_THRESHOLD_BACKGROUND, value);
        savePreferences();
    }
    
    public static int getThresholdNuclei() { return currentThresholdNuclei; }
    public static void setThresholdNuclei(int value) {
        currentThresholdNuclei = value;
        Prefs.set(KEY_THRESHOLD_NUCLEI, value);
        savePreferences();
    }
    
    // Paths getters and setters
    public static String getPathInput() { return currentPathInput; }
    public static void setPathInput(String value) {
        currentPathInput = value;
        Prefs.set(KEY_PATH_INPUT, value);
        savePreferences();
    }
    
    public static String getPathOutput() { return currentPathOutput; }
    public static void setPathOutput(String value) {
        currentPathOutput = value;
        Prefs.set(KEY_PATH_OUTPUT, value);
        savePreferences();
    }
    
    public static float getPathScale() { return currentPathScale; }
    public static void setPathScale(float value) {
        currentPathScale = value;
        Prefs.set(KEY_SCALE, value);
        savePreferences();
    }
    
    // Size getters and setters
    // Get methods apply the scale factor to convert from pixels to scaled units
    public static float getSizeMinVessel() {
        return currentSizeMinVessel / (currentPathScale * currentPathScale);
    }
    
    // Get raw value without scale for UI display
    public static float getRawSizeMinVessel() {
        return currentSizeMinVessel;
    }
    
    public static void setSizeMinVessel(float value) {
        currentSizeMinVessel = value;
        Prefs.set(KEY_SIZE_MIN_VESSEL, value);
        savePreferences();
    }
    
    public static float getSizeMaxVessel() {
        return currentSizeMaxVessel / (currentPathScale * currentPathScale);
    }
    
    // Get raw value without scale for UI display
    public static float getRawSizeMaxVessel() {
        return currentSizeMaxVessel;
    }
    
    public static void setSizeMaxVessel(float value) {
        currentSizeMaxVessel = value;
        Prefs.set(KEY_SIZE_MAX_VESSEL, value);
        savePreferences();
    }
    
    public static float getSizeMinNucleus() {
        return currentSizeMinNucleus * currentPathScale * currentPathScale;
    }
    
    // Get raw value without scale for UI display
    public static float getRawSizeMinNucleus() {
        return currentSizeMinNucleus;
    }
    
    public static void setSizeMinNucleus(float value) {
        currentSizeMinNucleus = value;
        Prefs.set(KEY_SIZE_MIN_NUCLEUS, value);
        savePreferences();
    }
    
    public static float getSizeMaxNucleus() {
        return currentSizeMaxNucleus * currentPathScale * currentPathScale;
    }
    
    // Get raw value without scale for UI display
    public static float getRawSizeMaxNucleus() {
        return currentSizeMaxNucleus;
    }
    
    public static void setSizeMaxNucleus(float value) {
        currentSizeMaxNucleus = value;
        Prefs.set(KEY_SIZE_MAX_NUCLEUS, value);
        savePreferences();
    }
    
    // Color getters and setters
    public static String getColorCentralNuclei() { return currentColorCentralNuclei; }
    public static void setColorCentralNuclei(String value) {
        currentColorCentralNuclei = value;
        Prefs.set(KEY_COLOR_CENTRAL_NUCLEI, value);
        savePreferences();
    }
    
    public static String getColorCentralVessels() { return currentColorCentralVessels; }
    public static void setColorCentralVessels(String value) {
        currentColorCentralVessels = value;
        Prefs.set(KEY_COLOR_CENTRAL_VESSELS, value);
        savePreferences();
    }
    
    public static String getColorBorderNuclei() { return currentColorBorderNuclei; }
    public static void setColorBorderNuclei(String value) {
        currentColorBorderNuclei = value;
        Prefs.set(KEY_COLOR_BORDER_NUCLEI, value);
        savePreferences();
    }
    
    public static String getColorBorderVessels() { return currentColorBorderVessels; }
    public static void setColorBorderVessels(String value) {
        currentColorBorderVessels = value;
        Prefs.set(KEY_COLOR_BORDER_VESSELS, value);
        savePreferences();
    }
    
    public static String getColorCentralCytoplasm() { return currentColorCentralCytoplasm; }
    public static void setColorCentralCytoplasm(String value) {
        currentColorCentralCytoplasm = value;
        Prefs.set(KEY_COLOR_CENTRAL_CYTOPLASM, value);
        savePreferences();
    }
    
    public static String getColorBorderCytoplasm() { return currentColorBorderCytoplasm; }
    public static void setColorBorderCytoplasm(String value) {
        currentColorBorderCytoplasm = value;
        Prefs.set(KEY_COLOR_BORDER_CYTOPLASM, value);
        savePreferences();
    }
    
    public static String getColorCentralCells() { return currentColorCentralCells; }
    public static void setColorCentralCells(String value) {
        currentColorCentralCells = value;
        Prefs.set(KEY_COLOR_CENTRAL_CELLS, value);
        savePreferences();
    }
    
    public static String getColorBorderCells() { return currentColorBorderCells; }
    public static void setColorBorderCells(String value) {
        currentColorBorderCells = value;
        Prefs.set(KEY_COLOR_BORDER_CELLS, value);
        savePreferences();
    }
    
    // Boolean getters and setters
    public static boolean getUseSemicolons() { return currentUseSemicolons; }
    public static void setUseSemicolons(boolean value) {
        currentUseSemicolons = value;
        Prefs.set(KEY_BOOL_USE_SEMICOLONS, value);
        savePreferences();
    }
    
    public static boolean getUseCommaForDecimals() { return currentUseCommaForDecimals; }
    public static void setUseCommaForDecimals(boolean value) {
        currentUseCommaForDecimals = value;
        Prefs.set(KEY_BOOL_USE_COMMA_FOR_DECIMALS, value);
        savePreferences();
    }
    
    public static boolean getSaveRoi() { return currentSaveRoi; }
    public static void setSaveRoi(boolean value) {
        currentSaveRoi = value;
        Prefs.set(KEY_BOOL_SAVE_ROI, value);
        savePreferences();
    }
    
    public static boolean getSavePreview() { return currentSavePreview; }
    public static void setSavePreview(boolean value) {
        currentSavePreview = value;
        Prefs.set(KEY_BOOL_SAVE_PREVIEW, value);
        savePreferences();
    }
    
    public static boolean getSaveAverages() { return currentSaveAverages; }
    public static void setSaveAverages(boolean value) {
        currentSaveAverages = value;
        Prefs.set(KEY_BOOL_SAVE_AVERAGES, value);
        savePreferences();
    }
    
    public static boolean getSaveIndividualCells() { return currentSaveIndividualCells; }
    public static void setSaveIndividualCells(boolean value) {
        currentSaveIndividualCells = value;
        Prefs.set(KEY_BOOL_SAVE_INDIVIDUAL_CELLS, value);
        savePreferences();
    }
    
    public static float getNeighborRadius() { return currentNeighborRadius * currentPathScale; }
    
    // Get raw value without scale for UI display
    public static float getRawNeighborRadius() {
        return currentNeighborRadius;
    }
    
    public static void setNeighborRadius(float value) {
        currentNeighborRadius = value;
        Prefs.set(KEY_NEIGHBOR_RADIUS, value);
        savePreferences();
    }
    
    public static boolean getIgnoreBorderVessels() { return currentIgnoreBorderVessels; }
    public static void setIgnoreBorderVessels(boolean value) {
        currentIgnoreBorderVessels = value;
        Prefs.set(KEY_BOOL_IGNORE_BORDER_VESSELS, value);
        savePreferences();
    }
    
    // Save all preferences to disk
    public static void savePreferences() {
        try {
            // Save to ImageJ Prefs
            Prefs.set(KEY_PERCENTILE_LOW, currentPercentileLow);
            Prefs.set(KEY_PERCENTILE_HIGH, currentPercentileHigh);
            Prefs.set(KEY_TILES, currentTiles);
            Prefs.set(KEY_THRESHOLD_BACKGROUND, currentThresholdBackground);
            Prefs.set(KEY_THRESHOLD_NUCLEI, currentThresholdNuclei);
            Prefs.set(KEY_PATH_INPUT, currentPathInput);
            Prefs.set(KEY_PATH_OUTPUT, currentPathOutput);
            Prefs.set(KEY_SCALE, currentPathScale);
            Prefs.set(KEY_SIZE_MIN_VESSEL, currentSizeMinVessel);
            Prefs.set(KEY_SIZE_MAX_VESSEL, currentSizeMaxVessel);
            Prefs.set(KEY_SIZE_MIN_NUCLEUS, currentSizeMinNucleus);
            Prefs.set(KEY_SIZE_MAX_NUCLEUS, currentSizeMaxNucleus);
            Prefs.set(KEY_COLOR_CENTRAL_NUCLEI, currentColorCentralNuclei);
            Prefs.set(KEY_COLOR_CENTRAL_VESSELS, currentColorCentralVessels);
            Prefs.set(KEY_COLOR_BORDER_NUCLEI, currentColorBorderNuclei);
            Prefs.set(KEY_COLOR_BORDER_VESSELS, currentColorBorderVessels);
            Prefs.set(KEY_COLOR_CENTRAL_CYTOPLASM, currentColorCentralCytoplasm);
            Prefs.set(KEY_COLOR_BORDER_CYTOPLASM, currentColorBorderCytoplasm);
            Prefs.set(KEY_COLOR_CENTRAL_CELLS, currentColorCentralCells);
            Prefs.set(KEY_COLOR_BORDER_CELLS, currentColorBorderCells);
            Prefs.set(KEY_BOOL_USE_SEMICOLONS, currentUseSemicolons);
            Prefs.set(KEY_BOOL_USE_COMMA_FOR_DECIMALS, currentUseCommaForDecimals);
            Prefs.set(KEY_BOOL_SAVE_ROI, currentSaveRoi);
            Prefs.set(KEY_BOOL_SAVE_PREVIEW, currentSavePreview);
            Prefs.set(KEY_BOOL_SAVE_AVERAGES, currentSaveAverages);
            Prefs.set(KEY_BOOL_SAVE_INDIVIDUAL_CELLS, currentSaveIndividualCells);
            Prefs.set(KEY_NEIGHBOR_RADIUS, currentNeighborRadius);
            Prefs.set(KEY_BOOL_IGNORE_BORDER_VESSELS, currentIgnoreBorderVessels);
            
            // Save preferences to disk
            Prefs.savePreferences();
            
            // Also save to properties file
            properties.setProperty(KEY_PERCENTILE_LOW, String.valueOf(currentPercentileLow));
            properties.setProperty(KEY_PERCENTILE_HIGH, String.valueOf(currentPercentileHigh));
            properties.setProperty(KEY_TILES, String.valueOf(currentTiles));
            properties.setProperty(KEY_THRESHOLD_BACKGROUND, String.valueOf(currentThresholdBackground));
            properties.setProperty(KEY_THRESHOLD_NUCLEI, String.valueOf(currentThresholdNuclei));
            properties.setProperty(KEY_PATH_INPUT, currentPathInput);
            properties.setProperty(KEY_PATH_OUTPUT, currentPathOutput);
            properties.setProperty(KEY_SCALE, String.valueOf(currentPathScale));
            properties.setProperty(KEY_SIZE_MIN_VESSEL, String.valueOf(currentSizeMinVessel));
            properties.setProperty(KEY_SIZE_MAX_VESSEL, String.valueOf(currentSizeMaxVessel));
            properties.setProperty(KEY_SIZE_MIN_NUCLEUS, String.valueOf(currentSizeMinNucleus));
            properties.setProperty(KEY_SIZE_MAX_NUCLEUS, String.valueOf(currentSizeMaxNucleus));
            properties.setProperty(KEY_COLOR_CENTRAL_NUCLEI, currentColorCentralNuclei);
            properties.setProperty(KEY_COLOR_CENTRAL_VESSELS, currentColorCentralVessels);
            properties.setProperty(KEY_COLOR_BORDER_NUCLEI, currentColorBorderNuclei);
            properties.setProperty(KEY_COLOR_BORDER_VESSELS, currentColorBorderVessels);
            properties.setProperty(KEY_COLOR_CENTRAL_CYTOPLASM, currentColorCentralCytoplasm);
            properties.setProperty(KEY_COLOR_BORDER_CYTOPLASM, currentColorBorderCytoplasm);
            properties.setProperty(KEY_COLOR_CENTRAL_CELLS, currentColorCentralCells);
            properties.setProperty(KEY_COLOR_BORDER_CELLS, currentColorBorderCells);
            properties.setProperty(KEY_BOOL_USE_SEMICOLONS, String.valueOf(currentUseSemicolons));
            properties.setProperty(KEY_BOOL_USE_COMMA_FOR_DECIMALS, String.valueOf(currentUseCommaForDecimals));
            properties.setProperty(KEY_BOOL_SAVE_ROI, String.valueOf(currentSaveRoi));
            properties.setProperty(KEY_BOOL_SAVE_PREVIEW, String.valueOf(currentSavePreview));
            properties.setProperty(KEY_BOOL_SAVE_AVERAGES, String.valueOf(currentSaveAverages));
            properties.setProperty(KEY_BOOL_SAVE_INDIVIDUAL_CELLS, String.valueOf(currentSaveIndividualCells));
            properties.setProperty(KEY_NEIGHBOR_RADIUS, String.valueOf(currentNeighborRadius));
            properties.setProperty(KEY_BOOL_IGNORE_BORDER_VESSELS, String.valueOf(currentIgnoreBorderVessels));
            
            // Save properties to file
            try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
                properties.store(out, "H&E Liver Segmentation Settings");
            }
            
            // Force preferences to be written to disk immediately
            java.util.prefs.Preferences.userRoot().flush();
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    // Reset all preferences to default values
    public static void resetAllPreferences() {
        // Reset in-memory values to defaults
        currentPercentileLow = DEFAULT_PERCENTILE_LOW;
        currentPercentileHigh = DEFAULT_PERCENTILE_HIGH;
        currentTiles = DEFAULT_TILES;
        currentThresholdBackground = DEFAULT_THRESHOLD_BACKGROUND;
        currentThresholdNuclei = DEFAULT_THRESHOLD_NUCLEI;
        currentPathInput = DEFAULT_PATH_INPUT;
        currentPathOutput = DEFAULT_PATH_OUTPUT;
        currentPathScale = DEFAULT_SCALE;
        currentSizeMinVessel = DEFAULT_SIZE_MIN_VESSEL;
        currentSizeMaxVessel = DEFAULT_SIZE_MAX_VESSEL;
        currentSizeMinNucleus = DEFAULT_SIZE_MIN_NUCLEUS;
        currentSizeMaxNucleus = DEFAULT_SIZE_MAX_NUCLEUS;
        currentColorCentralNuclei = DEFAULT_COLOR_CENTRAL_NUCLEI;
        currentColorCentralVessels = DEFAULT_COLOR_CENTRAL_VESSELS;
        currentColorBorderNuclei = DEFAULT_COLOR_BORDER_NUCLEI;
        currentColorBorderVessels = DEFAULT_COLOR_BORDER_VESSELS;
        currentColorCentralCytoplasm = DEFAULT_COLOR_CENTRAL_CYTOPLASM;
        currentColorBorderCytoplasm = DEFAULT_COLOR_BORDER_CYTOPLASM;
        currentColorCentralCells = DEFAULT_COLOR_CENTRAL_CELLS;
        currentColorBorderCells = DEFAULT_COLOR_BORDER_CELLS;
        currentUseSemicolons = DEFAULT_BOOL_USE_SEMICOLONS;
        currentUseCommaForDecimals = DEFAULT_BOOL_USE_COMMA_FOR_DECIMALS;
        currentSaveRoi = DEFAULT_BOOL_SAVE_ROI;
        currentSavePreview = DEFAULT_BOOL_SAVE_PREVIEW;
        currentSaveAverages = DEFAULT_BOOL_SAVE_AVERAGES;
        currentSaveIndividualCells = DEFAULT_BOOL_SAVE_INDIVIDUAL_CELLS;
        currentNeighborRadius = DEFAULT_NEIGHBOR_RADIUS;
        currentIgnoreBorderVessels = DEFAULT_BOOL_IGNORE_BORDER_VESSELS;
        
        // Save defaults to disk
        savePreferences();
        
        // Delete properties file if it exists
        try {
            File propsFile = new File(PROPERTIES_FILE);
            if (propsFile.exists()) {
                propsFile.delete();
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }
}
