ALTER TABLE `order_print_task`
  DROP COLUMN IF EXISTS `print_scope`,
  DROP COLUMN IF EXISTS `source_file_name`,
  DROP COLUMN IF EXISTS `source_file_path`,
  DROP COLUMN IF EXISTS `source_sheet_name`,
  DROP COLUMN IF EXISTS `development_no`,
  DROP COLUMN IF EXISTS `carton_start`,
  DROP COLUMN IF EXISTS `carton_end`;
