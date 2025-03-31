package com.sebastianmicu.scheli.segmentation;

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

public class Progress {
    private JFrame frame;
    private JProgressBar totalProgressBar;
    private JProgressBar stepProgressBar;
    private JLabel statusLabel;
    private JLabel elapsedTimeLabel;
    private JLabel estimatedTimeLabel;
    private int totalImages = 0;
    private int currentImage = 0;
    private int totalSteps = 8;
    private int currentStep = 0;
    private long startTime = 0;
    private Timer updateTimer;
    
    public Progress(String title) {
        createUI(title);
        startTime = System.currentTimeMillis();
        
        // Create a timer that updates the time display every second
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
        
        // Progress bars panel
        JPanel progressPanel = new JPanel(new GridLayout(3, 1, 0, 10));
        
        // Total progress (images)
        JPanel totalPanel = new JPanel(new BorderLayout(5, 5));
        totalPanel.setBorder(new TitledBorder("Total Progress"));
        totalProgressBar = new JProgressBar(0, 1);
        totalProgressBar.setStringPainted(true);
        totalProgressBar.setPreferredSize(new Dimension(300, 20));
        totalPanel.add(totalProgressBar, BorderLayout.CENTER);
        
        // Step progress (current image)
        JPanel stepPanel = new JPanel(new BorderLayout(5, 5));
        stepPanel.setBorder(new TitledBorder("Current Image Progress"));
        stepProgressBar = new JProgressBar(0, totalSteps + 1); // +1 to make it look more familiar
        stepProgressBar.setStringPainted(true);
        stepProgressBar.setPreferredSize(new Dimension(300, 20));
        stepPanel.add(stepProgressBar, BorderLayout.CENTER);
        
        // Time information panel
        JPanel timePanel = new JPanel(new GridLayout(2, 1, 0, 5));
        timePanel.setBorder(new TitledBorder("Time Information"));
        
        elapsedTimeLabel = new JLabel("Elapsed: 00:00:00");
        elapsedTimeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timePanel.add(elapsedTimeLabel);
        
        estimatedTimeLabel = new JLabel("Estimated remaining: --:--:--");
        estimatedTimeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timePanel.add(estimatedTimeLabel);
        
        progressPanel.add(totalPanel);
        progressPanel.add(stepPanel);
        progressPanel.add(timePanel);
        mainPanel.add(progressPanel, BorderLayout.CENTER);
        
        frame.add(mainPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }
    
    public void showProgress() {
        startTime = System.currentTimeMillis(); // Reset start time when showing progress
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        updateTimer.start(); // Start the timer to update time display every second
    }
    
    public void setTotalImages(int total) {
        totalImages = total;
        SwingUtilities.invokeLater(() -> {
            totalProgressBar.setMaximum(totalImages);
            totalProgressBar.setString("0 / " + totalImages);
        });
    }
    
    public void nextImage() {
        currentImage++;
        currentStep = 0;
        SwingUtilities.invokeLater(() -> {
            totalProgressBar.setValue(currentImage);
            totalProgressBar.setString(currentImage + " / " + totalImages);
            
            // Reset step progress for new image
            stepProgressBar.setValue(0);
            stepProgressBar.setString("0 / " + (totalSteps + 1));
        });
    }
    
    public void updateStatus(final String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            incrementStepProgress();
        });
        
        // Also update ImageJ status bar
        IJ.showStatus(status);
    }
    
    private void incrementStepProgress() {
        currentStep++;
        final int progress = Math.min(currentStep, totalSteps);
        
        SwingUtilities.invokeLater(() -> {
            stepProgressBar.setValue(progress);
            stepProgressBar.setString(progress + " / " + (totalSteps + 1)); // +1 to make it look more familiar
        });
        
        // Also update ImageJ progress bar (normalized between 0-1)
        double totalProgress = (currentImage - 1 + (double)progress/totalSteps) / totalImages;
        IJ.showProgress(totalProgress);
    }
    
    private void updateTimeInformation() {
        long currentTime = System.currentTimeMillis();
        long elapsedTimeMs = currentTime - startTime;
        
        // Format elapsed time directly without using Date (to avoid timezone issues)
        String elapsedTimeStr = formatTimeDirectly(elapsedTimeMs);
        
        // Calculate estimated time remaining
        String estimatedTimeStr = "--:--:--";
        if (totalImages > 0 && (currentImage > 0 || currentStep > 0)) {
            double totalProgressFraction = (currentImage - 1 + (double)currentStep/totalSteps) / totalImages;
            if (totalProgressFraction > 0) {
                long estimatedTotalTimeMs = (long)(elapsedTimeMs / totalProgressFraction);
                long estimatedRemainingTimeMs = estimatedTotalTimeMs - elapsedTimeMs;
                if (estimatedRemainingTimeMs > 0) {
                    estimatedTimeStr = formatTimeDirectly(estimatedRemainingTimeMs);
                } else {
                    estimatedTimeStr = "00:00:00";
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
        // Convert milliseconds to HH:mm:ss format directly without using Date
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    public void complete(final String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            totalProgressBar.setValue(totalImages);
            totalProgressBar.setString("Complete");
            stepProgressBar.setValue(totalSteps + 1); // +1 to make it look more familiar
            stepProgressBar.setString("Complete");
            
            // Update final elapsed time
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            elapsedTimeLabel.setText("Elapsed: " + formatTimeDirectly(elapsedTimeMs));
            estimatedTimeLabel.setText("Estimated remaining: 00:00:00");
            
            // Stop the timer
            updateTimer.stop();
        });
        
        IJ.showStatus(message);
        IJ.showProgress(1.0);
    }
    
    public void close() {
        // Stop the timer before disposing the frame
        if (updateTimer != null && updateTimer.isRunning()) {
            updateTimer.stop();
        }
        SwingUtilities.invokeLater(() -> frame.dispose());
    }
}
