ALTER TABLE `order_sheet_print_task`
  ADD COLUMN IF NOT EXISTS `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始订单文件名' AFTER `print_type`,
  ADD COLUMN IF NOT EXISTS `original_file_path` varchar(512) DEFAULT NULL COMMENT '原始订单文件本地路径' AFTER `original_file_name`;

UPDATE `order_sheet_print_task` t
JOIN `order_record` o ON o.`id` = t.`order_id`
SET
  t.`original_file_name` = IF(t.`original_file_name` IS NULL OR t.`original_file_name` = '', o.`original_file_name`, t.`original_file_name`),
  t.`original_file_path` = IF(t.`original_file_path` IS NULL OR t.`original_file_path` = '', o.`original_file_path`, t.`original_file_path`)
WHERE t.`original_file_name` IS NULL
   OR t.`original_file_name` = ''
   OR t.`original_file_path` IS NULL
   OR t.`original_file_path` = '';

ALTER TABLE `order_record`
  DROP COLUMN IF EXISTS `original_file_name`,
  DROP COLUMN IF EXISTS `original_file_path`;
