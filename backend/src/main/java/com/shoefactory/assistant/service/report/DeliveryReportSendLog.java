package com.shoefactory.assistant.service.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(prefix = "app.delivery-report", name = "enabled", havingValue = "true")
class DeliveryReportSendLog {
    private final JdbcTemplate jdbc;

    DeliveryReportSendLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Status claim(LocalDate date) {
        try {
            jdbc.update("INSERT INTO delivery_report_send (report_date, status) VALUES (?, 'SENDING')", date);
            return Status.CLAIMED;
        } catch (DuplicateKeyException existing) {
            if (jdbc.update("UPDATE delivery_report_send SET status = 'SENDING' WHERE report_date = ? AND status = 'FAILED'", date) == 1) {
                return Status.CLAIMED;
            }
            return Status.valueOf(jdbc.queryForObject(
                    "SELECT status FROM delivery_report_send WHERE report_date = ?", String.class, date));
        }
    }

    void finish(LocalDate date, Status status) {
        jdbc.update("""
                UPDATE delivery_report_send
                SET status = ?, sent_at = CASE WHEN ? = 'SENT' THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE report_date = ? AND status = 'SENDING'
                """, status.name(), status.name(), date);
    }

    enum Status { CLAIMED, SENDING, SENT, FAILED, UNKNOWN }
}
