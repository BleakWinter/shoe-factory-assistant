package com.shoefactory.assistant.service.report;

import com.shoefactory.assistant.service.report.DeliveryReportSendLog.Status;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@ConditionalOnProperty(prefix = "app.delivery-report", name = "enabled", havingValue = "true")
public class DeliveryReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(DeliveryReportService.class);

    private final DeliveryReportData data;
    private final DeliveryReportRenderer renderer;
    private final DeliveryReportMail mail;
    private final DeliveryReportSendLog sendLog;

    DeliveryReportService(DeliveryReportData data, DeliveryReportRenderer renderer,
                          DeliveryReportMail mail, DeliveryReportSendLog sendLog) {
        this.data = data;
        this.renderer = renderer;
        this.mail = mail;
        this.sendLog = sendLog;
    }

    @Scheduled(cron = "${app.delivery-report.cron}", zone = "Asia/Shanghai")
    public void sendScheduledReport() {
        sendToday(false);
    }

    public SendResult sendToday(boolean test) {
        LocalDate date = LocalDate.now(ZONE);
        mail.validateConfiguration();
        if (!test) {
            Status status = sendLog.claim(date);
            if (status != Status.CLAIMED) {
                return new SendResult(date, status == Status.SENT ? "ALREADY_SENT" : status.name(), false);
            }
        }

        MimeMessage message;
        try {
            var snapshot = data.load(date);
            var share = snapshot.forSharing();
            message = mail.prepare(snapshot, share, renderer.render(share), test);
        } catch (RuntimeException | MessagingException ex) {
            recordFailure(date, test, Status.FAILED, ex);
            throw new IllegalStateException("货期日报生成失败，可重试", ex);
        }

        try {
            mail.send(message);
        } catch (MailAuthenticationException ex) {
            recordFailure(date, test, Status.FAILED, ex);
            throw new IllegalStateException("QQ 邮箱认证失败，请检查发件账号和授权码", ex);
        } catch (RuntimeException ex) {
            // A timeout after SMTP accepted the message cannot be safely retried.
            recordFailure(date, test, Status.UNKNOWN, ex);
            throw new IllegalStateException("邮件发送结果未知，请核对 QQ 收件箱", ex);
        }
        if (!test) {
            sendLog.finish(date, Status.SENT);
        }
        log.info("货期日报邮件已交给 SMTP，日期 {}，试发 {}", date, test);
        return new SendResult(date, "SENT", test);
    }

    private void recordFailure(LocalDate date, boolean test, Status status, Exception original) {
        if (!test) {
            try {
                sendLog.finish(date, status);
            } catch (RuntimeException updateFailure) {
                original.addSuppressed(updateFailure);
            }
        }
    }

    public record SendResult(LocalDate reportDate, String status, boolean test) {}
}
