package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.sebastianmicu.scheli.Debug;
import com.sebastianmicu.scheli.classifier.ConfigVariables.ConfigKey;

/**
 * User interface for XGBoost classifier classification settings.
 * Provides panels for input/output paths and output format.
 *
 * @author Cristian Sebastian Micu
 */
public class ClassifySettingsUI {

    private static JDialog dialog;
    private static final Map<String, JComponent> controls = new HashMap<>();

    /**
     * Shows the settings dialog to the user.
     *
     * @return true if OK was clicked, false otherwise.
     */
    public static boolean showDialog() {
        controls.clear();
        dialog = null;

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final boolean[] result = new boolean[1];
        result[0] = false;

        SwingUtilities.invokeLater(() -> {
            try {
                dialog = new JDialog((Frame)null, "XGBoost Classifier - Classify Data Settings", true);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        latch.countDown();
                    }
                });

                JTabbedPane tabbedPane = new JTabbedPane();
                tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

                // Add tabs
                tabbedPane.addTab("Main", createMainPanel());
                tabbedPane.addTab("Output", createOutputPanel()); // Keep output format settings

                // Buttons
                JPanel buttonPanel = new JPanel();
                JButton okButton = new JButton("OK");
                JButton cancelButton = new JButton("Cancel");
                JButton resetButton = new JButton("Reset to Defaults"); // Keep reset functionality

                okButton.addActionListener(e -> {
                    updateSettings();
                    ConfigVariables.savePreferences();
                    ConfigVariables.printAllValues(); // Log settings used
                    result[0] = true;
                    dialog.dispose();
                });

                cancelButton.addActionListener(e -> {
                    result[0] = false;
                    dialog.dispose();
                });

                resetButton.addActionListener(e -> {
                    if (JOptionPane.showConfirmDialog(dialog,
                            "Reset classification settings to default values?", // Adjusted message
                            "Reset Settings",
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        // Reset only relevant preferences? Or all? For now, reset all.
                        ConfigVariables.resetAllPreferences();
                        dialog.dispose();
                        result[0] = false;
                    }
                });

                buttonPanel.add(resetButton);
                buttonPanel.add(cancelButton);
                buttonPanel.add(okButton);

                // Main panel layout
                JPanel mainPanel = new JPanel(new BorderLayout());
                mainPanel.add(tabbedPane, BorderLayout.CENTER);
                mainPanel.add(buttonPanel, BorderLayout.SOUTH);
                mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                dialog.add(mainPanel);
                dialog.setMinimumSize(new Dimension(500, 300)); // Can be smaller than training UI
                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);

            } catch (Exception e) {
                IJ.handleException(e);
                result[0] = false;
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        dialog = null;
        return result[0];
    }

    /**
     * Creates the main panel with input/output paths for classification.
     */
    private static JPanel createMainPanel() {
        JPanel panel = createPanel("Classification Paths");

        // Add path inputs specific to classification
        // TODO: Add ConfigKey.PATH_MODEL_FILE to ConfigVariables enum
        addPathInput(panel, "Model File (.bin):", ConfigKey.PATH_MODEL_FILE, false); // File chooser for model
        addPathInput(panel, "Input CSV Directory:", ConfigKey.PATH_CSV_DATA, true);  // Directory with data to classify
        addPathInput(panel, "Output Directory:", ConfigKey.PATH_OUTPUT_DIR, true); // Directory for results CSV
        addPathInput(panel, "Class Details JSON:", ConfigKey.PATH_JSON_DATA, false); // JSON for output formatting

        return panel;
    }

    /**
     * Creates the output panel for debug and format settings.
     * Copied from original SettingsUI, as these are relevant for classification output.
     */
    private static JPanel createOutputPanel() {
        JPanel panel = createPanel("Output Settings");

        // CSV Format Toggle
        JPanel formatTogglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        formatTogglePanel.setBorder(BorderFactory.createTitledBorder("CSV Format"));
        JLabel formatLabel = new JLabel("CSV Format:");
        JLabel usFormatLabel = new JLabel("US Format");
        usFormatLabel.setForeground(Color.GRAY);
        JLabel euFormatLabel = new JLabel("EU Format");
        euFormatLabel.setForeground(Color.GRAY);
        boolean isEuFormat = ConfigVariables.getBoolean(ConfigVariables.ConfigKey.USE_EU_FORMAT);
        FormatToggleSwitch toggleSwitch = new FormatToggleSwitch(isEuFormat);
        toggleSwitch.setToolTipText("Toggle between US format (comma separator, dot decimal) and EU format (semicolon separator, comma decimal)");

        if (isEuFormat) {
            euFormatLabel.setForeground(Color.BLACK);
            euFormatLabel.setFont(euFormatLabel.getFont().deriveFont(Font.BOLD));
        } else {
            usFormatLabel.setForeground(Color.BLACK);
            usFormatLabel.setFont(usFormatLabel.getFont().deriveFont(Font.BOLD));
        }

        formatTogglePanel.add(formatLabel);
        formatTogglePanel.add(usFormatLabel);
        formatTogglePanel.add(toggleSwitch);
        formatTogglePanel.add(euFormatLabel);

        toggleSwitch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleSwitch.toggle();
                boolean isEu = toggleSwitch.isOn();
                if (isEu) {
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

        // Debug Messages Checkbox
        JCheckBox debugCheckBox = new JCheckBox("Show Debug Messages");
        debugCheckBox.setSelected(Debug.getShowDebug());

        // Layout
        GridBagConstraints c = createConstraints();
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(formatTogglePanel, c);
        c.gridy = GridBagConstraints.RELATIVE;
        panel.add(debugCheckBox, c);

        // Store controls
        controls.put("USE_EU_FORMAT", toggleSwitch);
        controls.put("bool_show_debug", debugCheckBox);

        return panel;
    }

    /**
     * Helper method to add a path input field (JTextField + Browse button) to a panel.
     * Copied from original SettingsUI.
     */
    private static void addPathInput(JPanel panel, String labelText, ConfigKey configKey, boolean isDirectory) {
        GridBagConstraints c = createConstraints();
        panel.add(new JLabel(labelText), c);

        c.gridx = 1;
        JTextField pathField = new JTextField(ConfigVariables.getString(configKey), 25);
        JButton browseButton = new JButton("Browse...");

        browseButton.addActionListener(e -> {
            String selectedPath = null;
            String dialogTitle = "Select " + labelText.replace(":", "");
            if (isDirectory) {
                DirectoryChooser dc = new DirectoryChooser(dialogTitle);
                selectedPath = dc.getDirectory();
            } else {
                // Use OpenDialog for files
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

        controls.put(configKey.name(), pathField);
    }

    /**
     * Creates a basic panel with GridBagLayout. Copied from original SettingsUI.
     */
    private static JPanel createPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title)); // Add title border
        // panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Original border
        return panel;
    }

    /**
     * Creates GridBagConstraints for consistent layout. Copied from original SettingsUI.
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
     * Updates settings from dialog values. Simplified for classification UI.
     */
    private static void updateSettings() {
        for (Map.Entry<String, JComponent> entry : controls.entrySet()) {
            String mapKey = entry.getKey();
            JComponent component = entry.getValue();
            Optional<ConfigKey> configKeyOpt = findConfigKeyByExactName(mapKey);

            if (configKeyOpt.isPresent()) {
                ConfigKey configKey = configKeyOpt.get();
                Object valueToSet = null;
                Object defaultValue = configKey.getDefaultValue();

                try {
                    if (component instanceof JTextField) {
                        valueToSet = ((JTextField) component).getText();
                    } else if (component instanceof FormatToggleSwitch) {
                        valueToSet = ((FormatToggleSwitch) component).isOn();
                    }
                    // No sliders or feature checkboxes in this UI
                } catch (Exception ex) {
                    Debug.log("Error processing control for key '" + mapKey + "' (" + configKey.name() + "): " + ex.getMessage());
                }

                if (valueToSet != null) {
                    Object finalValue = convertValueToType(valueToSet, defaultValue.getClass());
                    if (finalValue != null) {
                        ConfigVariables.setValue(configKey, finalValue);
                    } else {
                        Debug.log("Error: Could not convert value '" + valueToSet + "' for key " + configKey.name());
                    }
                }
            } else {
                 // Handle special cases like debug checkbox
                if (mapKey.equals("bool_show_debug") && component instanceof JCheckBox) {
                     Debug.setShowDebug(((JCheckBox) component).isSelected());
                     Debug.log("Updated Debug.showDebug to: " + Debug.getShowDebug());
                } else {
                    Debug.log("Warning: Could not find ConfigKey for control map key: " + mapKey + " during update.");
                }
            }
        }
    }

    /**
     * Converts a value to the target type. Copied from original SettingsUI.
     */
    private static Object convertValueToType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        String sValue = String.valueOf(value);
        try {
            if (targetType == String.class) {
                return sValue;
            } else if (targetType == Integer.class || targetType == int.class) {
                if (value instanceof Number) return ((Number)value).intValue();
                return Integer.parseInt(sValue);
            } else if (targetType == Float.class || targetType == float.class) {
                if (value instanceof Number) return ((Number)value).floatValue();
                return Float.parseFloat(sValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                 if (value instanceof Boolean) return value;
                 return Boolean.parseBoolean(sValue);
            }
        } catch (NumberFormatException nfe) {
            Debug.log("Conversion Error: Cannot convert '" + sValue + "' to " + targetType.getSimpleName());
            return null;
        }
        return null;
    }

    /**
     * Finds ConfigKey enum by exact name match. Copied from original SettingsUI.
     */
    private static Optional<ConfigKey> findConfigKeyByExactName(String exactName) {
        try {
            return Optional.of(ConfigKey.valueOf(exactName));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /**
     * Custom toggle switch component. Copied from original SettingsUI.
     */
    private static class FormatToggleSwitch extends JPanel {
        private boolean isOn;
        private final Color ON_COLOR = new Color(52, 152, 219);
        private final Color OFF_COLOR = new Color(149, 165, 166);
        private final int WIDTH = 40;
        private final int HEIGHT = 20;

        public FormatToggleSwitch(boolean initialState) {
            this.isOn = initialState;
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setOpaque(false);
        }

        public boolean isOn() { return isOn; }
        public void toggle() { isOn = !isOn; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(isOn ? ON_COLOR : OFF_COLOR);
            g2d.fillRoundRect(0, 0, WIDTH, HEIGHT, HEIGHT, HEIGHT);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, HEIGHT, HEIGHT);
            int toggleX = isOn ? WIDTH - HEIGHT : 0;
            g2d.setColor(Color.WHITE);
            g2d.fillOval(toggleX, 0, HEIGHT, HEIGHT);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawOval(toggleX, 0, HEIGHT - 1, HEIGHT - 1);
            g2d.dispose();
        }
    }
}