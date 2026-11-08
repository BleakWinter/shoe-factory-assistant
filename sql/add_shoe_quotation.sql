-- MySQL 8：先执行 add_multi_customer_ledger.sql，再执行本脚本。
-- 每次保存写入独立报价版本；不修改旧款配置或现有客户价格。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS shoe_quotation (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '报价版本主键',
    version_group varchar(36) NOT NULL COMMENT '报价版本组 UUID，同一报价的各版本共用',
    version_no int NOT NULL COMMENT '组内版本号，从 1 开始递增',
    customer_id bigint DEFAULT NULL COMMENT '客户 ID，关联 customer_config.id；草稿可为空',
    development_no varchar(128) NOT NULL DEFAULT '' COMMENT '开发款号；草稿可为空字符串',
    last_no varchar(64) DEFAULT NULL COMMENT '楦号',
    color_material varchar(255) DEFAULT NULL COMMENT '鞋款颜色或材料说明',
    quotation_date date DEFAULT NULL COMMENT '报价日期',
    image_key varchar(128) DEFAULT NULL COMMENT '报价归档鞋图的相对存储路径',
    labor_cost decimal(12,4) DEFAULT NULL COMMENT '每双加工费（元）；NULL 表示待填写',
    include_outsole tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否计入大底、中底和鞋跟费用：0 否，1 是',
    rounding_mode varchar(16) NOT NULL DEFAULT 'CEIL' COMMENT '取整方式：CEIL 向上整元，CENT 保留两位小数，ROUND 四舍五入整元',
    manual_price tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否手动指定最终报价：0 自动计算，1 手动',
    final_price decimal(10,2) DEFAULT NULL COMMENT '最终报价（元/双）；NULL 表示待填写',
    material_amount decimal(28,8) DEFAULT NULL COMMENT '鞋面材料费用小计快照（元/双）；NULL 表示未填完整',
    auxiliary_amount decimal(28,8) DEFAULT NULL COMMENT '包装与辅料费用小计快照（元/双）；NULL 表示未填完整',
    outsole_amount decimal(28,8) DEFAULT NULL COMMENT '底料费用小计快照（元/双）；不计入时为 0，未填完整时为 NULL',
    calculated_amount decimal(28,8) DEFAULT NULL COMMENT '取整前成本合计快照（元/双）；NULL 表示未填完整',
    note varchar(2000) DEFAULT NULL COMMENT '整份报价的备注或报价条件',
    adopted_at datetime DEFAULT NULL COMMENT '本版本最近一次采用为客户单价的时间；NULL 表示未采用',
    signed_image_key varchar(128) DEFAULT NULL COMMENT '客户签字报价单照片的相对存储路径；历史采用记录可为空',
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报价版本保存时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quotation_version (version_group,version_no),
    KEY idx_quotation_customer_style (customer_id,development_no),
    KEY idx_quotation_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鞋款报价版本表；保存报价不生效，采用后更新客户单价';

CREATE TABLE IF NOT EXISTS shoe_quotation_item (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '报价明细主键',
    quotation_id bigint NOT NULL COMMENT '所属报价版本 ID，关联 shoe_quotation.id',
    category varchar(16) NOT NULL COMMENT '项目类别：MATERIAL 鞋面材料，FEE 包装与辅料，OUTSOLE 底料',
    sort_no int NOT NULL COMMENT '项目排序序号，从 0 开始',
    part_name varchar(100) DEFAULT NULL COMMENT '使用部位或费用项目名称',
    material_name varchar(255) DEFAULT NULL COMMENT '材料或工序名称',
    quantity decimal(12,4) DEFAULT NULL COMMENT '每双用量；NULL 表示待填写',
    unit varchar(16) DEFAULT NULL COMMENT '用量单位，如尺、码、双',
    unit_price decimal(12,4) DEFAULT NULL COMMENT '单位用量的单价（元）；NULL 表示待填写',
    amount decimal(24,8) DEFAULT NULL COMMENT '项目金额快照（元/双），用量乘以单价；NULL 表示未填完整',
    remark varchar(1000) DEFAULT NULL COMMENT '该项目的备注',
    PRIMARY KEY (id),
    KEY idx_quotation_items (quotation_id,sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鞋款报价明细表；保存各报价版本的材料与费用项目';

-- 已有报价库重复执行本脚本即可升级，保留历史报价和采用记录。
SET @sfa_quotation_signed_image_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'shoe_quotation' AND COLUMN_NAME = 'signed_image_key'),
    'SELECT 1',
    'ALTER TABLE shoe_quotation ADD COLUMN signed_image_key varchar(128) DEFAULT NULL COMMENT ''客户签字报价单照片的相对存储路径；历史采用记录可为空'''
);
PREPARE sfa_quotation_signed_image_stmt FROM @sfa_quotation_signed_image_ddl;
EXECUTE sfa_quotation_signed_image_stmt;
DEALLOCATE PREPARE sfa_quotation_signed_image_stmt;
