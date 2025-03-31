package com.sebastianmicu.scheli.classifier;

import ij.IJ;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Progress bar UI for Classifier Workflows.
 * Adapted from segmentation.Progress.
 */
public class ProgressUI {
    private JFrame frame;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel elapsedTimeLabel;
    private JLabel estimatedTimeLabel;
    private int totalSteps = 0;
    private int currentStep = 0;
    private long startTime = 0;
    private Timer updateTimer;

    public ProgressUI(String title) {
        createUI(title);
        startTime = System.currentTimeMillis();

        // Timer to update time display
        updateTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateTimeInformation();
            }
        });
        updateTimer.setInitialDelay(0);
    }

    private void createUI(String title) {
        frame = new JFrame(title);
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Status label
        statusLabel = new JLabel("Initializing...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        mainPanel.add(statusLabel, BorderLayout.NORTH);

        // Progress bars panel (only one bar now)
        JPanel progressPanel = new JPanel(new GridLayout(2, 1, 0, 10)); // Reduced grid layout rows

        // Overall progress
        JPanel overallPanel = new JPanel(new BorderLayout(5, 5));
        overallPanel.setBorder(new TitledBorder("Overall Progress"));
        progressBar = new JProgressBar(0, 1); // Initial max value
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 20));
        overallPanel.add(progressBar, BorderLayout.CENTER);

        // Time information panel
        JPanel timePanel = new JPanel(new GridLayout(2, 1, 0, 5));
        timePanel.setBorder(new TitledBorder("Time Information"));

        elapsedTimeLabel = new JLabel("Elapsed: 00:00:00");
        elapsedTimeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timePanel.add(elapsedTimeLabel);

        estimatedTimeLabel = new JLabel("Estimated remaining: --:--:--");
        estimatedTimeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timePanel.add(estimatedTimeLabel);

        progressPanel.add(overallPanel);
        progressPanel.add(timePanel);
        mainPanel.add(progressPanel, BorderLayout.CENTER);

        frame.add(mainPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Prevent accidental closing
    }

    public void showProgress() {
        startTime = System.currentTimeMillis(); // Reset start time
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        updateTimer.start();
    }

    public void setTotalSteps(int total) {
        totalSteps = total;
        SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(totalSteps);
            progressBar.setString("0 / " + totalSteps);
        });
    }

    public void updateStatus(final String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            incrementStepProgress();
        });
        IJ.showStatus(status); // Update ImageJ status bar
    }

    private void incrementStepProgress() {
        currentStep++;
        final int progress = Math.min(currentStep, totalSteps);

        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(progress);
            progressBar.setString(progress + " / " + totalSteps);
        });

        // Update ImageJ progress bar
        double totalProgress = (double)progress / totalSteps;
        IJ.showProgress(totalProgress);
    }

    private void updateTimeInformation() {
        long currentTime = System.currentTimeMillis();
        long elapsedTimeMs = currentTime - startTime;
        String elapsedTimeStr = formatTimeDirectly(elapsedTimeMs);

        String estimatedTimeStr = "--:--:--";
        if (totalSteps > 0 && currentStep > 0) {
            double progressFraction = (double)currentStep / totalSteps;
            if (progressFraction > 0) {
                long estimatedTotalTimeMs = (long)(elapsedTimeMs / progressFraction);
                long estimatedRemainingTimeMs = estimatedTotalTimeMs - elapsedTimeMs;
                if (estimatedRemainingTimeMs >= 0) { // Prevent negative estimates
                    estimatedTimeStr = formatTimeDirectly(estimatedRemainingTimeMs);
                } else {
                     estimatedTimeStr = "00:00:00"; // Or keep as "--:--:--" if preferred
                }
            }
        }

        final String elapsed = elapsedTimeStr;
        final String estimated = estimatedTimeStr;

        SwingUtilities.invokeLater(() -> {
            elapsedTimeLabel.setText("Elapsed: " + elapsed);
            estimatedTimeLabel.setText("Estimated remaining: " + estimated);
        });
    }

    private String formatTimeDirectly(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void complete(final String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            progressBar.setValue(totalSteps);
            progressBar.setString("Complete");

            // Update final elapsed time
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            elapsedTimeLabel.setText("Elapsed: " + formatTimeDirectly(elapsedTimeMs));
            estimatedTimeLabel.setText("Estimated remaining: 00:00:00");

            updateTimer.stop();
        });
        IJ.showStatus(message);
        IJ.showProgress(1.0);
    }

    public void close() {
        if (updateTimer != null && updateTimer.isRunning()) {
            updateTimer.stop();
        }
        SwingUtilities.invokeLater(() -> frame.dispose());
    }
}