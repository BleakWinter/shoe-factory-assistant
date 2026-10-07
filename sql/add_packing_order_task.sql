-- 订包装专用表；先选择实际使用的业务数据库。
-- 可重复执行，不删除或覆盖已有记录。此脚本不涉及其他配件下单。
CREATE TABLE IF NOT EXISTS `packing_order_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no` varchar(64) NOT NULL COMMENT '订包装记录编号',
  `order_date` date NOT NULL COMMENT '订包装日期',
  `order_nos` varchar(1024) DEFAULT NULL COMMENT '订单流水号汇总',
  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发款号汇总',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '订单与款号组合数量',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '总双数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '总箱数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_packing_order_task_no` (`task_no`),
  KEY `idx_packing_order_task_order_nos` (`order_nos`(191)),
  KEY `idx_packing_order_task_development_nos` (`development_nos`(191)),
  KEY `idx_packing_order_task_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订包装主表';

CREATE TABLE IF NOT EXISTS `packing_order_task_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '订包装记录ID',
  `line_no` int NOT NULL DEFAULT 0 COMMENT '记录内显示顺序',
  `order_id` bigint NOT NULL COMMENT '来源订单ID',
  `order_no` varchar(128) DEFAULT NULL COMMENT '保存时的订单流水号',
  `development_no` varchar(128) NOT NULL COMMENT '开发款号',
  `box_spec` varchar(100) NOT NULL COMMENT '内盒规格',
  `inner_box_type_id` bigint DEFAULT NULL COMMENT '保存时的内盒类型ID',
  `inner_box_type_name` varchar(100) DEFAULT NULL COMMENT '保存时的内盒类型版本名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '订包装备注',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '该款总双数',
  `carton_count` int NOT NULL DEFAULT 0 COMMENT '该款总箱数',
  `packing_capacities_json` longtext NOT NULL COMMENT '各装法箱数，如12装10箱',
  `source_detail_ids_json` longtext NOT NULL COMMENT '合并的来源订单明细ID列表',
  `source_packing_detail_ids_json` longtext NOT NULL COMMENT '来源装箱明细ID列表',
  `style_image_path` varchar(1024) DEFAULT NULL COMMENT '款式图片文件引用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_packing_order_task_item_style` (`task_id`, `order_id`, `development_no`),
  KEY `idx_packing_order_task_item_order_id` (`order_id`),
  KEY `idx_packing_order_task_item_development_no` (`development_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订包装明细表：一条为同订单同款的汇总';
