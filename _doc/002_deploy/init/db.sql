-- ============================================================
-- z-oss 对象存储数据库 (FEATURE027)
-- 2026-06-24
-- 实体: OssBucket, OssObject, OssUser, OssPart, OssMultipart
-- ============================================================

CREATE
DATABASE IF NOT EXISTS z_oss CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE
z_oss;

-- 用户表
DROP TABLE IF EXISTS `oss_user`;
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
    `password` VARCHAR
(
    128
) NOT NULL COMMENT '密码',
    `email` VARCHAR
(
    128
) DEFAULT NULL COMMENT '邮箱',
    `access_key` VARCHAR
(
    64
) DEFAULT NULL COMMENT 'AccessKey',
    `secret_key` VARCHAR
(
    128
) DEFAULT NULL COMMENT 'SecretKey',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_username`
(
    `username`
),
    UNIQUE KEY `uk_access_key`
(
    `access_key`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OSS 用户表';

-- 桶表
DROP TABLE IF EXISTS `oss_bucket`;
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
    128
) NOT NULL COMMENT '桶名',
    `user_id` BIGINT DEFAULT NULL COMMENT '所属用户',
    `region` VARCHAR
(
    32
) DEFAULT 'default' COMMENT '区域',
    `policy` VARCHAR
(
    32
) DEFAULT 'private' COMMENT '桶策略',
    `acl` VARCHAR
(
    32
) DEFAULT 'private' COMMENT '访问控制',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_name`
(
    `name`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OSS 桶表';

-- 对象元数据表
DROP TABLE IF EXISTS `oss_object`;
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
    128
) NOT NULL COMMENT '桶名',
    `object_key` VARCHAR
(
    1024
) NOT NULL COMMENT '对象 key',
    `object_name` VARCHAR
(
    255
) DEFAULT NULL COMMENT '对象名',
    `content_type` VARCHAR
(
    128
) DEFAULT 'application/octet-stream' COMMENT 'MIME 类型',
    `content_length` BIGINT DEFAULT 0 COMMENT '字节数',
    `etag` VARCHAR
(
    64
) DEFAULT NULL COMMENT 'ETag',
    `storage_path` VARCHAR
(
    1024
) DEFAULT NULL COMMENT '本地存储路径',
    `metadata` TEXT DEFAULT NULL COMMENT '自定义元数据 JSON',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY
(
    `id`
),
    KEY `idx_bucket_id`
(
    `bucket_id`
),
    KEY `idx_object_key`
(
    `object_key`
(
    255
))
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OSS 对象元数据表';

-- 分片上传 part 表
DROP TABLE IF EXISTS `oss_part`;
CREATE TABLE IF NOT EXISTS `oss_part`
(
    `id`
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '主键',
    `upload_id`
    VARCHAR
(
    64
) NOT NULL COMMENT 'uploadId',
    `object_key` VARCHAR
(
    1024
) NOT NULL COMMENT '对象 key',
    `part_number` INT NOT NULL COMMENT '分片号 (1-10000)',
    `etag` VARCHAR
(
    64
) DEFAULT NULL COMMENT 'part ETag',
    `size` BIGINT DEFAULT 0 COMMENT '字节数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_upload_part`
(
    `upload_id`,
    `object_key`
(
    255
), `part_number`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OSS 分片上传 part 表';

-- 分片上传元信息
DROP TABLE IF EXISTS `oss_multipart`;
CREATE TABLE IF NOT EXISTS `oss_multipart`
(
    `id`
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '主键',
    `upload_id`
    VARCHAR
(
    64
) NOT NULL COMMENT 'uploadId',
    `object_key` VARCHAR
(
    1024
) NOT NULL COMMENT '对象 key',
    `bucket_name` VARCHAR
(
    128
) NOT NULL COMMENT '桶名',
    `total_parts` INT DEFAULT 0 COMMENT '总分片数',
    `initiated_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '初始化时间',
    `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_upload_id`
(
    `upload_id`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OSS 分片上传元信息';
