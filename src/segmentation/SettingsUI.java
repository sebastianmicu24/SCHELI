package com.sebastianmicu.scheli.segmentation;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser; 
import com.sebastianmicu.scheli.Debug;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class SettingsUI {
    
    private static JDialog dialog;
    private static final Map<String, JComponent> controls = new HashMap<>();
    
    // Show settings dialog to user
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
                dialog = new JDialog((Frame)null, "H&E Liver Segmentation Settings", true);
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
                
                // Add tabs for each category - Paths first
                tabbedPane.addTab("Paths", createPathsPanel());
                tabbedPane.addTab("Percentiles", createPercentilesPanel());
                tabbedPane.addTab("Thresholds", createThresholdsPanel());
                tabbedPane.addTab("Sizes", createSizesPanel());
                tabbedPane.addTab("Colors", createColorsPanel());
                tabbedPane.addTab("Output", createOutputPanel());
                
                // Create buttons
                JPanel buttonPanel = new JPanel();
                JButton okButton = new JButton("OK");
                JButton cancelButton = new JButton("Cancel");
                JButton resetButton = new JButton("Reset to Defaults");
                
                // Set button actions
                okButton.addActionListener(e -> {
                    updateSettings();
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
                        ConfigVariables.resetAllPreferences();
                        dialog.dispose();
                        
                        // Don't reopen dialog here, let the main thread handle it
                        result[0] = false;
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
    
    private static JPanel createPercentilesPanel() {
        JPanel panel = createPanel("Percentiles");
        
        addNumberField(panel, "Low percentile (0 - 100):", ConfigVariables.getPercentileLow(), "percentile_low");
        addNumberField(panel, "High percentile (0 - 100):", ConfigVariables.getPercentileHigh(), "percentile_high");
        addNumberField(panel, "Tiles:", ConfigVariables.getPercentileTiles(), "percentile_tiles");
        
        return panel;
    }
    
    private static JPanel createThresholdsPanel() {
        JPanel panel = createPanel("Thresholds");
        
        addNumberField(panel, "Background threshold (0 - 100%):", ConfigVariables.getThresholdBackground(), "threshold_background");
        addNumberField(panel, "Nuclear threshold (0 - 100%):", ConfigVariables.getThresholdNuclei(), "threshold_nuclei");
        
        return panel;
    }
    
    private static JPanel createPathsPanel() {
        JPanel panel = createPanel("Paths");
        
        // Input directory with browse button
        GridBagConstraints c = createConstraints();
        panel.add(new JLabel("Input directory:"), c);
        
        c.gridx = 1;
        JTextField inputField = new JTextField(ConfigVariables.getPathInput(), 20);
        
        JButton inputBrowseButton = new JButton("Browse...");
        inputBrowseButton.addActionListener(e -> {
            DirectoryChooser dc = new DirectoryChooser("Select Input Directory");
            String dir = dc.getDirectory();
            if (dir != null) {
                inputField.setText(dir);
            }
        });
        
        JPanel inputPathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        inputPathPanel.add(inputField);
        inputPathPanel.add(inputBrowseButton);
        panel.add(inputPathPanel, c);
        controls.put("path_input", inputField);
        
        // Output directory with browse button
        c = createConstraints();
        panel.add(new JLabel("Output directory:"), c);
        
        c.gridx = 1;
        JTextField outputField = new JTextField(ConfigVariables.getPathOutput(), 20);
        
        JButton outputBrowseButton = new JButton("Browse...");
        outputBrowseButton.addActionListener(e -> {
            DirectoryChooser dc = new DirectoryChooser("Select Output Directory");
            String dir = dc.getDirectory();
            if (dir != null) {
                outputField.setText(dir);
            }
        });
        
        JPanel outputPathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        outputPathPanel.add(outputField);
        outputPathPanel.add(outputBrowseButton);
        panel.add(outputPathPanel, c);
        controls.put("path_output", outputField);
        
        // Scale
        addNumberField(panel, "Scale (μm/pixel):", ConfigVariables.getPathScale(), "path_scale");
        
        return panel;
    }
    
    private static JPanel createSizesPanel() {
        JPanel panel = createPanel("Sizes");
        
        // Use raw values (without scale) for UI display
        addNumberField(panel, "Min vessel size (μm²):", ConfigVariables.getRawSizeMinVessel(), "size_min_vessel");
        addNumberField(panel, "Max vessel size (μm²):", ConfigVariables.getRawSizeMaxVessel(), "size_max_vessel");
        addNumberField(panel, "Min nucleus size (μm²):", ConfigVariables.getRawSizeMinNucleus(), "size_min_nucleus");
        addNumberField(panel, "Max nucleus size (μm²):", ConfigVariables.getRawSizeMaxNucleus(), "size_max_nucleus");
        addNumberField(panel, "Neighbor radius (μm):", ConfigVariables.getRawNeighborRadius(), "neighbor_radius");
        
        // Add a label explaining that size values are in pixels but will be scaled during analysis
        GridBagConstraints c = createConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
    
        
        return panel;
    }
    
    private static JPanel createColorsPanel() {
        JPanel panel = createPanel("Colors");
        
        addColorField(panel, "Central Nuclei color:", ConfigVariables.getColorCentralNuclei(), "color_central_nuclei");
        addColorField(panel, "Central Vessels color:", ConfigVariables.getColorCentralVessels(), "color_central_vessels");
        addColorField(panel, "Border Nuclei color:", ConfigVariables.getColorBorderNuclei(), "color_border_nuclei");
        addColorField(panel, "Border Vessels color:", ConfigVariables.getColorBorderVessels(), "color_border_vessels");
        addColorField(panel, "Central Cytoplasm color:", ConfigVariables.getColorCentralCytoplasm(), "color_central_cytoplasm");
        addColorField(panel, "Border Cytoplasm color:", ConfigVariables.getColorBorderCytoplasm(), "color_border_cytoplasm");
        addColorField(panel, "Central Cells color:", ConfigVariables.getColorCentralCells(), "color_central_cells");
        addColorField(panel, "Border Cells color:", ConfigVariables.getColorBorderCells(), "color_border_cells");
        
        return panel;
    }
    private static JPanel createOutputPanel() {
        JPanel panel = createPanel("Output");
        
        // Create a panel for the CSV format toggle
        JPanel formatTogglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        formatTogglePanel.setBorder(BorderFactory.createTitledBorder("CSV Format"));
        
        // Create the toggle switch component
        JLabel formatLabel = new JLabel("Format:");
        JLabel usFormatLabel = new JLabel("US");
        usFormatLabel.setForeground(Color.GRAY);
        usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(11f));
        
        JLabel euFormatLabel = new JLabel("EU");
        euFormatLabel.setForeground(Color.GRAY);
        euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(11f));
        
        // Get current format settings
        boolean isEuFormat = ConfigVariables.getUseSemicolons() && ConfigVariables.getUseCommaForDecimals();
        
        // Create the toggle switch
        FormatToggleSwitch toggleSwitch = new FormatToggleSwitch(isEuFormat);
        toggleSwitch.setToolTipText("Toggle between US format (comma separator, dot decimal) and EU format (semicolon separator, comma decimal)");
        
        // Update label colors based on current selection
        if (isEuFormat) {
            euFormatLabel.setForeground(Color.BLACK);
            euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.BOLD, 11f));
        } else {
            usFormatLabel.setForeground(Color.BLACK);
            usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.BOLD, 11f));
        }
        
        // Add components to the format panel
        formatTogglePanel.add(formatLabel);
        formatTogglePanel.add(usFormatLabel);
        formatTogglePanel.add(toggleSwitch);
        formatTogglePanel.add(euFormatLabel);
        
        // Add a small description label
        JLabel descriptionLabel = new JLabel("US: comma separator, dot decimal | EU: semicolon separator, comma decimal");
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(10f));
        descriptionLabel.setForeground(Color.DARK_GRAY);
        
        // Add listener to update labels when toggle changes
        toggleSwitch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleSwitch.toggle();
                boolean isEuFormat = toggleSwitch.isOn();
                
                // Update label colors
                if (isEuFormat) {
                    euFormatLabel.setForeground(Color.BLACK);
                    euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.BOLD, 11f));
                    usFormatLabel.setForeground(Color.GRAY);
                    usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.PLAIN, 11f));
                } else {
                    usFormatLabel.setForeground(Color.BLACK);
                    usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.BOLD, 11f));
                    euFormatLabel.setForeground(Color.GRAY);
                    euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.PLAIN, 11f));
                }
            }
        });
        
        // Create a panel to hold the toggle and description
        JPanel csvFormatPanel = new JPanel(new BorderLayout());
        csvFormatPanel.add(formatTogglePanel, BorderLayout.CENTER);
        
        JPanel descriptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        descriptionPanel.add(descriptionLabel);
        csvFormatPanel.add(descriptionPanel, BorderLayout.SOUTH);
        
        // Add the format panel to the main panel
        GridBagConstraints c = createConstraints();
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(csvFormatPanel, c);
        
        // Store the toggle switch in controls map
        controls.put("format_toggle_switch", toggleSwitch);
        
        // Add other checkboxes
        addCheckBox(panel, "Save ROIs", ConfigVariables.getSaveRoi(), "bool_save_roi");
        addCheckBox(panel, "Save preview", ConfigVariables.getSavePreview(), "bool_save_preview");
        addCheckBox(panel, "Save averages", ConfigVariables.getSaveAverages(), "bool_save_averages");
        addCheckBox(panel, "Save individual cells", ConfigVariables.getSaveIndividualCells(), "bool_save_individual_cells");
        addCheckBox(panel, "Ignore border vessels", ConfigVariables.getIgnoreBorderVessels(), "bool_ignore_border_vessels");
        addCheckBox(panel, "Show Debug Messages", Debug.getShowDebug(), "bool_show_debug");
        
        return panel;
    }
    
    private static JPanel createPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }
    
    private static void addNumberField(JPanel panel, String label, Object value, String key) {
        GridBagConstraints c = createConstraints();
        panel.add(new JLabel(label), c);
        
        c.gridx = 1;
        JTextField field = new JTextField(10);
        field.setText(value.toString());
        
        // Add input verification for numbers
        field.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                String text = ((JTextField) input).getText();
                try {
                    if (text.contains(".")) {
                        Double.parseDouble(text);
                    } else {
                        Integer.parseInt(text);
                    }
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        });
        
        panel.add(field, c);
        controls.put(key, field);
    }
    
    private static void addCheckBox(JPanel panel, String label, boolean value, String key) {
        GridBagConstraints c = createConstraints();
        c.gridwidth = 2;
        
        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setSelected(value);
        panel.add(checkBox, c);
        controls.put(key, checkBox);
    }
    
    private static void addColorField(JPanel panel, String label, String value, String key) {
        GridBagConstraints c = createConstraints();
        panel.add(new JLabel(label), c);
        
        c.gridx = 1;
        JTextField field = new JTextField(value, 7);
        
        JButton colorButton = new JButton("Choose");
        colorButton.addActionListener(e -> {
            try {
                Color initialColor = Color.decode(field.getText());
                Color newColor = JColorChooser.showDialog(dialog, "Choose " + label, initialColor);
                if (newColor != null) {
                    String hex = String.format("#%02X%02X%02X", 
                        newColor.getRed(), newColor.getGreen(), newColor.getBlue());
                    field.setText(hex);
                }
            } catch (Exception ex) {
                // If color parsing fails, use a default color
                Color newColor = JColorChooser.showDialog(dialog, "Choose " + label, Color.WHITE);
                if (newColor != null) {
                    String hex = String.format("#%02X%02X%02X", 
                        newColor.getRed(), newColor.getGreen(), newColor.getBlue());
                    field.setText(hex);
                }
            }
        });
        
        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        colorPanel.add(field);
        colorPanel.add(colorButton);
        panel.add(colorPanel, c);
        controls.put(key, field);
    }
    
    private static GridBagConstraints createConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 5, 5, 5);
        return c;
    }
    
    // Update settings from dialog values
    private static void updateSettings() {
        try {
            Debug.log("Updating settings from dialog values...");
            
            // Update Percentiles
            updateFloatValue("percentile_low", ConfigVariables::setPercentileLow);
            updateFloatValue("percentile_high", ConfigVariables::setPercentileHigh);
            updateIntValue("percentile_tiles", ConfigVariables::setPercentileTiles);
            
            // Update Thresholds
            updateIntValue("threshold_background", ConfigVariables::setThresholdBackground);
            updateIntValue("threshold_nuclei", ConfigVariables::setThresholdNuclei);
            
            // Update Paths
            updateStringValue("path_input", ConfigVariables::setPathInput);
            updateStringValue("path_output", ConfigVariables::setPathOutput);
            updateFloatValue("path_scale", ConfigVariables::setPathScale);
            
            // Update Size
            updateFloatValue("size_min_vessel", ConfigVariables::setSizeMinVessel);
            updateFloatValue("size_max_vessel", ConfigVariables::setSizeMaxVessel);
            updateFloatValue("size_min_nucleus", ConfigVariables::setSizeMinNucleus);
            updateFloatValue("size_max_nucleus", ConfigVariables::setSizeMaxNucleus);
            updateFloatValue("neighbor_radius", ConfigVariables::setNeighborRadius);
            
            // Update Colors
            updateStringValue("color_central_nuclei", ConfigVariables::setColorCentralNuclei);
            updateStringValue("color_central_vessels", ConfigVariables::setColorCentralVessels);
            updateStringValue("color_border_nuclei", ConfigVariables::setColorBorderNuclei);
            updateStringValue("color_border_vessels", ConfigVariables::setColorBorderVessels);
            updateStringValue("color_central_cytoplasm", ConfigVariables::setColorCentralCytoplasm);
            updateStringValue("color_border_cytoplasm", ConfigVariables::setColorBorderCytoplasm);
            updateStringValue("color_central_cells", ConfigVariables::setColorCentralCells);
            updateStringValue("color_border_cells", ConfigVariables::setColorBorderCells);
            
            // Update CSV Format from toggle switch - IMPORTANT: This must be done before other settings
            FormatToggleSwitch formatToggle = (FormatToggleSwitch) controls.get("format_toggle_switch");
            if (formatToggle != null) {
                boolean isEuFormat = formatToggle.isOn();
                Debug.log("Updating CSV format to: " + (isEuFormat ? "EU Format" : "US Format"));
                
                // Explicitly set the values based on the toggle state
                if (isEuFormat) {
                    // EU Format: semicolon separator, comma decimal
                    ConfigVariables.setUseSemicolons(true);
                    ConfigVariables.setUseCommaForDecimals(true);
                    Debug.log("Set to EU Format: semicolon separator (;), comma decimal (,)");
                } else {
                    // US Format: comma separator, dot decimal
                    ConfigVariables.setUseSemicolons(false); // This means use comma separator
                    ConfigVariables.setUseCommaForDecimals(false); // This means use dot decimal
                    Debug.log("Set to US Format: comma separator (,), dot decimal (.)");
                }
            } else {
                Debug.log("Format toggle switch not found, using checkbox values instead");
                // Fallback to the old checkboxes if toggle not found
                updateBooleanValue("bool_use_semicolons", ConfigVariables::setUseSemicolons);
                updateBooleanValue("bool_use_comma_for_decimals", ConfigVariables::setUseCommaForDecimals);
            }
            
            // Update other Booleans
            updateBooleanValue("bool_save_roi", ConfigVariables::setSaveRoi);
            updateBooleanValue("bool_save_preview", ConfigVariables::setSavePreview);
            updateBooleanValue("bool_save_averages", ConfigVariables::setSaveAverages);
            updateBooleanValue("bool_save_individual_cells", ConfigVariables::setSaveIndividualCells);
            updateBooleanValue("bool_ignore_border_vessels", ConfigVariables::setIgnoreBorderVessels);
            updateBooleanValue("bool_show_debug", Debug::setShowDebug);
            
            // Log some values for debugging
            Debug.log("Updated threshold background: " + ConfigVariables.getThresholdBackground());
            Debug.log("Updated threshold nuclei: " + ConfigVariables.getThresholdNuclei());
            
            // Save preferences explicitly
            Debug.log("Saving preferences...");
            ConfigVariables.savePreferences();
            
            // Verify that preferences were saved
            Debug.log("Verifying saved preferences...");
            Debug.log("Verified threshold background: " + ConfigVariables.getThresholdBackground());
            Debug.log("Verified threshold nuclei: " + ConfigVariables.getThresholdNuclei());
            
        } catch (Exception e) {
            Debug.log("Error updating settings: " + e.getMessage());
            IJ.handleException(e);
        }
    }
    
    private static void updateFloatValue(String key, FloatSetter setter) {
        JTextField field = (JTextField) controls.get(key);
        if (field != null) {
            try {
                String text = field.getText();
                Debug.log("Updating " + key + " with value: " + text);
                float value = Float.parseFloat(text);
                setter.set(value);
                Debug.log("Updated " + key + " successfully");
            } catch (NumberFormatException e) {
                Debug.log("Error parsing float value for " + key + ": " + e.getMessage());
            }
        } else {
            Debug.log("Field not found for key: " + key);
        }
    }
    
    private static void updateIntValue(String key, IntSetter setter) {
        JTextField field = (JTextField) controls.get(key);
        if (field != null) {
            try {
                String text = field.getText();
                Debug.log("Updating " + key + " with value: " + text);
                int value = Integer.parseInt(text);
                setter.set(value);
                Debug.log("Updated " + key + " successfully");
            } catch (NumberFormatException e) {
                Debug.log("Error parsing int value for " + key + ": " + e.getMessage());
            }
        } else {
            Debug.log("Field not found for key: " + key);
        }
    }
    
    private static void updateStringValue(String key, StringSetter setter) {
        JTextField field = (JTextField) controls.get(key);
        if (field != null) {
            String text = field.getText();
            Debug.log("Updating " + key + " with value: " + text);
            setter.set(text);
            Debug.log("Updated " + key + " successfully");
        } else {
            Debug.log("Field not found for key: " + key);
        }
    }
    
    private static void updateBooleanValue(String key, BooleanSetter setter) {
        JCheckBox checkBox = (JCheckBox) controls.get(key);
        if (checkBox != null) {
            boolean selected = checkBox.isSelected();
            Debug.log("Updating " + key + " with value: " + selected);
            setter.set(selected);
            Debug.log("Updated " + key + " successfully");
        } else {
            Debug.log("CheckBox not found for key: " + key);
        }
    }
    
    // Functional interfaces for setters
    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
    
    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }
    
    @FunctionalInterface
    private interface StringSetter {
        void set(String value);
    }
    
    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
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
}
