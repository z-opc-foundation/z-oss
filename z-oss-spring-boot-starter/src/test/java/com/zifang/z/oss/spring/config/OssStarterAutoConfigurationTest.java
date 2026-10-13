package com.zifang.z.oss.spring.config;

import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssPartInfo;
import com.zifang.z.oss.spring.template.OssTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * starter 装配验证。
 *
 * <p>钉的是"消费方只依赖一个 starter 就能拿到 OssTemplate 与可用后端"这条契约，
 * 以及 {@code oss.provider} 的切换真的换了后端而不是只换了个字符串。</p>
 */
class OssStarterAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssStarterAutoConfiguration.class));

    private static InputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void templateIsWiredAndBackendWorks(@TempDir java.nio.file.Path tmp) {
        runner.withPropertyValues(
                "oss.storage.file.root=" + tmp.resolve("root").toString()
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(OssTemplate.class);

            OssTemplate oss = ctx.getBean(OssTemplate.class);
            assertThat(oss.activeProvider()).isEqualTo("local");
            assertThat(oss.providerTypes()).contains("local");
        });
    }

    @Test
    void ossEnabledFalseTurnsEverythingOff() {
        runner.withPropertyValues("oss.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(OssTemplate.class);
        });
    }

    @Test
    void selectingS3WithoutEndpointFailsAtStartup(@TempDir java.nio.file.Path tmp) {
        // 选了 s3 却是空 endpoint：必须在启动时就报出缺哪个键，而不是等第一次调用
        runner.withPropertyValues(
                "oss.storage.file.root=" + tmp.resolve("root").toString(),
                "oss.provider=s3"
        ).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("oss.s3.endpoint");
        });
    }

    @Test
    void unknownProviderTypeFailsLoudlyAtFirstUse(@TempDir java.nio.file.Path tmp) {
        // 不存在的后端名没有 bean 可建，只能在用起来时指出"注册了哪些"
        runner.withPropertyValues(
                "oss.storage.file.root=" + tmp.resolve("root").toString(),
                "oss.provider=does-not-exist"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            OssTemplate oss = ctx.getBean(OssTemplate.class);
            assertThatThrownBy(() -> oss.listBuckets())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does-not-exist");
        });
    }

    @Test
    void templateDelegatesMultipartToActiveProvider(@TempDir java.nio.file.Path tmp) throws Exception {
        runner.withPropertyValues(
                "oss.storage.file.root=" + tmp.resolve("root").toString()
        ).run(ctx -> {
            OssTemplate oss = ctx.getBean(OssTemplate.class);

            String uploadId = oss.initiateMultipart("b", "multi.txt", "text/plain");
            OssPartInfo p1 = oss.uploadPart("b", "multi.txt", uploadId, 1, in("hello "), 6, null);
            OssPartInfo p2 = oss.uploadPart("b", "multi.txt", uploadId, 2, in("oss"), 3, null);

            assertThat(p1.getEtag()).isNotBlank();
            assertThat(p2.getSize()).isEqualTo(3L);

            OssObjectSummary summary = oss.completeMultipart("b", "multi.txt", uploadId,
                    Arrays.asList(p2, p1));

            assertThat(summary.getSize()).isEqualTo(9L);
            try (InputStream got = oss.getObject("b", "multi.txt")) {
                assertThat(read(got)).isEqualTo("hello oss");
            }
        });
    }

    @Test
    void templateExposesRangeAndPresign(@TempDir java.nio.file.Path tmp) throws Exception {
        runner.withPropertyValues(
                "oss.storage.file.root=" + tmp.resolve("root").toString(),
                "oss.local.access-key=starter-test-key",
                "oss.local.public-base-url=https://oss.example.com"
        ).run(ctx -> {
            OssTemplate oss = ctx.getBean(OssTemplate.class);
            oss.putObject("b", "r.txt", in("0123456789"), 10, "text/plain");

            try (InputStream got = oss.getObject("b", "r.txt", 4, 3)) {
                assertThat(read(got)).isEqualTo("456");
            }

            String url = oss.presignedUrl("b", "r.txt", 600);
            assertThat(url)
                    .startsWith("https://oss.example.com/api/v1/object/download")
                    .contains("accessKeyId=starter-test-key");
        });
    }

    @Test
    void templateListsBuckets(@TempDir java.nio.file.Path tmp) {
        runner.withPropertyValues(
                "oss.storage.file.root=" + tmp.resolve("root").toString()
        ).run(ctx -> {
            OssTemplate oss = ctx.getBean(OssTemplate.class);
            oss.createBucket("alpha");
            oss.createBucket("beta");

            List<String> buckets = oss.listBuckets();
            assertThat(buckets).contains("alpha", "beta");
            assertThat(oss.bucketExists("alpha")).isTrue();
        });
    }
}
