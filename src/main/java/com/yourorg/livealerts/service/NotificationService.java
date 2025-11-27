package com.yourorg.livealerts.service;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yourorg.livealerts.model.DisasterEvent;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * NotificationService with hard-coded sender + multiple recipients (sent via BCC).
 * Sends both plain-text and HTML versions of the email (multipart/alternative).
 *
 * NOTE: Hard-coding credentials is insecure — prefer env vars or secrets manager for production.
 */
public class NotificationService {
    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());

    // Hard-coded credentials / recipients (provided by user)
    private final String senderEmail = "idma.notifications.iitnanded@gmail.com";
    private final String senderPassword = "tdntyfvdtimgbvza";
    private final List<String> recipientEmails = new ArrayList<>(List.of(
        "2022bit062@sggs.ac.in",
        "2022bit061@sggs.ac.in",
        "2022bit063@sggs.ac.in",
        "2022bit153@sggs.ac.in",
        "2022bcs120@sggs.ac.in"
    ));

    // SMTP host/port (defaults suitable for Gmail)
    private final String smtpHost = "smtp.gmail.com";
    private final String smtpPort = "587";

    private TrayIcon trayIcon;

    /**
     * No-arg constructor (uses hard-coded values above).
     */
    public NotificationService() {
        initTrayIcon();
    }

    /**
     * Alternate constructor (keeps compatibility if you want to create with custom values).
     */
    public NotificationService(String smtpHost, String smtpPort) {
        // Note: sender/recipients are still the hard-coded ones above.
        // This constructor only allows overriding host/port (not reassigning finals here).
        initTrayIcon();
    }

    private void initTrayIcon() {
        if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                java.net.URL imageUrl = getClass().getResource("/static/alert-icon.png");
                Image image;
                if (imageUrl != null) {
                    image = Toolkit.getDefaultToolkit().createImage(imageUrl);
                } else {
                    BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                    image = bi;
                    LOGGER.log(Level.FINE, "Tray icon resource not found; using fallback transparent image.");
                }
                trayIcon = new TrayIcon(image, "Live Alerts");
                trayIcon.setImageAutoSize(true);
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    LOGGER.log(Level.WARNING, "Failed to add tray icon: {0}", e.toString());
                    trayIcon = null;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unexpected error initializing tray icon: {0}", e.toString());
                trayIcon = null;
            }
        } else {
            trayIcon = null;
            LOGGER.log(Level.FINE, "System tray not supported or running in headless mode.");
        }
    }

    /**
     * Notify about a new disaster event: attempt email (if configured) and desktop notification.
     */
    public void notifyNewAlert(DisasterEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        if (canSendEmail()) {
            sendEmail(event);
        } else {
            LOGGER.log(Level.FINE, "Email not sent: missing SMTP configuration or recipients.");
        }

        showDesktopNotification(event);
    }

    private boolean canSendEmail() {
        return senderEmail != null && !senderEmail.isBlank()
            && senderPassword != null && !senderPassword.isBlank()
            && recipientEmails != null && !recipientEmails.isEmpty();
    }

    private void sendEmail(DisasterEvent event) {
        if (!canSendEmail()) {
            LOGGER.log(Level.FINE, "Email not sent: configuration incomplete.");
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, senderPassword);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));

            // Make the sender visible in TO, actual recipients go into BCC (hidden from each other)
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(senderEmail));
            String allRecipients = String.join(",", recipientEmails);
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(allRecipients, true));

            String subject = "Official Disaster Alert";
            if (event.getTitle() != null && !event.getTitle().isBlank()) {
                subject = subject + " — " + event.getTitle();
            }
            message.setSubject(subject);

            // Plain-text part (fallback for email clients that do not render HTML)
            String magnitudeLine = "";
            if (event.getMagnitude() != null) {
                magnitudeLine = String.format("Magnitude: %.1f%n", event.getMagnitude());
            }

            String plainText = String.format(
                "OFFICIAL DISASTER NOTIFICATION%n%n" +
                "Title       : %s%n" +
                "Category    : %s%n" +
                "Location    : %.3f°%s, %.3f°%s%n" +
                "Source      : %s%n" +
                "Date        : %s%n" +
                "Details     : %s%n%n" +
                "For further details, please visit: %s%n%n" +
                "------------------------------------------------------------%n" +
                "This is an automated disaster alert sent by the Institutional Disaster Management Authority (NDMA), IT Department, IIT Nanded (Maharashtra).%n" +
                "%nThis is a system-generated email. Please do not reply to this message.%n" +
                "------------------------------------------------------------",
                safe(event.getTitle()),
                safe(event.getCategory()),
                Math.abs(event.getLat()), event.getLat() >= 0 ? "N" : "S",
                Math.abs(event.getLon()), event.getLon() >= 0 ? "E" : "W",
                safe(event.getSource()),
                safe(event.getDate() != null ? event.getDate().toString() : ""),
                magnitudeLine,
                safe(event.getUrl())
            );

        // HTML part (nicer formatting) — note the escaped percent sign "%%" for "100%"
        String emailHtml = String.format("""
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Official Disaster Alert</title>
            </head>
            <body style="font-family: Arial, Helvetica, sans-serif; color: #222; line-height:1.4;">
                <div style="max-width:700px; margin:0 auto; border:1px solid #e0e0e0; padding:16px;">
                <div style="background:#003366; color:#fff; padding:12px 16px; border-radius:6px 6px 0 0; text-align:center;">
                    <h2 style="margin:0; font-size:18px;"> DISASTER NOTIFICATION</h2>
                    <div style="font-size:12px; opacity:0.9; text-align:center;">Institutional Disaster Management Authority (NDMA) — IT Department, IIT Nanded</div>
                </div>

                <table style="width:100%%; border-collapse:collapse; margin-top:12px;">
                    <tr>
                    <td style="padding:8px; font-weight:600; width:140px;">Title</td>
                    <td style="padding:8px;">%s</td>
                    </tr>
                    <tr style="background:#f9f9f9;">
                    <td style="padding:8px; font-weight:600;">Category</td>
                    <td style="padding:8px;">%s</td>
                    </tr>
                    <tr>
                    <td style="padding:8px; font-weight:600;">Location</td>
                    <td style="padding:8px;">%.3f°%s, %.3f°%s</td>
                    </tr>
                    <tr style="background:#f9f9f9;">
                    <td style="padding:8px; font-weight:600;">Source</td>
                    <td style="padding:8px;">%s</td>
                    </tr>
                    <tr>
                    <td style="padding:8px; font-weight:600;">Date</td>
                    <td style="padding:8px;">%s</td>
                    </tr>
                    <tr style="background:#f9f9f9;">
                    <td style="padding:8px; font-weight:600;">Details</td>
                    <td style="padding:8px;">%s</td>
                    </tr>
                </table>

                <p style="margin-top:14px;">For further details, please visit: <a href="%s">%s</a></p>

                <hr style="border:none; border-top:1px solid #e0e0e0; margin:18px 0;">

                <p style="font-size:12px; color:#555; margin:0;">
                    This is an automated disaster alert sent by the Institutional Disaster Management Authority (NDMA), IT Department, IIT Nanded (Maharashtra). <br>
                    This is a system-generated email. Please do not reply to this message.<br>
                </p>
                </div>
            </body>
            </html>
            """,
            escapeHtml(safe(event.getTitle())),
            escapeHtml(safe(event.getCategory())),
            Math.abs(event.getLat()), event.getLat() >= 0 ? "N" : "S",
            Math.abs(event.getLon()), event.getLon() >= 0 ? "E" : "W",
            escapeHtml(safe(event.getSource())),
            escapeHtml(safe(event.getDate() != null ? event.getDate().toString() : "")),
            escapeHtml(magnitudeLine.replaceAll("\\n", "<br/>")),
            safe(event.getUrl()),
            safe(event.getUrl())
        );


            // Build multipart/alternative
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(plainText, "utf-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(emailHtml, "text/html; charset=utf-8");

            Multipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);

            Transport.send(message);
            LOGGER.log(Level.FINE, "HTML email notification sent (BCC) to {0} recipients.", recipientEmails.size());
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

    /**
     * Programmatically add a new recipient to the in-memory list.
     * (Does not persist across application restarts.)
     */
    public void addRecipient(String email) {
        if (email != null && !email.isBlank()) {
            recipientEmails.add(email.trim());
        }
    }

    /**
     * Programmatically remove a recipient.
     */
    public boolean removeRecipient(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return recipientEmails.remove(email.trim());
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    /**
     * Simple HTML-escaping for values inserted into the HTML template.
     * Keeps things safe for basic text values.
     */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
