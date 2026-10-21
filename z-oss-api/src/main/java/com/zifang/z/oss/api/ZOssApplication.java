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
 *
 * <p>⚠ 2026-10-22 (TASK-20261022-011 第 7 项)：**本仓的配置不再放 classpath 根的
 * {@code application.yml}**，改名为 {@code z-oss-standalone.yml}，由本类用
 * {@code spring.config.additional-location} **显式**加载。
 *
 * <p><b>为什么必须这么改</b>：{@code z-oss-api} 既是可执行应用、又被别的仓当**库件**引用
 * （主壳 {@code z-opc-main} 就依赖它）。Spring Boot 的默认配置位置里有
 * {@code classpath:/application.yml} —— 这个位置在 classpath 上**只解析到第一个命中**，
 * 于是库件 jar 里的根级 {@code application.yml} 会**顶掉宿主的**（本次事故里它把
 * {@code spring.datasource.url} 回落成 {@code localhost} ⇒ 主壳启动期查库拿不到连接 ⇒
 * Druid {@code max-wait=-1} 无限等待 ⇒ 启动挂死，详见
 * {@code 006_部署方案/010_发版收尾清单.md} §3.8）。
 *
 * <p>改名的两个前提：
 * <ol>
 *   <li>文件**不叫** {@code application*} ⇒ Spring Boot 默认位置（{@code classpath:/}、
 *       {@code classpath:/config/}、{@code file:./}、{@code file:./config/}）**都不会**命中它
 *       ⇒ 宿主（谁引用本库都算）**不再被劫持**；</li>
 *   <li>本应用自己**显式**声明它 ⇒ 独立部署（{@code java -jar app.jar}）行为不变。</li>
 * </ol>
 *
 * <p>约束（构建前置检查 {@code _doc/003_script/precheck.sh} 会机械校验）：
 * <b>z-oss-api 的 jar 内不允许再出现根级 application.yml / application.properties /
 * bootstrap.yml</b>。
 */
@SpringBootApplication
@MapperScan("com.zifang.z.oss.core.domain.mapper")
@ComponentScan(basePackages = {"com.zifang.z.oss"})
@Import(OssCoreAutoConfiguration.class)
public class ZOssApplication {

    /** 本仓自己的配置文件（**不叫** application* ⇒ 宿主不会被劫持）。 */
    private static final String OWN_CONFIG_RESOURCE = "classpath:/z-oss-standalone.yml";

    public static void main(String[] args) {
        registerOwnConfigLocation();
        try {
            ApplicationContext context = SpringApplication.run(ZOssApplication.class, args);
            System.out.println("=== Z-OSS 启动成功 ===");
            System.out.println("总共 " + context.getBeanDefinitionCount() + " 个Bean");
        } catch (Exception e) {
            // ⚠ 2026-10-22 (TASK-20261022-011 第 7 项)：**失败必须显式**。
            //   原来这里把异常吞掉、只 println 然后**以退出码 0 结束** ⇒ 容器侧看到的是
            //   「Completed」而不是「CrashLoopBackOff」，**启动失败这件事在编排层是看不见的**。
            //   ⇒ 现在：打印明确失败信息 + **非 0 退出码**（让编排层立刻看见失败并重启/报警）。
            System.err.println("=== 应用启动失败 ===");
            System.err.println("原因：" + e.getClass().getName() + ": " + e.getMessage());
            System.err.println("提示：若缺数据库地址/用户名/口令，请显式提供 "
                    + "SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD"
                    + "（本仓不再在配置里写死库地址，见 z-oss-standalone.yml）");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 把本仓自己的配置文件登记成**附加**配置位置。
     *
     * <p>用「附加」而不是「替换」：宿主/运维如果通过 {@code SPRING_CONFIG_ADDITIONAL_LOCATION}
     * 或命令行另外指了配置，**不被这里覆盖**（追加在已有值之后）。
     *
     * <p>必须在 {@link SpringApplication#run} **之前**设置：配置数据加载发生在
     * {@code run()} 内部，system property 是那时环境里优先级最高的来源之一。
     */
    private static void registerOwnConfigLocation() {
        String key = "spring.config.additional-location";
        String existing = System.getProperty(key);
        if (existing == null || existing.trim().isEmpty()) {
            System.setProperty(key, OWN_CONFIG_RESOURCE);
        } else if (!existing.contains(OWN_CONFIG_RESOURCE)) {
            System.setProperty(key, existing + "," + OWN_CONFIG_RESOURCE);
        }
    }
}
