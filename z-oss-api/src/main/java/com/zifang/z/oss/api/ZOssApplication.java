package com.zifang.z.oss.api;

import com.zifang.z.oss.core.OssCoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * OSS应用启动类
 */
@SpringBootApplication
@MapperScan("com.zifang.z.oss.core.domain.mapper")
@ComponentScan(basePackages = {"com.zifang.z.oss"})
@Import(OssCoreAutoConfiguration.class)
public class ZOssApplication {

    public static void main(String[] args) {
        try {
            ApplicationContext context = SpringApplication.run(ZOssApplication.class, args);
            System.out.println("=== Z-OSS 启动成功 ===");
            System.out.println("总共 " + context.getBeanDefinitionCount() + " 个Bean");
        } catch (Exception e) {
            System.out.println("=== 应用启动失败 ===");
            e.printStackTrace();
        }
    }
}