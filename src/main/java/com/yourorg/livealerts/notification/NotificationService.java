package com.yourorg.livealerts.notification;

import com.yourorg.livealerts.model.DisasterEvent;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NotificationService - sends an email and (when available) shows a desktop tray notification.
 *
 * Notes:
 * - This class uses jakarta.mail APIs. Ensure the project has the appropriate Jakarta Mail dependency.
 * - Desktop notifications require AWT; in headless environments (servers, CI) the tray code will be skipped.
 */
public class NotificationService {
    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());

    private final String senderEmail;
    private final String senderPassword;
    private final String recipientEmail;
    private TrayIcon trayIcon;

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";

    public NotificationService(String senderEmail, String senderPassword, String recipientEmail) {
        this.senderEmail = Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        this.senderPassword = Objects.requireNonNull(senderPassword, "senderPassword must not be null");
        this.recipientEmail = Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");

        // Initialize system tray icon only if AWT is available (not headless) and system tray is supported.
        if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                java.net.URL imageUrl = getClass().getResource("/static/alert-icon.png");
                Image image;
                if (imageUrl != null) {
                    image = Toolkit.getDefaultToolkit().createImage(imageUrl);
                } else {
                    // Create a tiny transparent BufferedImage as a safe fallback.
                    BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                    image = bi;
                    LOGGER.log(Level.FINE, "Tray icon resource not found; using fallback image.");
                }
                trayIcon = new TrayIcon(image, "Live Alerts");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            } catch (AWTException e) {
                LOGGER.log(Level.WARNING, "Could not initialize system tray: {0}", e.getMessage());
                trayIcon = null;
            } catch (Exception e) {
                // Defensive catch for any other AWT/toolkit issues
                LOGGER.log(Level.WARNING, "Unexpected error initializing tray icon: {0}", e.toString());
                trayIcon = null;
            }
        } else {
            trayIcon = null;
            LOGGER.log(Level.FINE, "System tray not supported or running in headless mode.");
        }
    }

    /**
     * Public method to send notifications for a new disaster event.
     *
     * @param event the disaster event; must not be null
     */
    public void notifyNewAlert(DisasterEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        sendEmail(event);
        showDesktopNotification(event);
    }

    private void sendEmail(DisasterEvent event) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, senderPassword);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            String subject = "New Disaster Alert";
            if (event.getTitle() != null && !event.getTitle().isBlank()) {
                subject = subject + ": " + event.getTitle();
            }
            message.setSubject(subject);

            String magnitudeLine = "";
            if (event.getMagnitude() != null) {
                magnitudeLine = String.format("Magnitude: %.1f\n", event.getMagnitude());
            }

            String emailContent = String.format(
                "New disaster event detected:%n%n" +
                "Title: %s%n" +
                "Category: %s%n" +
                "Location: %.3f°%s, %.3f°%s%n" +
                "Source: %s%n" +
                "Date: %s%n" +
                "%s%n" +
                "View more details at: %s",
                safe(event.getTitle()),
                safe(event.getCategory()),
                Math.abs(event.getLat()), event.getLat() >= 0 ? "N" : "S",
                Math.abs(event.getLon()), event.getLon() >= 0 ? "E" : "W",
                safe(event.getSource()),
                safe(event.getDate() != null ? event.getDate().toString() : ""),
                magnitudeLine,
                safe(event.getUrl())
            );

            message.setText(emailContent);
            Transport.send(message);
            LOGGER.log(Level.FINE, "Email notification sent to {0}", recipientEmail);
        } catch (MessagingException e) {
            LOGGER.log(Level.SEVERE, "Failed to send email notification: {0}", e.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error when sending email: {0}", e.toString());
        }
    }

    private void showDesktopNotification(DisasterEvent event) {
        if (trayIcon != null) {
            String caption = "New Disaster Alert";
            String text = String.format("%s%nCategory: %s%nLocation: %.3f°%s, %.3f°%s",
                safe(event.getTitle()),
                safe(event.getCategory()),
                Math.abs(event.getLat()), event.getLat() >= 0 ? "N" : "S",
                Math.abs(event.getLon()), event.getLon() >= 0 ? "E" : "W"
            );
            try {
                trayIcon.displayMessage(caption, text, TrayIcon.MessageType.WARNING);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to show desktop notification: {0}", e.toString());
            }
        }
    }

    // Small helper to avoid nulls in formatted strings
    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }
}