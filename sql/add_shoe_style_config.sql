CREATE TABLE IF NOT EXISTS `shoe_style_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `development_no` varchar(128) NOT NULL COMMENT '开发编号',
  `box_spec` varchar(128) DEFAULT NULL COMMENT '盒规',
  `net_weight_per_pair` decimal(10,3) DEFAULT NULL COMMENT '净重/双，单位 kg',
  `gross_weight_per_pair` decimal(10,3) DEFAULT NULL COMMENT '毛重/双，单位 kg',
  `shoe_price` decimal(10,2) DEFAULT NULL COMMENT '鞋子单价',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shoe_style_config_development_no` (`development_no`),
  KEY `idx_shoe_style_config_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开发编号配置表';
