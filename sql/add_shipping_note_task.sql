DROP TABLE IF EXISTS `shipping_note_record`;

CREATE TABLE IF NOT EXISTS `shipping_note_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no` varchar(64) NOT NULL COMMENT '出货单任务编号',
  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `order_no` varchar(128) DEFAULT NULL COMMENT '订单流水号',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客人',
  `recipient_name` varchar(128) DEFAULT NULL COMMENT '收货单位',
  `shipping_date` date DEFAULT NULL COMMENT '出货日期',
  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发编号汇总',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '出货单明细行数',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '合计双数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '合计件数/箱数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_note_task_no` (`task_no`),
  KEY `idx_shipping_note_task_order_id` (`order_id`),
  KEY `idx_shipping_note_task_order_no` (`order_no`),
  KEY `idx_shipping_note_task_development_nos` (`development_nos`(191)),
  KEY `idx_shipping_note_task_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出货单打印任务表';

CREATE TABLE IF NOT EXISTS `shipping_note_task_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '出货单任务ID',
  `line_no` int NOT NULL DEFAULT 0 COMMENT '任务内行号',
  `source_detail_id` bigint NOT NULL COMMENT '来源订单明细ID',
  `order_no` varchar(128) DEFAULT NULL COMMENT '订单流水号',
  `development_no` varchar(128) DEFAULT NULL COMMENT '开发编号',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客人',
  `customer_style_no` varchar(128) DEFAULT NULL COMMENT '客人型体号',
  `english_color` varchar(255) DEFAULT NULL COMMENT '英文颜色',
  `english_material` varchar(255) DEFAULT NULL COMMENT '英文材质',
  `color_material` text DEFAULT NULL COMMENT '颜色/材质展示文本',
  `trademark` varchar(255) DEFAULT NULL COMMENT '商标',
  `size_quantities_json` json DEFAULT NULL COMMENT '尺码数量JSON',
  `pair_count` int NOT NULL DEFAULT 0 COMMENT '本行双数',
  `carton_count` int NOT NULL DEFAULT 0 COMMENT '本行件数/箱数',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '本行合计双数',
  `carton_start` varchar(128) DEFAULT NULL COMMENT '开始箱号',
  `carton_end` varchar(128) DEFAULT NULL COMMENT '结束箱号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_shipping_note_task_item_task_id` (`task_id`),
  KEY `idx_shipping_note_task_item_source_detail_id` (`source_detail_id`),
  KEY `idx_shipping_note_task_item_development_no` (`development_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出货单打印任务明细表';
