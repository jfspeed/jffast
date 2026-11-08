CREATE TABLE IF NOT EXISTS `demo_example` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `title`           VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '标题',
    `content`         TEXT            NULL     COMMENT '内容',
    `description`     VARCHAR(500)    NULL     COMMENT '描述',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `type`            VARCHAR(50)     NULL     COMMENT '类型',
    `priority`        INT             NOT NULL DEFAULT 0 COMMENT '优先级',
    `score`           DECIMAL(5,2)    NULL     COMMENT '评分',
    `quantity`        INT             NULL     COMMENT '数量',
    `price`           DECIMAL(10,2)   NULL     COMMENT '价格',
    `is_recommend`    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否推荐：0-否，1-是',
    `is_deleted`      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    `sort`            INT             NOT NULL DEFAULT 0 COMMENT '排序',
    `tags`            VARCHAR(500)    NULL     COMMENT '标签（逗号分隔）',
    `email`           VARCHAR(100)    NULL     COMMENT '邮箱',
    `phone`           VARCHAR(20)     NULL     COMMENT '手机号',
    `url`             VARCHAR(500)    NULL     COMMENT '链接地址',
    `icon`            VARCHAR(200)    NULL     COMMENT '图标',
    `image`           VARCHAR(500)    NULL     COMMENT '图片',
    `publish_time`    DATETIME        NULL     COMMENT '发布时间',
    `start_date`      DATE            NULL     COMMENT '开始日期',
    `end_date`        DATE            NULL     COMMENT '结束日期',
    `remark`          TEXT            NULL     COMMENT '备注',
    `version`         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_type` (`type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='示例表';

-- 默认示例记录：便于用户登录后直接在「示例管理」体验增删改查、状态 switch 切换、图片上传
INSERT IGNORE INTO `demo_example` (`id`, `title`, `content`, `description`, `status`, `type`, `priority`, `score`, `quantity`, `price`, `is_recommend`, `is_deleted`, `sort`, `tags`, `email`, `phone`, `url`, `icon`, `image`, `publish_time`, `start_date`, `end_date`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, '疾风示例数据', '这是一条用于功能验证的默认示例内容。', '默认示例，便于直接体验示例管理的增删改查与状态切换。', 1, 'type_a', 5, 9.50, 100, 199.00, 1, 0, 1, '热门,推荐', 'demo@example.com', '13800000000', 'https://example.com', 'app', '', '2026-09-09 12:00:00', '2026-09-01', '2026-12-31', '由初始化 SQL 写入的默认记录', 1, NOW(), 1, NOW());

-- 明细子表：演示「主表 + 明细」的统一事务保存与 /detail/{id} 回显
CREATE TABLE IF NOT EXISTS `demo_example_item` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `example_id`      BIGINT          NOT NULL COMMENT '主表ID',
    `product_id`      BIGINT          NULL     COMMENT '物料ID',
    `product_name`    VARCHAR(200)    NULL     COMMENT '物料名称',
    `qty`             INT             NULL     COMMENT '数量',
    `price`           DECIMAL(10,2)   NULL     COMMENT '单价',
    `sort`            INT             NOT NULL DEFAULT 0 COMMENT '排序',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_example_id` (`example_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='示例明细表';

INSERT IGNORE INTO `demo_example_item` (`id`, `example_id`, `product_id`, `product_name`, `qty`, `price`, `sort`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 1, 1001, '示例物料A', 2, 99.50, 0, 1, NOW(), 1, NOW()),
(2, 1, 1002, '示例物料B', 1, 100.00, 1, 1, NOW(), 1, NOW());
