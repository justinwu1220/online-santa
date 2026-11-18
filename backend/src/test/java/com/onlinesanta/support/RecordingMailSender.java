package com.onlinesanta.support;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * 測試用的記錄式 {@link JavaMailSender}，取代真的 SMTP 連線。
 *
 * <p>純文字信（{@link SimpleMailMessage}）與樣板信（MIME，text/plain + text/html 的
 * multipart/alternative）都收。兩種都統一成 {@link SimpleMailMessage}（收件人、主旨、純文字內文）
 * 從 {@link #sent()} 取得，所以只關心「寄給誰、說了什麼」的斷言不必在意格式；要檢查 HTML 版或
 * 原始 MIME 結構的測試用 {@link #sentMails()}。
 */
public class RecordingMailSender implements JavaMailSender {

    /** 一封寄出的信。{@code html} 純文字信時為 null；{@code mime} 純文字信時為 null。 */
    public record SentMail(SimpleMailMessage message, String html, MimeMessage mime) {
    }

    private static final Session SESSION = Session.getInstance(new Properties());

    private final List<SentMail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        sent.add(new SentMail(simpleMessage, null, null));
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage message : simpleMessages) {
            send(message);
        }
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(SESSION);
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        try {
            return new MimeMessage(SESSION, contentStream);
        } catch (MessagingException e) {
            throw new MailParseException(e);
        }
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        try {
            mimeMessage.saveChanges();
            SimpleMailMessage simple = new SimpleMailMessage();
            if (mimeMessage.getFrom() != null && mimeMessage.getFrom().length > 0) {
                simple.setFrom(mimeMessage.getFrom()[0].toString());
            }
            var to = mimeMessage.getRecipients(Message.RecipientType.TO);
            if (to != null) {
                simple.setTo(java.util.Arrays.stream(to).map(Object::toString).toArray(String[]::new));
            }
            simple.setSubject(mimeMessage.getSubject());
            String[] parts = new String[2];     // [0]=text/plain, [1]=text/html
            collect(mimeMessage, parts);
            simple.setText(parts[0]);
            sent.add(new SentMail(simple, parts[1], mimeMessage));
        } catch (MessagingException | java.io.IOException e) {
            throw new MailParseException(e);
        }
    }

    private static void collect(Part part, String[] out) throws MessagingException, java.io.IOException {
        if (part.isMimeType("text/plain") && out[0] == null) {
            out[0] = (String) part.getContent();
        } else if (part.isMimeType("text/html") && out[1] == null) {
            out[1] = (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, out);
            }
        }
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage message : mimeMessages) {
            send(message);
        }
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        MimeMessage message = createMimeMessage();
        try {
            mimeMessagePreparator.prepare(message);
        } catch (Exception e) {
            throw new MailParseException(e instanceof MessagingException m ? m : new MessagingException(e.toString()));
        }
        send(message);
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        for (MimeMessagePreparator preparator : mimeMessagePreparators) {
            send(preparator);
        }
    }

    /** 每封信的收件人、主旨與純文字內文（純文字信與樣板信一視同仁）。 */
    public List<SimpleMailMessage> sent() {
        List<SimpleMailMessage> out = new ArrayList<>();
        for (SentMail mail : sent) {
            out.add(mail.message());
        }
        return out;
    }

    /** 完整記錄，含 HTML 版與原始 MIME 訊息。 */
    public List<SentMail> sentMails() {
        return new ArrayList<>(sent);
    }

    public void reset() {
        sent.clear();
    }
}
