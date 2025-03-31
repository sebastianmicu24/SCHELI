import ij.IJ;
import ij.Prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Debug utility class for SCHELI plugin.
 * Provides configurable logging functionality that can be enabled/disabled via preferences.
 * 
 * @author Cristian Sebastian Micu
 */
public class Debug {
    
    // Preference key for debug setting
    private static final String KEY_SHOW_DEBUG = "he_liver.show_debug";
    
    // Default value
    private static final boolean DEFAULT_SHOW_DEBUG = false;
    
    // In-memory storage
    private static boolean currentShowDebug = DEFAULT_SHOW_DEBUG;
    
    // Properties file for persistent storage
    private static final String PROPERTIES_FILE = System.getProperty("user.home") + File.separator + ".he_liver.properties";
    private static Properties properties = new Properties();
    
    // Load preferences when class is loaded
    static {
        loadPreferences();
    }
    
    /**
     * Loads the debug preference from disk or properties file.
     */
    public static void loadPreferences() {
        try {
            // First try to load from properties file
            File propsFile = new File(PROPERTIES_FILE);
            if (propsFile.exists()) {
                properties.load(new FileInputStream(propsFile));
                
                // Load debug value from properties
                currentShowDebug = Boolean.parseBoolean(properties.getProperty(KEY_SHOW_DEBUG, String.valueOf(DEFAULT_SHOW_DEBUG)));
            } else {
                // If properties file doesn't exist, try to load from ImageJ Prefs
                currentShowDebug = Prefs.getBoolean(KEY_SHOW_DEBUG, DEFAULT_SHOW_DEBUG);
            }
        } catch (Exception e) {
            // If loading fails, use default
            currentShowDebug = DEFAULT_SHOW_DEBUG;
        }
    }
    
    /**
     * Saves the debug preference to disk.
     */
    public static void savePreferences() {
        try {
            // Save to ImageJ Prefs
            Prefs.set(KEY_SHOW_DEBUG, currentShowDebug);
            
            // Save preferences to disk
            Prefs.savePreferences();
            
            // Also save to properties file
            properties.setProperty(KEY_SHOW_DEBUG, String.valueOf(currentShowDebug));
            
            // Save properties to file
            try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
                properties.store(out, "SCHELI Debug Settings");
            }
            
            // Force preferences to be written to disk immediately
            java.util.prefs.Preferences.userRoot().flush();
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    /**
     * Gets the current debug setting.
     * 
     * @return true if debug messages should be shown, false otherwise
     */
    public static boolean getShowDebug() {
        return currentShowDebug;
    }
    
    /**
     * Sets the debug setting.
     * 
     * @param value true to show debug messages, false to hide them
     */
    public static void setShowDebug(boolean value) {
        currentShowDebug = value;
        Prefs.set(KEY_SHOW_DEBUG, value);
        savePreferences();
    }
    
    /**
     * Logs a debug message if debugging is enabled.
     * This method can be used as a direct replacement for IJ.log.
     * 
     * @param message the message to log
     */
    public static void log(String message) {
        if (currentShowDebug) {
            IJ.log(message);
        }
    }
    
    /**
     * Logs a debug message with a formatted string if debugging is enabled.
     * This method can be used as a direct replacement for IJ.log.
     * 
     * @param format the format string
     * @param args the arguments referenced by the format specifiers
     */
    public static void log(String format, Object... args) {
        if (currentShowDebug) {
            IJ.log(String.format(format, args));
        }
    }
}