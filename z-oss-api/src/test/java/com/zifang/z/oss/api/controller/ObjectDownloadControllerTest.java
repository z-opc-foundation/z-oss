package com.zifang.z.oss.api.controller;

import com.zifang.z.oss.api.config.AuthenticationInterceptor;
import com.zifang.z.oss.common.exception.ObjectException;
import com.zifang.z.oss.core.domain.entity.OssObject;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssObjectService;
import com.zifang.z.oss.core.util.ZOssSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 下载端点的字节语义验证。
 *
 * <p><b>钉的是那个已修的缺陷</b>：{@code GET /api/v1/object} 原本返回
 * {@code Result.success(byte[])}，字节被统一响应包装后交给 Jackson 序列化成 JSON，
 * 客户端拿到的是 base64/数组而不是文件；Content-Type / Content-Length / ETag
 * 被 set 在一个随后丢弃的 {@code HttpHeaders} 上，等于没设。这里断言响应体
 * 就是文件原始字节、响应头真的落在 HTTP 上。</p>
 */
@WebMvcTest(ObjectController.class)
class ObjectDownloadControllerTest {

    private static final byte[] CONTENT =
            "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String SIGN_KEY = "api-test-sign-key";

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private IOssObjectService objectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private OssObject meta(String key) {
        OssObject o = new OssObject();
        o.setBucketName("bucket");
        o.setObjectKey(key);
        o.setContentLength((long) CONTENT.length);
        o.setContentType("text/plain");
        o.setEtag("deadbeef");
        return o;
    }

    private void stubStream() throws IOException {
        when(objectService.getObject(eq("bucket"), eq("f.txt"), any()))
                .thenReturn(meta("f.txt"));
        when(objectService.getObjectByKey(eq("bucket"), eq("f.txt")))
                .thenReturn(meta("f.txt"));
        // range 通道：offset<=0 传 -1 表示整块
        when(objectService.openObjectStream(eq("bucket"), eq("f.txt"), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    long offset = inv.getArgument(2);
                    long length = inv.getArgument(3);
                    if (offset <= 0) {
                        return new ByteArrayInputStream(CONTENT);
                    }
                    int from = (int) offset;
                    int to = length >= 0 ? (int) Math.min(CONTENT.length, offset + length) : CONTENT.length;
                    return new ByteArrayInputStream(CONTENT, from, to - from);
                });
    }

    /** 直接调 controller，绕开鉴权拦截器 —— 这里验的是字节语义，不是鉴权。 */
    private org.springframework.mock.web.MockHttpServletResponse invokeDownload(String rangeHeader)
            throws Exception {
        ObjectController controller = context.getBean(ObjectController.class);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/object");
        OssUser user = new OssUser();
        user.setId(1L);
        req.setAttribute(AuthenticationInterceptor.ATTR_USER, user);
        if (rangeHeader != null) {
            req.addHeader("Range", rangeHeader);
        }
        return write(controller.downloadObject("bucket", "f.txt", req));
    }

    private static org.springframework.mock.web.MockHttpServletResponse write(
            org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> entity)
            throws Exception {
        org.springframework.mock.web.MockHttpServletResponse resp =
                new org.springframework.mock.web.MockHttpServletResponse();
        // 状态先设：MockHttpServletResponse 一旦开始写 body 就不再改状态
        resp.setStatus(entity.getStatusCodeValue());
        entity.getHeaders().forEach((k, v) -> v.forEach(val -> resp.addHeader(k, val)));
        if (entity.getBody() != null) {
            entity.getBody().writeTo(resp.getOutputStream());
        }
        resp.flushBuffer();
        return resp;
    }

    @Test
    void responseBodyIsRawBytesNotJson() throws Exception {
        stubStream();
        org.springframework.mock.web.MockHttpServletResponse r = invokeDownload(null);

        assertArrayEquals(CONTENT, r.getContentAsByteArray(),
                "响应体必须是文件原始字节，不能是 JSON 化的 byte[]");
    }

    @Test
    void contentHeadersActuallyReachTheWire() throws Exception {
        stubStream();
        org.springframework.mock.web.MockHttpServletResponse r = invokeDownload(null);

        assertEquals("text/plain", r.getContentType());
        assertEquals(CONTENT.length, r.getContentLength());
        assertEquals("\"deadbeef\"", r.getHeader("ETag"));
        assertEquals("bytes", r.getHeader("Accept-Ranges"),
                "不发 Accept-Ranges 客户端就不会尝试 Range，续传无从谈起");
    }

    @Test
    void rangeRequestReturnsPartialWindow() throws Exception {
        stubStream();
        org.springframework.mock.web.MockHttpServletResponse r = invokeDownload("bytes=4-7");

        assertEquals(206, r.getStatus());
        assertArrayEquals("4567".getBytes(StandardCharsets.UTF_8), r.getContentAsByteArray());
        assertEquals("bytes 4-7/16", r.getHeader("Content-Range"));
    }

    @Test
    void suffixRangeReturnsTail() throws Exception {
        stubStream();
        org.springframework.mock.web.MockHttpServletResponse r = invokeDownload("bytes=-4");

        assertArrayEquals("cdef".getBytes(StandardCharsets.UTF_8), r.getContentAsByteArray());
    }

    @Test
    void malformedRangeFallsBackToWholeObject() throws Exception {
        stubStream();
        org.springframework.mock.web.MockHttpServletResponse r = invokeDownload("bytes=not-a-number");

        assertArrayEquals(CONTENT, r.getContentAsByteArray(),
                "看不懂的 Range 应退化成整块响应，而不是 500");
    }

    // ==================== 预签名下载 ====================

    @Test
    void presignedDownloadAcceptsValidSignature() throws Exception {
        stubStream();
        ObjectController controller = context.getBean(ObjectController.class);
        long expires = System.currentTimeMillis() / 1000L + 300;
        String sig = ZOssSignature.sign(SIGN_KEY, "bucket", "f.txt", expires);

        org.springframework.mock.web.MockHttpServletResponse resp = write(
                controller.downloadObjectPresigned("bucket", "f.txt", expires, SIGN_KEY, sig,
                        new MockHttpServletRequest()));

        assertArrayEquals(CONTENT, resp.getContentAsByteArray());
    }

    @Test
    void presignedDownloadRejectsTamperedSignature() throws Exception {
        stubStream();
        ObjectController controller = context.getBean(ObjectController.class);
        long expires = System.currentTimeMillis() / 1000L + 300;
        String sig = ZOssSignature.sign(SIGN_KEY, "bucket", "f.txt", expires);

        try {
            controller.downloadObjectPresigned("bucket", "other.txt", expires, SIGN_KEY, sig,
                    new MockHttpServletRequest());
            org.junit.jupiter.api.Assertions.fail("签名与 key 不符时必须被拒");
        } catch (ObjectException expected) {
            assertEquals("预签名 URL 无效或已过期", expected.getMessage());
        }
    }

    @Test
    void presignedDownloadRejectsExpiredUrl() throws Exception {
        stubStream();
        ObjectController controller = context.getBean(ObjectController.class);
        long past = System.currentTimeMillis() / 1000L - 10;
        String sig = ZOssSignature.sign(SIGN_KEY, "bucket", "f.txt", past);

        try {
            controller.downloadObjectPresigned("bucket", "f.txt", past, SIGN_KEY, sig,
                    new MockHttpServletRequest());
            org.junit.jupiter.api.Assertions.fail("过期签名必须被拒");
        } catch (ObjectException expected) {
            assertEquals("预签名 URL 无效或已过期", expected.getMessage());
        }
    }
}
