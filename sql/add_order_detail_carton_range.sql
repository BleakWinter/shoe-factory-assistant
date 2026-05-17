ALTER TABLE `order_record_detail`
  ADD COLUMN `carton_start` varchar(128) DEFAULT NULL COMMENT 'CTN START/开始箱号' AFTER `carton_count`,
  ADD COLUMN `carton_end` varchar(128) DEFAULT NULL COMMENT 'CTN END/结束箱号' AFTER `carton_start`,
  ADD KEY `idx_detail_carton_range` (`carton_start`, `carton_end`);
