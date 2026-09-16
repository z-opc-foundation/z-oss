package com.zifang.z.oss.api.config;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zifang.z.boot.datasource.starter.ModuleDataSourceTemplate;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * z-oss 数据源 + SqlSessionFactory 配置
 * <p>
 * 对齐 z-ctc DataSourceConfigForCtc：继承 z-boot 的 ModuleDataSourceTemplate，
 * 通过 z.base.db.oss.* 读取数据源。OssBucket/OssObject 等实体使用 @TableField(fill=...)，
 * 因此需要把 OssMybatisPlusConfig（MetaObjectHandler）显式注入到 SqlSessionFactory 的 GlobalConfig，
 * 这里 override 父类的 buildSqlSessionFactory 来补齐这一步。
 */
@Configuration
@MapperScan(basePackages = "com.zifang.z.oss.core.domain.mapper", sqlSessionFactoryRef = "sqlSessionFactoryOss")
public class OssModuleDataSource extends ModuleDataSourceTemplate {

    @Bean("dataSourceOss")
    public DataSource dataSource(Environment env) {
        return buildDataSource(env, "oss");
    }

    @Bean("sqlSessionFactoryOss")
    public SqlSessionFactory sqlSessionFactoryOss(
            DataSource dataSourceOss,
            ObjectProvider<MetaObjectHandler> metaObjectHandlers) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSourceOss);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml"));
        factoryBean.setTypeAliasesPackage("com.zifang.z.oss.core.domain.entity");

        // 注入 MetaObjectHandler（@TableField(fill=...) 依赖）
        MetaObjectHandler handler = metaObjectHandlers.getIfAvailable();
        if (handler != null) {
            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setMetaObjectHandler(handler);
            factoryBean.setGlobalConfig(globalConfig);
        }

        return factoryBean.getObject();
    }
}
