package com.zifang.z.oss.core.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.oss.core.domain.entity.OssUser;

/**
 * 用户Mapper（由 z-oss-api 的 OssModuleDataSource @MapperScan 统一注册，
 * 不要再加 @Mapper 注解，否则会按 type 注入 SqlSessionFactory 导致多 bean 冲突）
 */
public interface OssUserMapper extends BaseMapper<OssUser> {
}