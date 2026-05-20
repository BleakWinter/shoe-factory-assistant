CREATE TABLE IF NOT EXISTS `order_print_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `order_detail_id` bigint DEFAULT NULL COMMENT '订单明细ID，订单/装箱单打印为空',

  `print_type` tinyint NOT NULL COMMENT '打印类型: 1订单 2装箱单 3出货单 4外箱贴 5内盒贴 6标签',

  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 1待打印 2已打印 3失败 4已失效',
  `preview_pdf_path` varchar(512) DEFAULT NULL COMMENT 'PDF预览文件路径',
  `pdf_generated_at` datetime DEFAULT NULL COMMENT 'PDF生成时间',

  `print_count` int NOT NULL DEFAULT 0 COMMENT '累计成功打印次数',
  `last_print_time` datetime DEFAULT NULL COMMENT '最后打印时间',
  `last_print_user` varchar(64) DEFAULT NULL COMMENT '最后打印人',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '失败原因',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_print_task_order_id` (`order_id`),
  KEY `idx_print_task_detail_id` (`order_detail_id`),
  KEY `idx_print_task_type_status` (`print_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打印任务表';

INSERT INTO `order_print_task` (
  `order_id`,
  `order_detail_id`,
  `print_type`,
  `status`,
  `preview_pdf_path`,
  `pdf_generated_at`,
  `print_count`,
  `created_at`,
  `updated_at`
)
SELECT
  o.`id`,
  NULL,
  1,
  CASE WHEN COALESCE(o.`order_printed`, 0) = 1 THEN 2 ELSE 1 END,
  o.`order_pdf_path`,
  o.`order_pdf_generated_at`,
  CASE WHEN COALESCE(o.`order_printed`, 0) = 1 THEN 1 ELSE 0 END,
  o.`created_at`,
  CURRENT_TIMESTAMP
FROM `order_record` o
WHERE NOT EXISTS (
  SELECT 1
  FROM `order_print_task` t
  WHERE t.`order_id` = o.`id`
    AND t.`print_type` = 1
    AND t.`order_detail_id` IS NULL
);

INSERT INTO `order_print_task` (
  `order_id`,
  `order_detail_id`,
  `print_type`,
  `status`,
  `preview_pdf_path`,
  `pdf_generated_at`,
  `print_count`,
  `created_at`,
  `updated_at`
)
SELECT
  o.`id`,
  NULL,
  2,
  CASE WHEN COALESCE(o.`packing_printed`, 0) = 1 THEN 2 ELSE 1 END,
  o.`packing_pdf_path`,
  o.`packing_pdf_generated_at`,
  CASE WHEN COALESCE(o.`packing_printed`, 0) = 1 THEN 1 ELSE 0 END,
  o.`created_at`,
  CURRENT_TIMESTAMP
FROM `order_record` o
WHERE NOT EXISTS (
  SELECT 1
  FROM `order_print_task` t
  WHERE t.`order_id` = o.`id`
    AND t.`print_type` = 2
    AND t.`order_detail_id` IS NULL
);

ALTER TABLE `order_record`
  DROP COLUMN IF EXISTS `order_printed`,
  DROP COLUMN IF EXISTS `packing_printed`,
  DROP COLUMN IF EXISTS `order_pdf_path`,
  DROP COLUMN IF EXISTS `packing_pdf_path`,
  DROP COLUMN IF EXISTS `order_pdf_generated_at`,
  DROP COLUMN IF EXISTS `packing_pdf_generated_at`;

ALTER TABLE `order_print_task`
  DROP COLUMN IF EXISTS `print_scope`,
  DROP COLUMN IF EXISTS `source_file_name`,
  DROP COLUMN IF EXISTS `source_file_path`,
  DROP COLUMN IF EXISTS `source_sheet_name`,
  DROP COLUMN IF EXISTS `development_no`,
  DROP COLUMN IF EXISTS `carton_start`,
  DROP COLUMN IF EXISTS `carton_end`;
