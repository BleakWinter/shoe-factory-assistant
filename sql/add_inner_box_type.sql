-- 先选择实际业务数据库，并执行 add_packing_order_task.sql。
-- 可重复执行；只新增类型目录和字段，保留已有订包装记录及旧列的数据。
CREATE TABLE IF NOT EXISTS `inner_box_type_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '内盒类型和版本名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '类型备注',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inner_box_type_config_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内盒类型与版本目录';

SET @sfa_inner_box_type_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'shoe_style_config' AND COLUMN_NAME = 'inner_box_type_id'),
  'SELECT 1',
  'ALTER TABLE `shoe_style_config` ADD COLUMN `inner_box_type_id` bigint DEFAULT NULL COMMENT ''默认内盒类型ID'''
);
PREPARE sfa_inner_box_type_stmt FROM @sfa_inner_box_type_ddl;
EXECUTE sfa_inner_box_type_stmt;
DEALLOCATE PREPARE sfa_inner_box_type_stmt;

SET @sfa_inner_box_type_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'packing_order_task_item' AND COLUMN_NAME = 'inner_box_type_id'),
  'SELECT 1',
  'ALTER TABLE `packing_order_task_item` ADD COLUMN `inner_box_type_id` bigint DEFAULT NULL COMMENT ''保存时的内盒类型ID'''
);
PREPARE sfa_inner_box_type_stmt FROM @sfa_inner_box_type_ddl;
EXECUTE sfa_inner_box_type_stmt;
DEALLOCATE PREPARE sfa_inner_box_type_stmt;

SET @sfa_inner_box_type_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'packing_order_task_item' AND COLUMN_NAME = 'inner_box_type_name'),
  'SELECT 1',
  'ALTER TABLE `packing_order_task_item` ADD COLUMN `inner_box_type_name` varchar(100) DEFAULT NULL COMMENT ''保存时的内盒类型版本名称'''
);
PREPARE sfa_inner_box_type_stmt FROM @sfa_inner_box_type_ddl;
EXECUTE sfa_inner_box_type_stmt;
DEALLOCATE PREPARE sfa_inner_box_type_stmt;
