-- MySQL 8：为已创建的报价表补充中文表备注和字段备注。
-- 保留原字段类型、默认值和索引，不修改业务数据；可重复执行。
SET NAMES utf8mb4;

ALTER TABLE shoe_quotation
    MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '报价版本主键',
    MODIFY COLUMN version_group varchar(36) NOT NULL COMMENT '报价版本组 UUID，同一报价的各版本共用',
    MODIFY COLUMN version_no int NOT NULL COMMENT '组内版本号，从 1 开始递增',
    MODIFY COLUMN customer_id bigint DEFAULT NULL COMMENT '客户 ID，关联 customer_config.id；草稿可为空',
    MODIFY COLUMN development_no varchar(128) NOT NULL DEFAULT '' COMMENT '开发款号；草稿可为空字符串',
    MODIFY COLUMN last_no varchar(64) DEFAULT NULL COMMENT '楦号',
    MODIFY COLUMN color_material varchar(255) DEFAULT NULL COMMENT '鞋款颜色或材料说明',
    MODIFY COLUMN quotation_date date DEFAULT NULL COMMENT '报价日期',
    MODIFY COLUMN image_key varchar(128) DEFAULT NULL COMMENT '报价归档鞋图的相对存储路径',
    MODIFY COLUMN labor_cost decimal(12,4) DEFAULT NULL COMMENT '每双加工费（元）；NULL 表示待填写',
    MODIFY COLUMN include_outsole tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否计入大底、中底和鞋跟费用：0 否，1 是',
    MODIFY COLUMN rounding_mode varchar(16) NOT NULL DEFAULT 'CEIL' COMMENT '取整方式：CEIL 向上整元，CENT 保留两位小数，ROUND 四舍五入整元',
    MODIFY COLUMN manual_price tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否手动指定最终报价：0 自动计算，1 手动',
    MODIFY COLUMN final_price decimal(10,2) DEFAULT NULL COMMENT '最终报价（元/双）；NULL 表示待填写',
    MODIFY COLUMN material_amount decimal(28,8) DEFAULT NULL COMMENT '鞋面材料费用小计快照（元/双）；NULL 表示未填完整',
    MODIFY COLUMN auxiliary_amount decimal(28,8) DEFAULT NULL COMMENT '包装与辅料费用小计快照（元/双）；NULL 表示未填完整',
    MODIFY COLUMN outsole_amount decimal(28,8) DEFAULT NULL COMMENT '底料费用小计快照（元/双）；不计入时为 0，未填完整时为 NULL',
    MODIFY COLUMN calculated_amount decimal(28,8) DEFAULT NULL COMMENT '取整前成本合计快照（元/双）；NULL 表示未填完整',
    MODIFY COLUMN note varchar(2000) DEFAULT NULL COMMENT '整份报价的备注或报价条件',
    MODIFY COLUMN adopted_at datetime DEFAULT NULL COMMENT '本版本最近一次采用为客户单价的时间；NULL 表示未采用',
    MODIFY COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报价版本保存时间',
    COMMENT='鞋款报价版本表；保存报价不生效，采用后更新客户单价',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE shoe_quotation_item
    MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '报价明细主键',
    MODIFY COLUMN quotation_id bigint NOT NULL COMMENT '所属报价版本 ID，关联 shoe_quotation.id',
    MODIFY COLUMN category varchar(16) NOT NULL COMMENT '项目类别：MATERIAL 鞋面材料，FEE 包装与辅料，OUTSOLE 底料',
    MODIFY COLUMN sort_no int NOT NULL COMMENT '项目排序序号，从 0 开始',
    MODIFY COLUMN part_name varchar(100) DEFAULT NULL COMMENT '使用部位或费用项目名称',
    MODIFY COLUMN material_name varchar(255) DEFAULT NULL COMMENT '材料或工序名称',
    MODIFY COLUMN quantity decimal(12,4) DEFAULT NULL COMMENT '每双用量；NULL 表示待填写',
    MODIFY COLUMN unit varchar(16) DEFAULT NULL COMMENT '用量单位，如尺、码、双',
    MODIFY COLUMN unit_price decimal(12,4) DEFAULT NULL COMMENT '单位用量的单价（元）；NULL 表示待填写',
    MODIFY COLUMN amount decimal(24,8) DEFAULT NULL COMMENT '项目金额快照（元/双），用量乘以单价；NULL 表示未填完整',
    MODIFY COLUMN remark varchar(1000) DEFAULT NULL COMMENT '该项目的备注',
    COMMENT='鞋款报价明细表；保存各报价版本的材料与费用项目',
    ALGORITHM=INPLACE, LOCK=NONE;
