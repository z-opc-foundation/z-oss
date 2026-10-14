package com.zifang.z.oss.core.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.oss.core.domain.entity.OssBucket;
import org.apache.ibatis.annotations.Delete;

/**
 * 桶Mapper（由 z-oss-api 的 OssModuleDataSource @MapperScan 统一注册，
 * 不要再加 @Mapper 注解，否则会按 type 注入 SqlSessionFactory 导致多 bean 冲突）
 */
public interface OssBucketMapper extends BaseMapper<OssBucket> {

    /**
     * 物理删除桶行，绕过逻辑删除。
     * <p>
     * 桶的删除本来就是"物理目录也一起删掉"，留一行 deleted=1 的记录没有价值，
     * 反而把唯一键 (name,user_id) 堵死：同名桶删掉之后就再也建不回来。
     * （唯一键若改成带 deleted 也不成立 —— deleted 只有 0/1，删第二轮又撞车。）
     */
    @Delete("DELETE FROM oss_bucket WHERE id = #{id}")
    int hardDeleteById(Long id);
}