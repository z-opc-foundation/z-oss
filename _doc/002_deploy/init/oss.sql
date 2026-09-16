-- z-oss 数据库初始化脚本

-- 用户表（存储AK/SK）
CREATE TABLE IF NOT EXISTS `oss_user`
(
    `id`
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '主键',
    `username`
    VARCHAR
(
    64
) NOT NULL COMMENT '用户名',
    `access_key` VARCHAR
(
    64
) NOT NULL COMMENT 'Access Key',
    `secret_key` VARCHAR
(
    128
) NOT NULL COMMENT 'Secret Key（加密存储）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_access_key`
(
    `access_key`
),
    KEY `idx_username`
(
    `username`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS用户表';

-- 存储桶表
CREATE TABLE IF NOT EXISTS `oss_bucket`
(
    `id`
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '主键',
    `name`
    VARCHAR
(
    64
) NOT NULL COMMENT '桶名称',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `region` VARCHAR
(
    32
) NOT NULL DEFAULT 'default' COMMENT '区域',
    `policy` TEXT COMMENT '桶策略（JSON）',
    `acl` VARCHAR
(
    32
) NOT NULL DEFAULT 'private' COMMENT '访问控制',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_name_user`
(
    `name`,
    `user_id`
),
    KEY `idx_user_id`
(
    `user_id`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储桶表';

-- 对象元数据表
CREATE TABLE IF NOT EXISTS `oss_object`
(
    `id`
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '主键',
    `bucket_id`
    BIGINT
    NOT
    NULL
    COMMENT
    '桶ID',
    `bucket_name`
    VARCHAR
(
    64
) NOT NULL COMMENT '桶名称',
    `object_key` VARCHAR
(
    512
) NOT NULL COMMENT '对象键',
    `object_name` VARCHAR
(
    256
) COMMENT '对象名称',
    `content_type` VARCHAR
(
    128
) COMMENT '内容类型',
    `content_length` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    `etag` VARCHAR
(
    64
) COMMENT 'ETag',
    `storage_path` VARCHAR
(
    512
) NOT NULL COMMENT '存储路径',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `version_id` VARCHAR
(
    64
) COMMENT '版本ID',
    `is_folder` TINYINT NOT NULL DEFAULT 0 COMMENT '是否文件夹',
    `metadata` TEXT COMMENT '自定义元数据',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY
(
    `id`
),
    KEY `idx_bucket_id`
(
    `bucket_id`
),
    KEY `idx_user_id`
(
    `user_id`
),
    KEY `idx_bucket_key`
(
    `bucket_name`,
    `object_key`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象元数据表';

-- 初始化测试用户
INSERT INTO `oss_user` (`username`, `access_key`, `secret_key`)
VALUES ('admin', 'zossadmin', 'zossadmin123456');