-- MySQL 8：先执行 add_multi_customer_ledger.sql，再执行本脚本。
-- 每次保存写入独立报价版本；不修改旧款配置或现有客户价格。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS shoe_quotation (
    id bigint NOT NULL AUTO_INCREMENT,
    version_group varchar(36) NOT NULL,
    version_no int NOT NULL,
    customer_id bigint DEFAULT NULL,
    development_no varchar(128) NOT NULL DEFAULT '',
    last_no varchar(64) DEFAULT NULL,
    color_material varchar(255) DEFAULT NULL,
    quotation_date date DEFAULT NULL,
    image_key varchar(128) DEFAULT NULL,
    labor_cost decimal(12,4) DEFAULT NULL,
    include_outsole tinyint(1) NOT NULL DEFAULT 0,
    rounding_mode varchar(16) NOT NULL DEFAULT 'CEIL',
    manual_price tinyint(1) NOT NULL DEFAULT 0,
    final_price decimal(10,2) DEFAULT NULL,
    material_amount decimal(28,8) DEFAULT NULL,
    auxiliary_amount decimal(28,8) DEFAULT NULL,
    outsole_amount decimal(28,8) DEFAULT NULL,
    calculated_amount decimal(28,8) DEFAULT NULL,
    note varchar(2000) DEFAULT NULL,
    adopted_at datetime DEFAULT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quotation_version (version_group,version_no),
    KEY idx_quotation_customer_style (customer_id,development_no),
    KEY idx_quotation_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shoe_quotation_item (
    id bigint NOT NULL AUTO_INCREMENT,
    quotation_id bigint NOT NULL,
    category varchar(16) NOT NULL,
    sort_no int NOT NULL,
    part_name varchar(100) DEFAULT NULL,
    material_name varchar(255) DEFAULT NULL,
    quantity decimal(12,4) DEFAULT NULL,
    unit varchar(16) DEFAULT NULL,
    unit_price decimal(12,4) DEFAULT NULL,
    amount decimal(24,8) DEFAULT NULL,
    remark varchar(1000) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_quotation_items (quotation_id,sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
