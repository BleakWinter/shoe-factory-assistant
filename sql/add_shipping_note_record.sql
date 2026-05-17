CREATE TABLE IF NOT EXISTS `shipping_note_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `print_no` varchar(64) NOT NULL COMMENT '出货单打印编号',
  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `order_no` varchar(128) DEFAULT NULL COMMENT '订单流水号',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客人',
  `recipient_name` varchar(128) DEFAULT NULL COMMENT '收货单位',
  `shipping_date` date DEFAULT NULL COMMENT '出货日期',
  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发编号汇总',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '出货单明细行数',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '合计双数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '合计件数/箱数',
  `data_json` json NOT NULL COMMENT '本次出货单打印数据快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_shipping_note_order_id` (`order_id`),
  KEY `idx_shipping_note_order_no` (`order_no`),
  KEY `idx_shipping_note_development_nos` (`development_nos`(191)),
  KEY `idx_shipping_note_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出货单打印记录表';
