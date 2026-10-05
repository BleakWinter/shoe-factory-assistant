-- 仅用于独立测试数据库，演示旧版达为数据迁移。
SET NAMES utf8mb4;
INSERT INTO order_record (id,order_no,customer_name,total_quantity,source_type,order_recognition_status,created_at)
VALUES (900001,'LEGACY-DV','终端客人-Delmar',10,1,1,'2026-09-01 09:00:00');
INSERT INTO order_record_detail (id,order_id,line_no,development_no,customer_order_no,quantity,size_quantities_json,carton_count,created_at)
VALUES (900001,900001,1,'JCD-LEGACY-01','DV-TEST',10,'{"36":1,"37":1}',5,'2026-09-01 09:00:00');
INSERT INTO shoe_style_config (development_no,shoe_price) VALUES ('JCD-LEGACY-01',25.00);
INSERT INTO shipping_note_task (id,task_no,recipient_name,shipping_date,invoice_nos,total_pairs)
VALUES (900001,'SH-LEGACY','达为鞋业','2026-09-05','LEGACY-DV',10);
INSERT INTO shipping_note_task_item (task_id,order_id,source_detail_id,line_no)
VALUES (900001,900001,900001,1);
