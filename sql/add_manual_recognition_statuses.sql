ALTER TABLE `order_record`
  ADD COLUMN `order_recognition_status` tinyint NOT NULL DEFAULT 0 COMMENT '订单识别状态: 0待识别, 1已识别, 2待人工处理, 3识别失败' AFTER `recognition_status`,
  ADD COLUMN `packing_recognition_status` tinyint NOT NULL DEFAULT 0 COMMENT '装箱单识别状态: 0待识别, 1已识别, 2待人工处理, 3识别失败' AFTER `order_recognition_status`,
  ADD COLUMN `order_error_message` varchar(1024) DEFAULT NULL COMMENT '订单识别错误信息' AFTER `error_message`,
  ADD COLUMN `packing_error_message` varchar(1024) DEFAULT NULL COMMENT '装箱单识别错误信息' AFTER `order_error_message`;

UPDATE `order_record`
SET
  `order_recognition_status` = `recognition_status`,
  `packing_recognition_status` = `recognition_status`,
  `order_error_message` = `error_message`,
  `packing_error_message` = `error_message`
WHERE `recognition_status` IS NOT NULL
   OR `error_message` IS NOT NULL;
