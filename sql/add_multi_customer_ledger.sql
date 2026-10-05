-- MySQL 8：在现有业务数据库中执行；支持重复执行，不删除订单/出货/价格。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS customer_config (
    id bigint NOT NULL AUTO_INCREMENT,
    code varchar(30) NOT NULL,
    name varchar(50) NOT NULL,
    recipient_name varchar(128) NOT NULL,
    input_mode varchar(16) NOT NULL,
    enabled tinyint(1) NOT NULL DEFAULT 1,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_customer_code (code), UNIQUE KEY uk_customer_recipient (recipient_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT IGNORE INTO customer_config (code,name,recipient_name,input_mode)
VALUES ('DAWEI','达为','达为鞋业','EXCEL'),('BAOXIANG','宝翔','宝翔','MANUAL'),('TIANTUZI','天兔子','天兔子','MANUAL');

CREATE TABLE IF NOT EXISTS customer_price_config (
    id bigint NOT NULL AUTO_INCREMENT,
    customer_id bigint NOT NULL,
    development_no varchar(128) NOT NULL,
    shoe_price decimal(10,2) DEFAULT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_customer_price (customer_id,development_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 仅添加缺失列。DDL 会隐式提交，执行前先做数据库备份。
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_multi_customer_column$$
CREATE PROCEDURE migrate_multi_customer_column(IN table_value varchar(64), IN column_value varchar(64), IN definition_value varchar(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name=table_value AND column_name=column_value) THEN
        SET @multi_customer_ddl = CONCAT('ALTER TABLE `',table_value,'` ADD COLUMN `',column_value,'` ',definition_value);
        PREPARE multi_customer_stmt FROM @multi_customer_ddl;
        EXECUTE multi_customer_stmt;
        DEALLOCATE PREPARE multi_customer_stmt;
    END IF;
END$$
DELIMITER ;
CALL migrate_multi_customer_column('order_record','customer_id','bigint DEFAULT NULL');
CALL migrate_multi_customer_column('order_record','order_date','date DEFAULT NULL');
CALL migrate_multi_customer_column('shipping_note_task','customer_id','bigint DEFAULT NULL');
CALL migrate_multi_customer_column('shipping_note_task','source_type','tinyint NOT NULL DEFAULT 1');
CALL migrate_multi_customer_column('shipping_note_task','request_id','varchar(64) DEFAULT NULL');
DROP PROCEDURE migrate_multi_customer_column;

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_multi_customer_index$$
CREATE PROCEDURE migrate_multi_customer_index(IN table_value varchar(64), IN index_value varchar(64), IN definition_value varchar(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name=table_value AND index_name=index_value) THEN
        SET @multi_customer_ddl = CONCAT('ALTER TABLE `',table_value,'` ADD ',definition_value);
        PREPARE multi_customer_stmt FROM @multi_customer_ddl;
        EXECUTE multi_customer_stmt;
        DEALLOCATE PREPARE multi_customer_stmt;
    END IF;
END$$
DELIMITER ;
CALL migrate_multi_customer_index('order_record','idx_order_customer','INDEX idx_order_customer (customer_id, order_no)');
CALL migrate_multi_customer_index('shipping_note_task','idx_shipment_customer','INDEX idx_shipment_customer (customer_id, shipping_date)');
CALL migrate_multi_customer_index('shipping_note_task','uk_shipment_request','UNIQUE INDEX uk_shipment_request (request_id)');
DROP PROCEDURE migrate_multi_customer_index;

-- 历史 Excel 全部归属达为。保留原 customer_name（Excel 内的终端客人）。
UPDATE order_record
SET customer_id=(SELECT id FROM customer_config WHERE code='DAWEI')
WHERE customer_id IS NULL AND source_type=1;
UPDATE order_record SET order_date=DATE(created_at) WHERE order_date IS NULL;
UPDATE shipping_note_task t
JOIN shipping_note_task_item i ON i.task_id=t.id
JOIN order_record o ON o.id=i.order_id
SET t.customer_id=o.customer_id WHERE t.customer_id IS NULL AND o.customer_id IS NOT NULL;
-- 没有明细的历史打印任务也归属达为。
UPDATE shipping_note_task SET customer_id=(SELECT id FROM customer_config WHERE code='DAWEI')
WHERE customer_id IS NULL AND source_type=1;

-- 复制达为旧款价，重复执行不覆盖后来维护的客户价格。
INSERT IGNORE INTO customer_price_config (customer_id,development_no,shoe_price,created_at,updated_at)
SELECT c.id,UPPER(TRIM(s.development_no)),s.shoe_price,s.created_at,s.updated_at
FROM shoe_style_config s CROSS JOIN customer_config c WHERE c.code='DAWEI';

-- 出货按来源订单明细的全部数量计算；历史和新出货均读取客户最新款价。
