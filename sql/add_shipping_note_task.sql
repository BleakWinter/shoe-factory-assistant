DROP TABLE IF EXISTS `shipping_note_task_item`;
DROP TABLE IF EXISTS `shipping_note_task`;
DROP TABLE IF EXISTS `shipping_note_record`;

CREATE TABLE IF NOT EXISTS `shipping_note_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no` varchar(64) NOT NULL COMMENT '出货单任务编号',
  `recipient_name` varchar(128) DEFAULT NULL COMMENT '收货单位',
  `shipping_date` date DEFAULT NULL COMMENT '出货日期',
  `invoice_nos` varchar(1024) DEFAULT NULL COMMENT '发票编号汇总',
  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发编号汇总',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '出货单明细行数',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '合计双数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '合计件数/箱数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_note_task_no` (`task_no`),
  KEY `idx_shipping_note_task_invoice_nos` (`invoice_nos`(191)),
  KEY `idx_shipping_note_task_development_nos` (`development_nos`(191)),
  KEY `idx_shipping_note_task_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出货单打印任务表';

CREATE TABLE IF NOT EXISTS `shipping_note_task_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '出货单任务ID',
  `line_no` int NOT NULL DEFAULT 0 COMMENT '任务内行号',
  `order_id` bigint DEFAULT NULL COMMENT '订单主表ID',
  `source_detail_id` bigint NOT NULL COMMENT '来源装箱单明细ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_shipping_note_task_item_task_id` (`task_id`),
  KEY `idx_shipping_note_task_item_order_id` (`order_id`),
  KEY `idx_shipping_note_task_item_source_detail_id` (`source_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出货单打印任务明细表';
