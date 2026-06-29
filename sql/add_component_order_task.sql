CREATE TABLE IF NOT EXISTS `component_order_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no` varchar(64) NOT NULL COMMENT '配件下单任务编号',
  `process_type` tinyint NOT NULL COMMENT '下单类型: 1订包装, 2订大底, 3订中底, 4订鞋跟',
  `order_nos` varchar(1024) DEFAULT NULL COMMENT '订单流水号汇总',
  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发编号汇总',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '下单明细行数',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '合计双数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '合计箱数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_component_order_task_no` (`task_no`),
  KEY `idx_component_order_task_type` (`process_type`),
  KEY `idx_component_order_task_order_nos` (`order_nos`(191)),
  KEY `idx_component_order_task_development_nos` (`development_nos`(191)),
  KEY `idx_component_order_task_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配件下单任务表';

CREATE TABLE IF NOT EXISTS `component_order_task_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '配件下单任务ID',
  `line_no` int NOT NULL DEFAULT 0 COMMENT '任务内行号',
  `order_id` bigint DEFAULT NULL COMMENT '订单主表ID',
  `source_detail_id` bigint NOT NULL COMMENT '来源订单明细ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_component_order_task_item_task_id` (`task_id`),
  KEY `idx_component_order_task_item_order_id` (`order_id`),
  KEY `idx_component_order_task_item_source_detail_id` (`source_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配件下单任务明细表';
