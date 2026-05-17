ALTER TABLE `shoe_style_config`
  ADD COLUMN `shoe_price` decimal(10,2) DEFAULT NULL COMMENT '鞋子单价'
  AFTER `gross_weight_per_pair`;
