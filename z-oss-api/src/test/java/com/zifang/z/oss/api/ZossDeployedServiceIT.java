package com.zifang.z.oss.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打真实部署实例的集成测试 —— 目标是家庭 k3s 里由 z-ops 部署的那台 z-oss，
 * 不是本地起进程。所以端点、鉴权头、存储落盘全走真链路，PVC/多副本这些问题只有这一层能兜住。
 *
 * <p>AK/SK 只从环境变量读，不给默认值 —— 凭据不进版本库。
 * 前置：{@code ZOSS_BASE_URL=http://192.168.31.231:30910 ZOSS_ACCESS_KEY=... ZOSS_SECRET_KEY=... mvn verify}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "ZOSS_BASE_URL", matches = ".+")
class ZossDeployedServiceIT {

    private static final String BUCKET = "it-deployed";

    private static String baseUrl;
    private static String accessKey;
    private static String secretKey;
    private static final RestTemplate REST = new RestTemplate();

    @BeforeAll
    static void loadConfig() {
        baseUrl = System.getenv("ZOSS_BASE_URL");
        accessKey = System.getenv("ZOSS_ACCESS_KEY");
        secretKey = System.getenv("ZOSS_SECRET_KEY");
        REST.setRequestFactory(new SimpleClientHttpRequestFactoryNoRedirect());
    }

    @Test
    @Order(1)
    void healthEndpointIsUp() {
        ResponseEntity<String> r = REST.getForEntity(baseUrl + "/actuator/health", String.class);
        assertEquals(200, r.getStatusCodeValue());
        assertTrue(r.getBody().contains("UP"), r.getBody());
    }

    @Test
    @Order(2)
    void requestsWithoutCredentialsAreRejected() {
        try {
            REST.getForEntity(baseUrl + "/api/v1/bucket", String.class);
            org.junit.jupiter.api.Assertions.fail("无凭据竟然通过了鉴权");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError());
        }
    }

    @Test
    @Order(3)
    void requestsWithWrongCredentialsAreRejected() {
        HttpHeaders h = authHeaders("nobody", "wrong-secret");
        try {
            REST.exchange(baseUrl + "/api/v1/bucket", HttpMethod.GET, new HttpEntity<>(h), String.class);
            org.junit.jupiter.api.Assertions.fail("错凭据竟然通过了鉴权");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError());
        }
    }

    @Test
    @Order(4)
    void bucketLifecycleWorks() {
        deleteBucketQuietly();
        // 上一轮跑失败可能留下非空桶，provider 拒删非空桶；先清对象再删桶
        purgeBucket();

        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String created = REST.exchange(baseUrl + "/api/v1/bucket", HttpMethod.POST,
                new HttpEntity<String>("{\"name\":\"" + BUCKET + "\",\"acl\":\"private\"}", h), String.class).getBody();
        assertTrue(created.contains(BUCKET), created);

        String list = REST.exchange(baseUrl + "/api/v1/bucket", HttpMethod.GET,
                new HttpEntity<Void>(authHeaders()), String.class).getBody();
        assertTrue(list.contains(BUCKET), list);
    }

    @Test
    @Order(5)
    void putGetRoundTripsThroughTheCluster() {
        byte[] payload = new byte[64 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        put("roundtrip.bin", payload);

        ResponseEntity<byte[]> got = REST.exchange(
                baseUrl + "/api/v1/object?bucketName=" + BUCKET + "&objectKey=roundtrip.bin",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), byte[].class);
        assertEquals(200, got.getStatusCodeValue());
        assertArrayEquals(payload, got.getBody());
    }

    @Test
    @Order(6)
    void rangeRequestReturnsExactlyThatWindow() {
        byte[] payload = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8);
        put("range.txt", payload);

        HttpHeaders h = authHeaders();
        h.set("Range", "bytes=5-9");
        ResponseEntity<byte[]> got = REST.exchange(
                baseUrl + "/api/v1/object?bucketName=" + BUCKET + "&objectKey=range.txt",
                HttpMethod.GET, new HttpEntity<>(h), byte[].class);

        assertEquals(206, got.getStatusCodeValue());
        assertEquals("bytes 5-9/20", got.getHeaders().getFirst("Content-Range"));
        assertEquals("56789", new String(got.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @Order(7)
    void listReturnsTheObjectsWeActuallyPut() {
        put("a/1.txt", "one".getBytes(StandardCharsets.UTF_8));
        put("a/2.txt", "two".getBytes(StandardCharsets.UTF_8));
        put("b/3.txt", "three".getBytes(StandardCharsets.UTF_8));

        String list = REST.exchange(
                baseUrl + "/api/v1/object/list?bucketName=" + BUCKET + "&prefix=a/",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class).getBody();

        assertTrue(list.contains("a/1.txt"), list);
        assertTrue(list.contains("a/2.txt"), list);
        assertFalse(list.contains("b/3.txt"), "prefix=a/ 不该带出 b/ 下的对象: " + list);
    }

    @Test
    @Order(8)
    void reuploadAndRecopyOverwriteInsteadOfDuplicating() {
        // 同一 key 反复 PUT / COPY：元数据表必须始终只有一行，
        // 否则后续 getObject 的 getOne() 会抛 "found: N"。第一次做看不出问题，
        // 第二次做才炸 —— 所以这里必须真的做两遍。
        put("overwrite.txt", "v1".getBytes(StandardCharsets.UTF_8));
        put("overwrite.txt", "v2".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<byte[]> afterPut = REST.exchange(
                baseUrl + "/api/v1/object?bucketName=" + BUCKET + "&objectKey=overwrite.txt",
                HttpMethod.GET, new HttpEntity<Void>(authHeaders()), byte[].class);
        assertEquals(200, afterPut.getStatusCodeValue());
        assertEquals("v2", new String(afterPut.getBody(), StandardCharsets.UTF_8));

        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String copyBody = "{\"destBucketName\":\"" + BUCKET + "\",\"destObjectKey\":\"copy-twice.txt\"}";
        String url = baseUrl + "/api/v1/object/copy?bucketName=" + BUCKET + "&objectKey=overwrite.txt";
        REST.exchange(url, HttpMethod.POST, new HttpEntity<String>(copyBody, h), String.class);
        REST.exchange(url, HttpMethod.POST, new HttpEntity<String>(copyBody, h), String.class);

        String list = REST.exchange(
                baseUrl + "/api/v1/object/list?bucketName=" + BUCKET + "&prefix=copy-twice.txt",
                HttpMethod.GET, new HttpEntity<Void>(authHeaders()), String.class).getBody();
        assertEquals(1, countOccurrences(list, "\"key\":\""),
                "两次 COPY 之后应该只有一条元数据: " + list);
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    @Test
    @Order(9)
    void copyThenBatchDeleteLeavesOnlyTheSurvivor() {
        put("copy-src.txt", "copy-me".getBytes(StandardCharsets.UTF_8));

        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String copied = REST.exchange(
                baseUrl + "/api/v1/object/copy?bucketName=" + BUCKET + "&objectKey=copy-src.txt",
                HttpMethod.POST,
                new HttpEntity<>("{\"destBucketName\":\"" + BUCKET + "\",\"destObjectKey\":\"copy-dst.txt\"}", h),
                String.class).getBody();
        assertTrue(copied.contains("copy-dst.txt"), copied);

        String deleted = REST.exchange(
                baseUrl + "/api/v1/object/batch-delete?bucketName=" + BUCKET,
                HttpMethod.POST,
                new HttpEntity<>("[\"copy-src.txt\",\"copy-dst.txt\",\"never-existed.txt\"]", h),
                String.class).getBody();
        assertFalse(deleted.contains("never-existed.txt"),
                "删不存在的 key 不该被当成删成功返回: " + deleted);
    }

    @Test
    @Order(10)
    void presignedUrlIsRetrievableWithoutCredentials() {
        put("presigned.txt", "signed-content".getBytes(StandardCharsets.UTF_8));

        String url = REST.exchange(
                baseUrl + "/api/v1/object/url?bucketName=" + BUCKET + "&objectKey=presigned.txt&expires=600",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class).getBody();

        int i = url.indexOf("http");
        int j = url.indexOf('"', i);
        String signed = url.substring(i, j).replace("\\/", "/");

        ResponseEntity<byte[]> got = REST.getForEntity(signed, byte[].class);
        assertEquals(200, got.getStatusCodeValue());
        assertEquals("signed-content", new String(got.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @Order(11)
    void multipartUploadAssemblesIntoOneObject() {
        byte[] part1 = new byte[5 * 1024 * 1024];
        Arrays.fill(part1, (byte) 'A');
        byte[] part2 = new byte[1024 * 1024];
        Arrays.fill(part2, (byte) 'B');

        HttpHeaders h = authHeaders();
        Map<?, ?> initResp = REST.exchange(
                baseUrl + "/api/v1/object/multipart/init?bucketName=" + BUCKET + "&objectKey=big.bin",
                HttpMethod.POST, new HttpEntity<Void>(h), Map.class).getBody();
        Object initData = initResp.get("data");
        assertNotNull(initData, String.valueOf(initResp));
        String uploadId = String.valueOf(((Map<?, ?>) initData).get("uploadId"));
        assertNotNull(uploadId);

        String etag1 = uploadPart("big.bin", uploadId, 1, part1);
        String etag2 = uploadPart("big.bin", uploadId, 2, part2);

        h.setContentType(MediaType.APPLICATION_JSON);
        String completed = REST.exchange(
                baseUrl + "/api/v1/object/multipart/complete?bucketName=" + BUCKET
                        + "&objectKey=big.bin&uploadId=" + uploadId,
                HttpMethod.POST,
                new HttpEntity<>("[{\"partNumber\":1,\"eTag\":\"" + etag1 + "\"},"
                        + "{\"partNumber\":2,\"eTag\":\"" + etag2 + "\"}]", h),
                String.class).getBody();
        assertTrue(completed.contains("big.bin"), completed);

        ResponseEntity<byte[]> got = REST.exchange(
                baseUrl + "/api/v1/object?bucketName=" + BUCKET + "&objectKey=big.bin",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), byte[].class);
        assertEquals(200, got.getStatusCodeValue());
        assertEquals(part1.length + part2.length, got.getBody().length);
        assertEquals((byte) 'A', got.getBody()[0]);
        assertEquals((byte) 'B', got.getBody()[part1.length]);
    }

    @Test
    @Order(12)
    void cleanup() {
        purgeBucket();
    }

    private void deleteBucketQuietly() {
        try {
            REST.exchange(baseUrl + "/api/v1/bucket?bucketName=" + BUCKET, HttpMethod.DELETE,
                    new HttpEntity<Void>(authHeaders()), String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // bucket 还没建 / 已经删过，服务端回 400，属于预期的前置清理
        }
    }

    /**
     * 把桶连同里面所有对象清掉。上一轮跑失败留下的残骸会让下一轮直接
     * "Bucket already exists" 崩掉，而红着的用例比不跑更难查。
     * <p>
     * 逐个删而不是一把 batch-delete：批量接口里只要有一个 key 删不掉，
     * 整个请求就中断在那一半，剩下的对象留在桶里，桶就永远删不掉。
     */
    private void purgeBucket() {
        for (int round = 0; round < 10; round++) {
            List<String> keys = listKeys();
            if (keys.isEmpty()) {
                break;
            }
            HttpHeaders h = authHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            for (String key : keys) {
                try {
                    // key 交给 RestTemplate 自己编码。这里若先 URLEncoder.encode，
                    // '%' 会被再编成 '%25'，服务端解一次拿到的是字面量 "a%2F1.txt"，
                    // 删不掉任何东西 —— 表现是删除静默失败、桶永远删不掉。
                    REST.exchange(baseUrl + "/api/v1/object?bucketName={b}&objectKey={k}",
                            HttpMethod.DELETE,
                            new HttpEntity<Void>(h),
                            String.class,
                            BUCKET, key);
                } catch (org.springframework.web.client.HttpStatusCodeException e) {
                    // 单个删不掉就留给下一轮；连续几轮都清不动说明不是残留问题
                }
            }
        }
        deleteBucketQuietly();
    }

    
    private List<String> listKeys() {
        String list;
        try {
            list = REST.exchange(baseUrl + "/api/v1/object/list?bucketName=" + BUCKET,
                    HttpMethod.GET, new HttpEntity<Void>(authHeaders()), String.class).getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return Collections.emptyList();
        }
        List<String> keys = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"key\"\\s*:\\s*\"([^\"]+)\"").matcher(list);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private String uploadPart(String objectKey, String uploadId, int partNumber, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("bucketName", BUCKET);
        body.add("objectKey", objectKey);
        body.add("uploadId", uploadId);
        body.add("partNumber", String.valueOf(partNumber));
        ByteArrayResource res = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "part";
            }
        };
        body.add("file", res);

        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        Map<?, ?> r = REST.exchange(baseUrl + "/api/v1/object/multipart/part", HttpMethod.POST,
                new HttpEntity<>(body, h), Map.class).getBody();
        // 响应外层是 Result，真正的 part 信息在 data 里
        Object data = r.get("data");
        assertNotNull(data, String.valueOf(r));
        Object etag = ((Map<?, ?>) data).get("etag");
        assertNotNull(etag, String.valueOf(data));
        return String.valueOf(etag);
    }

    private void put(String key, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("bucketName", BUCKET);
        body.add("objectKey", key);
        ByteArrayResource res = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return key;
            }
        };
        body.add("file", res);
        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        REST.exchange(baseUrl + "/api/v1/object", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private HttpHeaders authHeaders() {
        return authHeaders(accessKey, secretKey);
    }

    private static HttpHeaders authHeaders(String ak, String sk) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Zoss-Access-Key", ak);
        h.set("X-Zoss-Secret-Key", sk);
        return h;
    }

    /**
     * 预签名 URL 验证要能看见 302/403 而不是被 RestTemplate 悄悄跟过去，
     * 所以关掉自动重定向。
     */
    private static class SimpleClientHttpRequestFactoryNoRedirect
            extends org.springframework.http.client.SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                throws java.io.IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}