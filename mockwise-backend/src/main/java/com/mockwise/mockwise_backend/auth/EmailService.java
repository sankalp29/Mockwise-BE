package com.mockwise.mockwise_backend.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final String VERIFICATION_EMAIL_SUBJECT = "Account Verification for Mockwise";

    private static final String VERIFICATION_EMAIL_HTML_TEMPLATE =
            "<!DOCTYPE html>"
                    + "<html lang=\"en\">"
                    + "<head>"
                    + "    <meta charset=\"UTF-8\">"
                    + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                    + "    <title>Account Verification</title>"
                    + "    <style>"
                    + "        body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; font-size: 16px; color: #333333; }"
                    + "        .container { max-width: 600px; margin: 20px auto; background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 0 10px rgba(0, 0, 0, 0.1); }"
                    + "        .header { text-align: center; padding-bottom: 20px; border-bottom: 1px solid #eeeeee; }"
                    + "        h2 { font-size: 24px; color: #2d3748; }"
                    + "        .content { padding: 20px 0; line-height: 1.6; }"
                    + "        .content p { font-size: 16px; margin-bottom: 1em; }"
                    + "        .button-container { text-align: center; margin-top: 30px; }"
                    + "        .button { display: inline-block; padding: 12px 25px; margin: 0 auto; background-color: #28a745; color: #ffffff !important; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px; }"
                    + "        .button:hover { background-color: #218838; }"
                    + "        .footer { text-align: center; padding-top: 20px; margin-top: 20px; border-top: 1px solid #eeeeee; font-size: 14px; color: #777777; }"
                    + "    </style>"
                    + "</head>"
                    + "<body>"
                    + "    <div class=\"container\">"
                    + "        <div class=\"header\">"
                    + "            <h2>Welcome to Mockwise!</h2>"
                    + "        </div>"
                    + "        <div class=\"content\">"
                    + "            <p>Dear %s,</p>"
                    + "            <p>Thank you for registering with Mockwise. Please click the button below to verify your account:</p>"
                    + "            <div class=\"button-container\">"
                    + "                <a href=\"%s\" class=\"button\">Verify Email</a>"
                    + "            </div>"
                    + "            <p style=\"margin-top: 30px;\">This link will expire in 3 hours.</p>"
                    + "            <p>If you did not register for this account, please ignore this email.</p>"
                    + "        </div>"
                    + "        <div class=\"footer\">"
                    + "            <p>Regards,<br>Mockwise Team</p>"
                    + "        </div>"
                    + "    </div>"
                    + "</body>"
                    + "</html>";

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String text, boolean isHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, isHtml);
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendVerificationEmail(String to, String userName, String verificationLink) {
        String personalizedHtml = String.format(VERIFICATION_EMAIL_HTML_TEMPLATE, userName, verificationLink);
        sendEmail(to, VERIFICATION_EMAIL_SUBJECT, personalizedHtml, true);
    }
}


