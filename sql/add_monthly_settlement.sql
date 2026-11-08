-- MySQL 8：在多客户和鞋款报价迁移之后执行。已有出货、报价和 Excel 对账单不会被回填。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS monthly_settlement (
    id bigint NOT NULL AUTO_INCREMENT,
    customer_id bigint NOT NULL,
    bill_month char(7) NOT NULL COMMENT '账单所属月 YYYY-MM',
    revision int NOT NULL,
    draft_json json NOT NULL COMMENT '制单日期、结转选择、质保金与补扣明细',
    snapshot_json json DEFAULT NULL COMMENT '确认月结时固化的出货、待请款、补结与金额',
    confirmed_at datetime DEFAULT NULL,
    received_on date DEFAULT NULL COMMENT '实际到账日',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_customer_month (customer_id,bill_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度结算单及已确认快照';
