CREATE TABLE IF NOT EXISTS `order_sheet_print_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_id` bigint NOT NULL COMMENT '订单主表ID',

  `print_type` tinyint NOT NULL COMMENT '打印类型: 1订单 2装箱单',
  `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始订单文件名',
  `original_file_path` varchar(512) DEFAULT NULL COMMENT '原始订单文件本地路径',

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
  KEY `idx_sheet_print_task_order_id` (`order_id`),
  KEY `idx_sheet_print_task_type_status` (`print_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单和装箱单Sheet打印任务表';

ALTER TABLE `order_sheet_print_task`
  ADD COLUMN IF NOT EXISTS `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始订单文件名' AFTER `print_type`,
  ADD COLUMN IF NOT EXISTS `original_file_path` varchar(512) DEFAULT NULL COMMENT '原始订单文件本地路径' AFTER `original_file_name`;

INSERT INTO `order_sheet_print_task` (
  `order_id`,
  `print_type`,
  `original_file_name`,
  `original_file_path`,
  `status`,
  `preview_pdf_path`,
  `pdf_generated_at`,
  `print_count`,
  `created_at`,
  `updated_at`
)
SELECT
  o.`id`,
  1,
  o.`original_file_name`,
  o.`original_file_path`,
  CASE WHEN COALESCE(o.`order_printed`, 0) = 1 THEN 2 ELSE 1 END,
  o.`order_pdf_path`,
  o.`order_pdf_generated_at`,
  CASE WHEN COALESCE(o.`order_printed`, 0) = 1 THEN 1 ELSE 0 END,
  o.`created_at`,
  CURRENT_TIMESTAMP
FROM `order_record` o
WHERE NOT EXISTS (
  SELECT 1
  FROM `order_sheet_print_task` t
  WHERE t.`order_id` = o.`id`
    AND t.`print_type` = 1
);

INSERT INTO `order_sheet_print_task` (
  `order_id`,
  `print_type`,
  `original_file_name`,
  `original_file_path`,
  `status`,
  `preview_pdf_path`,
  `pdf_generated_at`,
  `print_count`,
  `created_at`,
  `updated_at`
)
SELECT
  o.`id`,
  2,
  o.`original_file_name`,
  o.`original_file_path`,
  CASE WHEN COALESCE(o.`packing_printed`, 0) = 1 THEN 2 ELSE 1 END,
  o.`packing_pdf_path`,
  o.`packing_pdf_generated_at`,
  CASE WHEN COALESCE(o.`packing_printed`, 0) = 1 THEN 1 ELSE 0 END,
  o.`created_at`,
  CURRENT_TIMESTAMP
FROM `order_record` o
WHERE NOT EXISTS (
  SELECT 1
  FROM `order_sheet_print_task` t
  WHERE t.`order_id` = o.`id`
    AND t.`print_type` = 2
);

UPDATE `order_sheet_print_task` t
JOIN `order_record` o ON o.`id` = t.`order_id`
SET
  t.`original_file_name` = IF(t.`original_file_name` IS NULL OR t.`original_file_name` = '', o.`original_file_name`, t.`original_file_name`),
  t.`original_file_path` = IF(t.`original_file_path` IS NULL OR t.`original_file_path` = '', o.`original_file_path`, t.`original_file_path`)
WHERE t.`original_file_name` IS NULL
   OR t.`original_file_name` = ''
   OR t.`original_file_path` = ''
   OR t.`original_file_path` IS NULL;

ALTER TABLE `order_record`
  DROP COLUMN IF EXISTS `order_printed`,
  DROP COLUMN IF EXISTS `packing_printed`,
  DROP COLUMN IF EXISTS `order_pdf_path`,
  DROP COLUMN IF EXISTS `packing_pdf_path`,
  DROP COLUMN IF EXISTS `order_pdf_generated_at`,
  DROP COLUMN IF EXISTS `packing_pdf_generated_at`,
  DROP COLUMN IF EXISTS `original_file_name`,
  DROP COLUMN IF EXISTS `original_file_path`;
