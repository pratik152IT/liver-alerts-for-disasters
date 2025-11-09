package com.yourorg.livealerts.notification;

import com.yourorg.livealerts.model.DisasterEvent;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.awt.*;
import java.net.PasswordAuthentication;
import java.util.Properties;

import org.apache.hc.core5.http.Message;

public class NotificationService {
    private final String senderEmail;
    private final String senderPassword;
    private final String recipientEmail;
    private final TrayIcon trayIcon;
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;

    public NotificationService(String senderEmail, String senderPassword, String recipientEmail) {
        this.senderEmail = "reshavshrma@gmail.com";
        this.senderPassword = "qwwppxqmoakqagrh";
        this.recipientEmail = "2022bit061@sggs.ac.in";

        // Initialize system tray icon
        if (SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/static/alert-icon.png"));
                this.trayIcon = new TrayIcon(image, "Live Alerts");
                this.trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("Could not initialize system tray: " + e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            this.trayIcon = null;
            System.err.println("System tray is not supported");
        }
    }

    public void notifyNewAlert(DisasterEvent event) {
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
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("⚠️ New Disaster Alert: " + event.getTitle());

            String emailContent = String.format("""
                New disaster event detected:
                
                Title: %s
                Category: %s
                Location: %.3f°%s, %.3f°%s
                Source: %s
                Date: %s
                %s
                
                View more details at: %s
                """,
                event.getTitle(),
                event.getCategory(),
                Math.abs(event.getLat()), event.getLat() >= 0 ? "N" : "S",
                Math.abs(event.getLon()), event.getLon() >= 0 ? "E" : "W",
                event.getSource(),
                event.getDate(),
                event.getMagnitude() != null ? String.format("Magnitude: %.1f", event.getMagnitude()) : "",
                event.getUrl()
            );

            message.setText(emailContent);
            Transport.send(message);
        } catch (MessagingException e) {
            System.err.println("Failed to send email notification: " + e.getMessage());
        }
    }

    private void showDesktopNotification(DisasterEvent event) {
        if (trayIcon != null) {
            String caption = "New Disaster Alert";
            String text = String.format("%s\nCategory: %s\nLocation: %.3f°%s, %.3f°%s",
                event.getTitle(),
                event.getCategory(),
                Math.abs(event.getLat()), event.getLat() >= 0 ? "N" : "S",
                Math.abs(event.getLon()), event.getLon() >= 0 ? "E" : "W"
            );
            
            trayIcon.displayMessage(caption, text, TrayIcon.MessageType.WARNING);
        }
    }
}