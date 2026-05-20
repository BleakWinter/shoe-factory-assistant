CREATE TABLE IF NOT EXISTS `order_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_no` varchar(128) DEFAULT NULL COMMENT '订单流水号',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客户名称',

  `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始订单文件名',
  `original_file_path` varchar(512) DEFAULT NULL COMMENT '原始订单文件本地路径',

  `box_image_url` varchar(512) DEFAULT NULL COMMENT '订单盒子图片访问链接',
  `box_image_path` varchar(512) DEFAULT NULL COMMENT '订单盒子图片本地路径',

  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发编号汇总，多个用逗号分隔，仅用于展示',

  `total_quantity` int NOT NULL DEFAULT 0 COMMENT '总对数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '总箱数',

  `source_type` tinyint DEFAULT NULL COMMENT '订单来源: 1 Excel, 2 图片, 3 手动录入',
  `recognition_status` tinyint NOT NULL DEFAULT 0 COMMENT '识别状态: 0待识别, 1已识别, 2待人工处理, 3识别失败',
  `order_recognition_status` tinyint NOT NULL DEFAULT 0 COMMENT '订单识别状态: 0待识别, 1已识别, 2待人工处理, 3识别失败',
  `packing_recognition_status` tinyint NOT NULL DEFAULT 0 COMMENT '装箱单识别状态: 0待识别, 1已识别, 2待人工处理, 3识别失败',

  `remark` varchar(1024) DEFAULT NULL COMMENT '备注',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '识别或处理错误信息',
  `order_error_message` varchar(1024) DEFAULT NULL COMMENT '订单识别错误信息',
  `packing_error_message` varchar(1024) DEFAULT NULL COMMENT '装箱单识别错误信息',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),

  KEY `idx_order_record_order_no` (`order_no`),
  KEY `idx_order_record_customer_name` (`customer_name`),
  KEY `idx_order_record_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';


CREATE TABLE IF NOT EXISTS `order_record_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_id` bigint NOT NULL COMMENT '订单主表ID',

  `line_no` int DEFAULT NULL COMMENT '明细行号',

  `last_no` varchar(128) DEFAULT NULL COMMENT '楦头号',

  `development_no` varchar(128) DEFAULT NULL COMMENT '款号/开发编号',

  `customer_name` varchar(128) DEFAULT NULL COMMENT '客人',

  `customer_order_no` varchar(255) DEFAULT NULL COMMENT '客人订单号',

  `warehouse_store_no` varchar(128) DEFAULT NULL COMMENT '仓库号/店铺号',

  `delivery_date` date DEFAULT NULL COMMENT '交期/出货日期',

  `po_no` varchar(128) DEFAULT NULL COMMENT 'PO号',

  `customer_style_no` varchar(128) DEFAULT NULL COMMENT '客人型体号',

  `style_image_url` varchar(512) DEFAULT NULL COMMENT '图片访问链接',

  `style_image_path` varchar(512) DEFAULT NULL COMMENT '图片本地路径',

  `english_color` varchar(255) DEFAULT NULL COMMENT '英文颜色',

  `english_material` varchar(255) DEFAULT NULL COMMENT '英文材质',

  `upper_material` text DEFAULT NULL COMMENT '面料',

  `lining_material` text DEFAULT NULL COMMENT '里料/垫脚',

  `accessory` varchar(512) DEFAULT NULL COMMENT '饰扣/鞋带',

  `insole_platform` varchar(512) DEFAULT NULL COMMENT '包中底/水台',

  `outsole` text DEFAULT NULL COMMENT '大底',

  `trademark` varchar(255) DEFAULT NULL COMMENT '商标',

  `size_quantities_json` json DEFAULT NULL COMMENT '尺码数量JSON，例如 {"35":10,"36":20}',

  `quantity` int NOT NULL DEFAULT 0 COMMENT '双数',

  `carton_count` int NOT NULL DEFAULT 0 COMMENT '箱数',

  `carton_start` varchar(128) DEFAULT NULL COMMENT 'CTN START/开始箱号',

  `carton_end` varchar(128) DEFAULT NULL COMMENT 'CTN END/结束箱号',

  `source_sheet_name` varchar(128) DEFAULT NULL COMMENT '来源sheet',

  `row_index` int DEFAULT NULL COMMENT 'Excel行号',

  `remark` varchar(1024) DEFAULT NULL COMMENT '备注',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),

  KEY `idx_detail_order_id` (`order_id`),
  KEY `idx_detail_development_no` (`development_no`),
  KEY `idx_detail_last_no` (`last_no`),
  KEY `idx_detail_delivery_date` (`delivery_date`),
  KEY `idx_detail_carton_range` (`carton_start`, `carton_end`),
  KEY `idx_detail_row_index` (`order_id`, `row_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';


CREATE TABLE IF NOT EXISTS `order_packing_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `line_no` int DEFAULT NULL COMMENT '明细行号',

  `style_image_path` varchar(512) DEFAULT NULL COMMENT '图片本地路径',
  `company_style_no` varchar(128) DEFAULT NULL COMMENT '公司款号',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客人',
  `customer_order_no` varchar(255) DEFAULT NULL COMMENT '客人订单号',
  `warehouse_store_no` varchar(128) DEFAULT NULL COMMENT '仓库号/店铺号',
  `po_no` varchar(128) DEFAULT NULL COMMENT 'PO号',
  `customer_style_no` varchar(128) DEFAULT NULL COMMENT 'STYLE/客人款号',
  `customer_color` varchar(255) DEFAULT NULL COMMENT 'COLOR/客人颜色',
  `material` varchar(255) DEFAULT NULL COMMENT 'MATERIAL/面料材质',
  `item_number` varchar(128) DEFAULT NULL COMMENT 'ITEM NUMBER/项目编号',
  `trademark` varchar(255) DEFAULT NULL COMMENT '商标',
  `size_quantities_json` json DEFAULT NULL COMMENT '尺码数量JSON，例如 {"6.5/36":10}',

  `carton_count` int NOT NULL DEFAULT 0 COMMENT 'CTNS',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT 'TTL PRS',
  `carton_start` varchar(128) DEFAULT NULL COMMENT 'CTN START/开始箱号',
  `carton_end` varchar(128) DEFAULT NULL COMMENT 'CTN END/结束箱号',


  `source_sheet_name` varchar(128) DEFAULT NULL COMMENT '来源sheet',
  `row_index` int DEFAULT NULL COMMENT 'Excel行号',
  `remark` varchar(1024) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),

  KEY `idx_packing_order_id` (`order_id`),
  KEY `idx_packing_company_style_no` (`company_style_no`),
  KEY `idx_packing_carton_range` (`carton_start`, `carton_end`),
  KEY `idx_packing_row_index` (`order_id`, `row_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='装箱单明细表';


CREATE TABLE IF NOT EXISTS `order_detail_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `order_detail_id` bigint NOT NULL COMMENT '订单明细ID',

  `process_type` tinyint NOT NULL COMMENT '处理类型: 1订包装, 2定大底, 3定中底, 4定跟, 5内盒贴标, 6外箱贴标',

  `process_status` tinyint NOT NULL DEFAULT 1 COMMENT '处理状态: 1已处理',

  `process_count` int NOT NULL DEFAULT 1 COMMENT '处理次数',

  `last_process_at` datetime DEFAULT NULL COMMENT '最后处理时间',

  `remark` varchar(1024) DEFAULT NULL COMMENT '备注',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_detail_process` (`order_detail_id`, `process_type`),

  KEY `idx_process_order_id` (`order_id`),
  KEY `idx_process_detail_id` (`order_detail_id`),
  KEY `idx_process_type` (`process_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细处理状态表';


CREATE TABLE IF NOT EXISTS `order_print_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',

  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `order_detail_id` bigint DEFAULT NULL COMMENT '订单明细ID，订单/装箱单打印为空',

  `print_type` tinyint NOT NULL COMMENT '打印类型: 1订单 2装箱单 3出货单 4外箱贴 5内盒贴 6标签',

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
  KEY `idx_print_task_order_id` (`order_id`),
  KEY `idx_print_task_detail_id` (`order_detail_id`),
  KEY `idx_print_task_type_status` (`print_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打印任务表';


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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置表（盒规、净重、毛重、鞋价）';


CREATE TABLE IF NOT EXISTS `shipping_note_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `print_no` varchar(64) NOT NULL COMMENT '出货单打印编号',
  `order_id` bigint NOT NULL COMMENT '订单主表ID',
  `order_no` varchar(128) DEFAULT NULL COMMENT '订单流水号',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客人',
  `recipient_name` varchar(128) DEFAULT NULL COMMENT '收货单位',
  `shipping_date` date DEFAULT NULL COMMENT '出货日期',
  `development_nos` varchar(1024) DEFAULT NULL COMMENT '开发编号汇总',
  `item_count` int NOT NULL DEFAULT 0 COMMENT '出货单明细行数',
  `total_pairs` int NOT NULL DEFAULT 0 COMMENT '合计双数',
  `total_carton_count` int NOT NULL DEFAULT 0 COMMENT '合计件数/箱数',
  `data_json` json NOT NULL COMMENT '本次出货单打印数据快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_shipping_note_order_id` (`order_id`),
  KEY `idx_shipping_note_order_no` (`order_no`),
  KEY `idx_shipping_note_development_nos` (`development_nos`(191)),
  KEY `idx_shipping_note_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出货单打印记录表';
