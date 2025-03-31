package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import java.io.File; // Added import for File class
import com.sebastianmicu.scheli.Debug;
import ij.io.OpenDialog; // Import for file selection
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.List; // Keep if selectedFeatures is used elsewhere, otherwise remove
// import java.util.ArrayList; // Keep if selectedFeatures is used elsewhere, otherwise remove
import java.util.Optional; // For findConfigKeyFromString

// Import the ConfigKey enum
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey; // Revert import to match declared package in ConfigVariables.java

/**
 * User interface for XGBoost classifier training settings.
 * Provides panels for parameters, feature selection, and main operations.
 *
 * @author Cristian Sebastian Micu
 */
public class TrainSettingsUI { // Renamed class

    private static JDialog dialog;
    private static final Map<String, JComponent> controls = new HashMap<>();
    // private static List<String> selectedFeatures = new ArrayList<>(); // No longer needed here, managed by ConfigVariables

    /**
     * Shows the settings dialog to the user
     *
     * @return true if OK was clicked, false otherwise
     */
    public static boolean showDialog() {
        // Reset static variables
        controls.clear();
        dialog = null;

        // Use a CountDownLatch to synchronize between threads
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final boolean[] result = new boolean[1];
        result[0] = false; // Initialize to false

        // Create and show dialog on EDT
        SwingUtilities.invokeLater(() -> {
            try {
                // Create new dialog
                dialog = new JDialog((Frame)null, "XGBoost Classifier - Train Model Settings", true); // Updated title
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                // Add window listener to handle dialog close
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        // Release the latch when dialog is closed
                        latch.countDown();
                    }
                });

                // Create tabbed pane
                JTabbedPane tabbedPane = new JTabbedPane();
                tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

                // Add tabs for each category (All relevant for training)
                tabbedPane.addTab("Main", createMainPanel());
                tabbedPane.addTab("Parameters", createParametersPanel());
                tabbedPane.addTab("Features", createFeaturesPanel());
                tabbedPane.addTab("Output", createOutputPanel());

                // Create buttons
                JPanel buttonPanel = new JPanel();
                JButton okButton = new JButton("OK");
                JButton cancelButton = new JButton("Cancel");
                JButton resetButton = new JButton("Reset to Defaults");

                // Set button actions
                okButton.addActionListener(e -> {
                    updateSettings();
                    // Explicitly save preferences to ensure they persist
                    ConfigVariables.savePreferences();
                    // Print all values to the console
                    ConfigVariables.printAllValues();
                    result[0] = true;
                    dialog.dispose();
                });

                cancelButton.addActionListener(e -> {
                    result[0] = false;
                    dialog.dispose();
                });

                resetButton.addActionListener(e -> {
                    if (JOptionPane.showConfirmDialog(dialog,
                            "Reset all settings to default values?",
                            "Reset Settings",
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        // Reset settings to defaults
                        ConfigVariables.resetAllPreferences();
                        dialog.dispose();
                        result[0] = false; // Re-show dialog might be better, but for now just close
                    }
                });

                buttonPanel.add(resetButton);
                buttonPanel.add(cancelButton);
                buttonPanel.add(okButton);

                // Create main panel
                JPanel mainPanel = new JPanel(new BorderLayout());
                mainPanel.add(tabbedPane, BorderLayout.CENTER);
                mainPanel.add(buttonPanel, BorderLayout.SOUTH);
                mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                dialog.add(mainPanel);
                dialog.setMinimumSize(new Dimension(500, 400));
                dialog.pack();

                // Limit the dialog height to 500px while maintaining width
                Dimension size = dialog.getSize();
                if (size.height > 600) {
                    dialog.setSize(new Dimension(size.width, 500));
                }
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
            } catch (Exception e) {
                IJ.handleException(e);
                result[0] = false;
                latch.countDown(); // Make sure to release the latch on error
            }
        });

        // Wait for dialog to close using the latch
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Clean up
        dialog = null;

        return result[0];
    }

    /**
     * Creates the main panel with input/output paths
     */
    private static JPanel createMainPanel() {
        JPanel panel = createPanel("Main Settings");

        // Use helper to add path inputs
        addPathInput(panel, "CSV Data Directory:", ConfigKey.PATH_CSV_DATA, true); // Directory
        addPathInput(panel, "Output Directory:", ConfigKey.PATH_OUTPUT_DIR, true); // Directory for model, features, etc.
        addPathInput(panel, "Ground Truth JSON:", ConfigKey.PATH_JSON_DATA, false); // File

        // Train Ratio Slider (remains as before)
        addSlider(panel, "Train Ratio:", 0.1f, 0.9f, 0.05f, ConfigKey.TRAIN_RATIO); // Pass ConfigKey

        return panel;
    }

    /**
     * Helper method to add a path input field (JTextField + Browse button) to a panel.
     * Stores the JTextField in the controls map using the ConfigKey's name as the map key.
     * @param panel The panel to add the components to.
     * @param labelText The text for the JLabel.
     * @param configKey The ConfigKey associated with this path.
     * @param isDirectory True if a directory chooser should be used, false for a file chooser.
     */
    private static void addPathInput(JPanel panel, String labelText, ConfigKey configKey, boolean isDirectory) {
        GridBagConstraints c = createConstraints();
        panel.add(new JLabel(labelText), c);

        c.gridx = 1;
        // Initialize with current value from ConfigVariables
        JTextField pathField = new JTextField(ConfigVariables.getString(configKey), 25);
        JButton browseButton = new JButton("Browse...");

        browseButton.addActionListener(e -> {
            String selectedPath = null;
            String dialogTitle = "Select " + labelText.replace(":", "");
            if (isDirectory) {
                DirectoryChooser dc = new DirectoryChooser(dialogTitle);
                selectedPath = dc.getDirectory();
            } else {
                OpenDialog od = new OpenDialog(dialogTitle, null);
                String directory = od.getDirectory();
                String fileName = od.getFileName();
                if (directory != null && fileName != null) {
                    selectedPath = new File(directory, fileName).getPath();
                }
            }
            if (selectedPath != null) {
                pathField.setText(selectedPath);
            }
        });

        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pathPanel.add(pathField);
        pathPanel.add(browseButton);
        panel.add(pathPanel, c);

        // Use the configKey's name() as the key for the controls map
        controls.put(configKey.name(), pathField);
    }


    /**
     * Creates the parameters panel for XGBoost settings
     */
    private static JPanel createParametersPanel() {
        JPanel panel = createPanel("XGBoost Parameters");

        // Add sliders for XGBoost parameters
        addSlider(panel, "Learning Rate:", 0.0f, 0.5f, 0.01f, ConfigKey.LEARNING_RATE);
        addSlider(panel, "Max Depth:", 2, 10, 1, ConfigKey.MAX_DEPTH);
        addSlider(panel, "Number of Trees:", 100, 4000, 50, ConfigKey.N_TREES);
        addSlider(panel, "Min Child Weight:", 0, 10, 1, ConfigKey.MIN_CHILD_WEIGHT);
        addSlider(panel, "Cells Subsample:", 0.5f, 1.0f, 0.05f, ConfigKey.CELLS_SUBSAMPLE);
        addSlider(panel, "Features Subsample:", 0.5f, 1.0f, 0.05f, ConfigKey.FEATURES_SUBSAMPLE);
        addSlider(panel, "Lambda:", 0.0f, 10.0f, 0.1f, ConfigKey.LAMBDA);
        addSlider(panel, "Alpha:", 0.0f, 10.0f, 0.1f, ConfigKey.ALPHA);
        addSlider(panel, "Gamma:", 0.0f, 10.0f, 0.1f, ConfigKey.GAMMA);

        return panel;
    }

    /**
     * Creates the features panel for selecting which features to use
     */
    private static JPanel createFeaturesPanel() {
        JPanel panel = createPanel("Feature Selection");

        // Create a panel with 3 columns for cell, nucleus, cytoplasm
        JPanel columnsPanel = new JPanel(new GridLayout(1, 3, 10, 0));

        // Create scroll panes for each column to handle the large number of features
        JScrollPane cellScroll = new JScrollPane();
        JScrollPane nucleusScroll = new JScrollPane();
        JScrollPane cytoplasmScroll = new JScrollPane();

        // Cell features column
        JPanel cellPanel = new JPanel();
        cellPanel.setLayout(new BoxLayout(cellPanel, BoxLayout.Y_AXIS));
        cellPanel.setBorder(BorderFactory.createTitledBorder("Cell"));
        addFeatureCheckboxes(cellPanel, "cell");
        cellScroll.setViewportView(cellPanel);
        columnsPanel.add(cellScroll);

        // Nucleus features column
        JPanel nucleusPanel = new JPanel();
        nucleusPanel.setLayout(new BoxLayout(nucleusPanel, BoxLayout.Y_AXIS));
        nucleusPanel.setBorder(BorderFactory.createTitledBorder("Nucleus"));
        addFeatureCheckboxes(nucleusPanel, "nucleus");
        nucleusScroll.setViewportView(nucleusPanel);
        columnsPanel.add(nucleusScroll);

        // Cytoplasm features column
        JPanel cytoplasmPanel = new JPanel();
        cytoplasmPanel.setLayout(new BoxLayout(cytoplasmPanel, BoxLayout.Y_AXIS));
        cytoplasmPanel.setBorder(BorderFactory.createTitledBorder("Cytoplasm"));
        addFeatureCheckboxes(cytoplasmPanel, "cytoplasm");
        cytoplasmScroll.setViewportView(cytoplasmPanel);
        columnsPanel.add(cytoplasmScroll);

        // Add the columns panel to the main panel
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        panel.add(columnsPanel, c);

        return panel;
    }

    /**
     * Creates the output panel for debug settings
     */
    private static JPanel createOutputPanel() {
        JPanel panel = createPanel("Output Settings");

        // Create a panel for the CSV format toggle
        JPanel formatTogglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        formatTogglePanel.setBorder(BorderFactory.createTitledBorder("CSV Format"));

        // Create the toggle switch component
        JLabel formatLabel = new JLabel("CSV Format:");
        JLabel usFormatLabel = new JLabel("US Format");
        usFormatLabel.setForeground(Color.GRAY);
        JLabel euFormatLabel = new JLabel("EU Format");
        euFormatLabel.setForeground(Color.GRAY);

        // Get current format setting
        boolean isEuFormat = ConfigVariables.getBoolean(ConfigVariables.ConfigKey.USE_EU_FORMAT);

        // Create the toggle switch
        FormatToggleSwitch toggleSwitch = new FormatToggleSwitch(isEuFormat);
        toggleSwitch.setToolTipText("Toggle between US format (comma separator, dot decimal) and EU format (semicolon separator, comma decimal)");

        // Update label colors based on current selection
        if (isEuFormat) {
            euFormatLabel.setForeground(Color.BLACK);
            euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.BOLD));
        } else {
            usFormatLabel.setForeground(Color.BLACK);
            usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.BOLD));
        }

        // Add components to the format panel
        formatTogglePanel.add(formatLabel);
        formatTogglePanel.add(usFormatLabel);
        formatTogglePanel.add(toggleSwitch);
        formatTogglePanel.add(euFormatLabel);

        // Add listener to update labels when toggle changes
        toggleSwitch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleSwitch.toggle();
                boolean isEuFormat = toggleSwitch.isOn();

                // Update label colors
                if (isEuFormat) {
                    euFormatLabel.setForeground(Color.BLACK);
                    euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.BOLD));
                    usFormatLabel.setForeground(Color.GRAY);
                    usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.PLAIN));
                } else {
                    usFormatLabel.setForeground(Color.BLACK);
                    usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.BOLD));
                    euFormatLabel.setForeground(Color.GRAY);
                    euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.PLAIN));
                }
            }
        });

        // Add checkbox for debug messages
        JCheckBox debugCheckBox = new JCheckBox("Show Debug Messages");
        debugCheckBox.setSelected(Debug.getShowDebug());

        GridBagConstraints c = createConstraints();
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;

        // Add the format toggle panel first
        panel.add(formatTogglePanel, c);

        // Then add the debug checkbox
        c.gridy = GridBagConstraints.RELATIVE;
        panel.add(debugCheckBox, c);

        // Store the components in controls map
        controls.put("USE_EU_FORMAT", toggleSwitch);
        controls.put("bool_show_debug", debugCheckBox);

        return panel;
    }

    /**
     * Custom toggle switch component for CSV format selection
     */
    private static class FormatToggleSwitch extends JPanel {
        private boolean isOn;
        private final Color ON_COLOR = new Color(52, 152, 219); // Light blue color
        private final Color OFF_COLOR = new Color(149, 165, 166);
        private final int WIDTH = 40; // Smaller width
        private final int HEIGHT = 20; // Smaller height

        public FormatToggleSwitch(boolean initialState) {
            this.isOn = initialState;
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setOpaque(false);
        }

        public boolean isOn() {
            return isOn;
        }

        public void toggle() {
            isOn = !isOn;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw background
            g2d.setColor(isOn ? ON_COLOR : OFF_COLOR);
            g2d.fillRoundRect(0, 0, WIDTH, HEIGHT, HEIGHT, HEIGHT);

            // Draw border
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, HEIGHT, HEIGHT);

            // Draw toggle button
            int toggleX = isOn ? WIDTH - HEIGHT : 0;
            g2d.setColor(Color.WHITE);
            g2d.fillOval(toggleX, 0, HEIGHT, HEIGHT);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawOval(toggleX, 0, HEIGHT - 1, HEIGHT - 1);

            g2d.dispose();
        }
    }

    private static JPanel createPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private static void addFeatureCheckbox(JPanel panel, String label, String key) {
        JCheckBox checkBox = new JCheckBox(label);

        // Initialize checkbox with the current value from ConfigVariables
        boolean isSelected = getFeatureValue(key);
        checkBox.setSelected(isSelected);

        panel.add(checkBox);
        controls.put(key, checkBox);
    }


    private static Optional<ConfigKey> findConfigKeyFromString(String uiKey) {
        if (uiKey == null || uiKey.isEmpty()) {
            return Optional.empty();
        }

        // Attempt to match based on common patterns (feature or direct parameter)
        String potentialEnumName = uiKey.toUpperCase();
        String potentialFeatureEnumName = "FEATURE_" + potentialEnumName;

        for (ConfigKey configKey : ConfigKey.values()) {
            String enumName = configKey.name();
            // Direct match (e.g., LEARNING_RATE vs learning_rate)
            if (enumName.equalsIgnoreCase(potentialEnumName)) {
                return Optional.of(configKey);
            }
            // Feature match (e.g., FEATURE_CELL_AREA vs cell_area)
            if (enumName.equalsIgnoreCase(potentialFeatureEnumName)) {
                return Optional.of(configKey);
            }

        }

        Debug.log("Warning: Could not find ConfigKey for UI key: " + uiKey);
        return Optional.empty();
    }

    private static boolean getFeatureValue(String key) {
        Optional<ConfigKey> configKeyOpt = findConfigKeyFromString(key);
        if (configKeyOpt.isPresent()) {
            ConfigKey configKey = configKeyOpt.get();
            // Ensure the found key is actually a boolean type
            if (configKey.getDefaultValue() instanceof Boolean) {
                return ConfigVariables.getBoolean(configKey);
            } else {
                Debug.log("Error: ConfigKey " + configKey.name() + " found for UI key '" + key + "' is not a Boolean type.");
                // Return default boolean value from the key if possible, otherwise default to true
                return configKey.getDefaultValue() instanceof Boolean ? configKey.getDefaultBoolean() : true;
            }
        }
        // Default to true if key not found or type mismatch
        return true;
    }

    /**
     * Adds all feature checkboxes to a panel
     */
    private static void addFeatureCheckboxes(JPanel panel, String prefix) {
        // Morphological and spatial features
        addFeatureCheckbox(panel, "Vessel Distance", prefix + "_vessel_distance");
        addFeatureCheckbox(panel, "Neighbor Count", prefix + "_neighbor_count");
        addFeatureCheckbox(panel, "Closest Neighbor Distance", prefix + "_closest_neighbor_distance");
        addFeatureCheckbox(panel, "Area", prefix + "_area");
        addFeatureCheckbox(panel, "Perim.", prefix + "_perim");
        addFeatureCheckbox(panel, "Width", prefix + "_width");
        addFeatureCheckbox(panel, "Height", prefix + "_height");
        addFeatureCheckbox(panel, "Major", prefix + "_major");
        addFeatureCheckbox(panel, "Minor", prefix + "_minor");
        addFeatureCheckbox(panel, "Angle", prefix + "_angle");
        addFeatureCheckbox(panel, "Circ.", prefix + "_circ");
        addFeatureCheckbox(panel, "IntDen", prefix + "_intden");
        addFeatureCheckbox(panel, "Feret", prefix + "_feret");
        addFeatureCheckbox(panel, "FeretX", prefix + "_feretx");
        addFeatureCheckbox(panel, "FeretY", prefix + "_ferety");
        addFeatureCheckbox(panel, "FeretAngle", prefix + "_feretangle");
        addFeatureCheckbox(panel, "MinFeret", prefix + "_minferet");
        addFeatureCheckbox(panel, "AR", prefix + "_ar");
        addFeatureCheckbox(panel, "Round", prefix + "_round");
        addFeatureCheckbox(panel, "Solidity", prefix + "_solidity");

        // Intensity features
        addFeatureCheckbox(panel, "Mean", prefix + "_mean");
        addFeatureCheckbox(panel, "StdDev", prefix + "_stddev");
        addFeatureCheckbox(panel, "Mode", prefix + "_mode");
        addFeatureCheckbox(panel, "Min", prefix + "_min");
        addFeatureCheckbox(panel, "Max", prefix + "_max");
        addFeatureCheckbox(panel, "Median", prefix + "_median");
        addFeatureCheckbox(panel, "Skew", prefix + "_skew");
        addFeatureCheckbox(panel, "Kurt", prefix + "_kurt");

        // Hematoxylin features
        addFeatureCheckbox(panel, "Hema_Mean", prefix + "_hema_mean");
        addFeatureCheckbox(panel, "Hema_StdDev", prefix + "_hema_stddev");
        addFeatureCheckbox(panel, "Hema_Mode", prefix + "_hema_mode");
        addFeatureCheckbox(panel, "Hema_Min", prefix + "_hema_min");
        addFeatureCheckbox(panel, "Hema_Max", prefix + "_hema_max");
        addFeatureCheckbox(panel, "Hema_Median", prefix + "_hema_median");
        addFeatureCheckbox(panel, "Hema_Skew", prefix + "_hema_skew");
        addFeatureCheckbox(panel, "Hema_Kurt", prefix + "_hema_kurt");

        // Eosin features
        addFeatureCheckbox(panel, "Eosin_Mean", prefix + "_eosin_mean");
        addFeatureCheckbox(panel, "Eosin_StdDev", prefix + "_eosin_stddev");
        addFeatureCheckbox(panel, "Eosin_Mode", prefix + "_eosin_mode");
        addFeatureCheckbox(panel, "Eosin_Min", prefix + "_eosin_min");
        addFeatureCheckbox(panel, "Eosin_Max", prefix + "_eosin_max");
        addFeatureCheckbox(panel, "Eosin_Median", prefix + "_eosin_median");
        addFeatureCheckbox(panel, "Eosin_Skew", prefix + "_eosin_skew");
        addFeatureCheckbox(panel, "Eosin_Kurt", prefix + "_eosin_kurt");
    }

    /**
     * Adds a slider for numeric (int or float) values.
     * Determines type based on the ConfigKey's default value.
     * Stores the JSlider in the controls map using the ConfigKey's name as the map key.
     */
    private static void addSlider(JPanel panel, String label, Number min, Number max, Number step, ConfigKey configKey) {
        GridBagConstraints c = createConstraints();
        panel.add(new JLabel(label), c);

        c.gridx = 1;

        Object defaultValue = configKey.getDefaultValue();
        boolean isFloat = defaultValue instanceof Float;

        // Get current value, handling type
        Number currentValue = isFloat ? ConfigVariables.getFloat(configKey) : ConfigVariables.getInt(configKey);

        // Create slider based on type
        int sliderMin, sliderMax, sliderCurrent;
        if (isFloat) {
            sliderMin = (int) (min.floatValue() / step.floatValue());
            sliderMax = (int) (max.floatValue() / step.floatValue());
            sliderCurrent = (int) (currentValue.floatValue() / step.floatValue());
        } else {
            sliderMin = min.intValue() / step.intValue();
            sliderMax = max.intValue() / step.intValue();
            sliderCurrent = currentValue.intValue() / step.intValue();
        }

        JSlider slider = new JSlider(JSlider.HORIZONTAL, sliderMin, sliderMax, sliderCurrent);

        // Create text field for display
        JTextField valueField = new JTextField(5);
        valueField.setEditable(false);
        if (isFloat) {
            valueField.setText(String.format("%.2f", currentValue.floatValue()));
        } else {
            valueField.setText(String.valueOf(currentValue.intValue()));
        }

        // Add listener to update text field
        slider.addChangeListener(e -> {
            if (isFloat) {
                float value = slider.getValue() * step.floatValue();
                valueField.setText(String.format("%.2f", value));
            } else {
                int value = slider.getValue() * step.intValue();
                valueField.setText(String.valueOf(value));
            }
        });

        // Layout slider and text field
        JPanel sliderPanel = new JPanel(new BorderLayout(5, 0));
        sliderPanel.add(slider, BorderLayout.CENTER);
        sliderPanel.add(valueField, BorderLayout.EAST);

        panel.add(sliderPanel, c);
        controls.put(configKey.name(), slider); // Use ConfigKey name as map key
    }

    // Removed duplicate addSlider overload

    /**
     * Creates GridBagConstraints for consistent layout
     */
    private static GridBagConstraints createConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 5, 5, 5);
        return c;
    }
    /**
     * Updates settings from dialog values by iterating through the controls map.
     */
    private static void updateSettings() {
        for (Map.Entry<String, JComponent> entry : controls.entrySet()) {
            String mapKey = entry.getKey(); // Should be ConfigKey.name() or uiKey for features
            JComponent component = entry.getValue();
            Optional<ConfigKey> configKeyOpt = findConfigKeyByExactName(mapKey); // Try exact match first (for paths, sliders)

            if (!configKeyOpt.isPresent()) {
                // If not exact, try matching as a feature uiKey (e.g., "cell_area")
                configKeyOpt = findConfigKeyFromString(mapKey);
            }

            if (configKeyOpt.isPresent()) {
                ConfigKey configKey = configKeyOpt.get();
                Object valueToSet = null;
                Object defaultValue = configKey.getDefaultValue(); // Get default for type checking/conversion

                try {
                    // Extract value based on component type
                    if (component instanceof JTextField) {
                        valueToSet = ((JTextField) component).getText();
                    } else if (component instanceof JCheckBox) {
                        valueToSet = ((JCheckBox) component).isSelected();
                    } else if (component instanceof FormatToggleSwitch) {
                        valueToSet = ((FormatToggleSwitch) component).isOn();
                    } else if (component instanceof JSlider) {
                        JSlider slider = (JSlider) component;
                        // Determine step based on ConfigKey type (assuming steps were set correctly in addSlider)
                        if (defaultValue instanceof Float) {
                            // Infer step - THIS IS FRAGILE. Consider storing step with slider or using a custom component.
                            float step = 0.01f; // Default guess
                            if (configKey == ConfigKey.TRAIN_RATIO || configKey == ConfigKey.CELLS_SUBSAMPLE || configKey == ConfigKey.FEATURES_SUBSAMPLE) step = 0.05f;
                            else if (configKey == ConfigKey.LAMBDA || configKey == ConfigKey.ALPHA || configKey == ConfigKey.GAMMA) step = 0.1f;
                            // Add other float sliders if their step differs from 0.01f
                            valueToSet = slider.getValue() * step;
                        } else if (defaultValue instanceof Integer) {
                            int step = 1; // Default guess
                            if (configKey == ConfigKey.N_TREES) step = 50;
                            // Add other int sliders if their step differs from 1
                            valueToSet = slider.getValue() * step;
                        }
                        // Removed duplicate Integer check
                    }
                } catch (Exception ex) {
                    Debug.log("Error processing control for key '" + mapKey + "' (" + configKey.name() + "): " + ex.getMessage());
                }

                    // Add other component types if needed

                    // Set value in ConfigVariables if extracted successfully
                    if (valueToSet != null) {
                        // Perform necessary type conversion before setting
                        Object finalValue = convertValueToType(valueToSet, defaultValue.getClass());
                        if (finalValue != null) {
                            ConfigVariables.setValue(configKey, finalValue);
                        } else {
                            Debug.log("Error: Could not convert value '" + valueToSet + "' for key " + configKey.name());
                        }
                    }

            } else {
                // Handle special cases like debug checkbox which don't map directly to ConfigKey enum
                if (mapKey.equals("bool_show_debug") && component instanceof JCheckBox) {
                     Debug.setShowDebug(((JCheckBox) component).isSelected());
                     Debug.log("Updated Debug.showDebug to: " + Debug.getShowDebug());
                } else if (mapKey.equals("USE_EU_FORMAT") && component instanceof FormatToggleSwitch) {
                    // This key IS in ConfigKey, but the logic below handles it.
                    // If findConfigKeyByExactName fails, it means the mapKey doesn't match the enum name exactly.
                    // We need to ensure ConfigKey.USE_EU_FORMAT exists and handle it.
                    Optional<ConfigKey> euFormatKeyOpt = findConfigKeyByExactName("USE_EU_FORMAT");
                    if (euFormatKeyOpt.isPresent()) {
                         ConfigKey euFormatKey = euFormatKeyOpt.get();
                         boolean isEu = ((FormatToggleSwitch) component).isOn();
                         ConfigVariables.setValue(euFormatKey, isEu);
                         Debug.log("Updated USE_EU_FORMAT to: " + isEu);
                    } else {
                         Debug.log("Warning: ConfigKey.USE_EU_FORMAT not found during update.");
                    }

                } else {
                    Debug.log("Warning: Could not find ConfigKey for control map key: " + mapKey + " during update.");
                }
            }
        }
        // Removed separate handling for debug checkbox as it's now inside the loop's else block
    }

    private static Object convertValueToType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value; // Already correct type

        String sValue = String.valueOf(value);
        try {
            if (targetType == String.class) {
                return sValue;
            } else if (targetType == Integer.class || targetType == int.class) {
                // Handle potential float/double from sliders before parsing
                if (value instanceof Number) return ((Number)value).intValue();
                return Integer.parseInt(sValue);
            } else if (targetType == Float.class || targetType == float.class) {
                if (value instanceof Number) return ((Number)value).floatValue();
                return Float.parseFloat(sValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                // Handle boolean conversion carefully
                if (value instanceof Boolean) return value;
                return Boolean.parseBoolean(sValue); // Standard parsing
            }
            // Add other type conversions if needed
        } catch (NumberFormatException nfe) {
            Debug.log("Conversion Error: Cannot convert '" + sValue + "' to " + targetType.getSimpleName());
            return null; // Return null on conversion error
        }
        return null; // Type not handled
    }

    private static Optional<ConfigKey> findConfigKeyByExactName(String exactName) {
        try {
            return Optional.of(ConfigKey.valueOf(exactName));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}