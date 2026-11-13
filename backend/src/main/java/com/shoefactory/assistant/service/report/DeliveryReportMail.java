package com.shoefactory.assistant.service.report;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.delivery-report", name = "enabled", havingValue = "true")
class DeliveryReportMail {
    private final JavaMailSender sender;
    private final MailProperties mail;
    private final String recipient;

    DeliveryReportMail(JavaMailSender sender, MailProperties mail,
                       @Value("${app.delivery-report.recipient:}") String recipient) {
        this.sender = sender;
        this.mail = mail;
        this.recipient = recipient;
    }

    void validateConfiguration() {
        if (!StringUtils.hasText(mail.getUsername()) || !StringUtils.hasText(mail.getPassword())) {
            throw new IllegalStateException("货期日报邮件需要配置发件账号和 SMTP 授权码");
        }
        try {
            new InternetAddress(mail.getUsername(), true).validate();
            new InternetAddress(toAddress(), true).validate();
        } catch (MessagingException ex) {
            throw new IllegalStateException("货期日报邮件地址无效", ex);
        }
    }

    MimeMessage prepare(DeliveryReportData.Snapshot snapshot, DeliveryReport share, List<byte[]> pages, boolean test)
            throws MessagingException {
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(mail.getUsername());
        helper.setTo(toAddress());
        helper.setSubject((test ? "【试发】" : "") + "清化鞋业｜货期预警｜" + snapshot.date());
        helper.setText(body(snapshot, share));
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.size() == 1 ? "" : "-" + (i + 1);
            helper.addAttachment("货期预警-" + snapshot.date() + page + ".png",
                    new ByteArrayResource(pages.get(i)), "image/png");
        }
        return message;
    }

    void send(MimeMessage message) {
        sender.send(message);
    }

    private String toAddress() {
        return StringUtils.hasText(recipient) ? recipient : mail.getUsername();
    }

    private static String body(DeliveryReportData.Snapshot snapshot, DeliveryReport share) {
        LocalDate date = snapshot.date();
        StringBuilder body = new StringBuilder("清化鞋业 · 货期预警\n").append(date)
                .append("\n待出货双数按系统出货记录统计。\n");
        long overdue = 0, today = 0, nextWeek = 0, unknown = 0;
        for (var alert : snapshot.groups()) {
            var order = alert.order();
            long pairs = order.styles().stream().mapToLong(DeliveryReport.Style::pendingPairs).sum();
            if (order.deliveryDate() == null) {
                unknown += pairs;
            } else if (order.overdueDays() > 0) {
                overdue += pairs;
            } else if (order.deliveryDate().equals(date)) {
                today += pairs;
            } else {
                nextWeek += pairs;
            }
        }
        body.append("逾期 ").append(overdue).append(" 双｜今日到期 ").append(today)
                .append(" 双｜未来 7 天到期 ").append(nextWeek).append(" 双｜交期待确认 ")
                .append(unknown).append(" 双\n\n");
        if (snapshot.groups().isEmpty()) {
            body.append("暂无预警订单。\n");
        }
        for (var group : snapshot.groups()) {
            appendOrder(body, group.order(), date, group.packingMissing());
        }
        body.append("\n微信群转发文案\n清化鞋业 · ").append(date).append(" 货期预警\n");
        if (share.orderGroups().isEmpty()) {
            body.append("暂无逾期或未来 3 天到期订单。\n");
        }
        for (var group : share.orderGroups()) {
            appendOrder(body, group, date, false);
        }
        return body.toString();
    }

    private static void appendOrder(StringBuilder body, DeliveryReport.OrderGroup group, LocalDate date, boolean packingMissing) {
        body.append(group.orderNo()).append("｜");
        if (group.deliveryDate() == null) {
            body.append("交期待确认");
        } else {
            body.append(group.deliveryDate()).append("｜");
            if (group.overdueDays() > 0) {
                body.append("逾期 ").append(group.overdueDays()).append(" 天");
            } else if (group.deliveryDate().equals(date)) {
                body.append("今日到期");
            } else {
                body.append("还有 ").append(ChronoUnit.DAYS.between(date, group.deliveryDate())).append(" 天");
            }
        }
        if (packingMissing) {
            body.append("｜装箱单待补");
        }
        body.append('\n');
        for (var style : group.styles()) {
            body.append("  ").append(style.styleNo()).append("｜待出货 ").append(style.pendingPairs()).append(" 双\n");
        }
        body.append('\n');
    }
}
