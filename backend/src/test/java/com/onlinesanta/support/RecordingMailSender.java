package com.onlinesanta.support;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.internet.MimeMessage;

/**
 * 測試用的記錄式 {@link JavaMailSender}，取代真的 SMTP 連線。
 *
 * <p>{@code NotificationService} 一律用純文字的 {@link SimpleMailMessage}，
 * {@link JavaMailSender} 其餘的方法（MIME 訊息相關）沒有呼叫路徑，直接丟
 * {@link UnsupportedOperationException}——真的被呼叫到會馬上在測試裡爆出來，
 * 而不是靜默沒反應。
 */
public class RecordingMailSender implements JavaMailSender {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        sent.add(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage message : simpleMessages) {
            send(message);
        }
    }

    @Override
    public MimeMessage createMimeMessage() {
        throw new UnsupportedOperationException("測試不需要 MIME 訊息");
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        throw new UnsupportedOperationException("測試不需要 MIME 訊息");
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        throw new UnsupportedOperationException("測試不需要 MIME 訊息");
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        throw new UnsupportedOperationException("測試不需要 MIME 訊息");
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        throw new UnsupportedOperationException("測試不需要 MIME 訊息");
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        throw new UnsupportedOperationException("測試不需要 MIME 訊息");
    }

    public List<SimpleMailMessage> sent() {
        return new ArrayList<>(sent);
    }

    public void reset() {
        sent.clear();
    }
}
