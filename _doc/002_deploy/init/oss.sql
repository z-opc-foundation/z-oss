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

-- 分片上传会话表。local 后端的分片数据暂存在文件系统（root/.zmultipart/<uploadId>/），
-- 这张表记的是会话元数据：谁开的、归并到哪个 key、什么时候过期。
-- ⚠ 当前代码只保证 complete/abort 会正确落盘/清目录，**没有做过期分片的巡检清理** ——
-- 客户端开了分片就断线不调 abort 时，分片会一直占着盘，需要靠运维定期清 root/.zmultipart。
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
    128
) NOT NULL COMMENT '分片上传会话ID',
    `bucket_id`
    BIGINT
    NOT NULL
    COMMENT
        '桶ID',
    `object_key`
    VARCHAR
(
    512
) NOT NULL COMMENT '归并后的对象键',
    `user_id` BIGINT NOT NULL COMMENT '发起者ID',
    `part_size` BIGINT COMMENT '分片大小（字节）',
    `total_parts` INT COMMENT '总分片数',
    `uploaded_parts` INT NOT NULL DEFAULT 0 COMMENT '已上传分片数',
    `status`
    TINYINT NOT NULL
    DEFAULT 0 COMMENT '状态 0进行中 1已完成 2已放弃',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_upload_id`
(
    `upload_id`
),
    KEY `idx_expire_status`
(
    `status`,
    `expire_time`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传会话表';

-- 分片表。归并前记下每片的 etag 与大小，客户端少传或重传都能对出来。
CREATE TABLE IF NOT EXISTS `oss_part`
(
    `id`
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
        '主键',
    `multipart_id`
    BIGINT
    NOT NULL
    COMMENT
        '分片会话ID',
    `part_number`
    INT NOT NULL COMMENT '分片序号，从1开始',
    `etag`
    VARCHAR
(
    128
) COMMENT '分片校验和',
    `size` BIGINT NOT NULL DEFAULT 0 COMMENT '分片大小',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_multipart_part`
(
    `multipart_id`,
    `part_number`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片表';

-- 初始化测试用户
INSERT INTO `oss_user` (`username`, `access_key`, `secret_key`)
VALUES ('admin', 'zossadmin', 'zossadmin123456');