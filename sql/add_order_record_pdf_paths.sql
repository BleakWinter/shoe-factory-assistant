USE shoe_factory_assistant;

-- 已经建过三张新订单表时，执行这个脚本给 order_record 补上 PDF 路径字段。
-- 新装数据库直接执行 print_table.sql 即可，不需要跑本脚本。

ALTER TABLE order_record
  ADD COLUMN order_pdf_path varchar(512) DEFAULT NULL COMMENT '订单PDF本地路径' AFTER packing_printed,
  ADD COLUMN packing_pdf_path varchar(512) DEFAULT NULL COMMENT '装箱单PDF本地路径' AFTER order_pdf_path,
  ADD COLUMN order_pdf_generated_at datetime DEFAULT NULL COMMENT '订单PDF生成时间' AFTER packing_pdf_path,
  ADD COLUMN packing_pdf_generated_at datetime DEFAULT NULL COMMENT '装箱单PDF生成时间' AFTER order_pdf_generated_at;
