package com.zifang.z.oss.api.controller;

import com.zifang.util.core.meta.Result;
import com.zifang.util.core.meta.ResultCode;
import com.zifang.z.oss.api.config.AuthenticationInterceptor;
import com.zifang.z.oss.api.vo.BucketStatsVO;
import com.zifang.z.oss.api.vo.ObjectMetadataVO;
import com.zifang.z.oss.api.vo.ObjectVO;
import com.zifang.z.oss.api.vo.PresignedUrlVO;
import com.zifang.z.oss.common.exception.ObjectException;
import com.zifang.z.oss.core.domain.entity.OssObject;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssObjectService;
import com.zifang.z.oss.core.provider.OssPartInfo;
import com.zifang.z.oss.core.util.ZOssSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对象存储(Object) API 控制器,提供对象上传、下载、删除、复制、批量删除、预签名 URL 以及桶统计等接口.
 * <p>
 * API 基础路径: /api/v1
 * 所属模块: z-oss-api
 * 鉴权: 由 AuthenticationInterceptor 在请求属性中注入当前 OssUser,所有接口均需登录
 *
 * <p>主要端点:
 * <ul>
 *   <li>POST /api/v1/object/{bucketName}/{objectKey} — 上传对象</li>
 *   <li>GET /api/v1/object/{bucketName}/{objectKey} — 下载对象</li>
 *   <li>DELETE /api/v1/object/{bucketName}/{objectKey} — 删除对象</li>
 *   <li>GET /api/v1/object/{bucketName} — 列出对象(支持 prefix 过滤)</li>
 *   <li>POST /api/v1/folder/{bucketName}/{folderKey} — 创建文件夹</li>
 *   <li>HEAD /api/v1/object/{bucketName}/{objectKey} — 查询对象元数据</li>
 *   <li>POST /api/v1/object/{bucketName}/{objectKey}/copy — 复制对象</li>
 *   <li>POST /api/v1/object/{bucketName}/batch-delete — 批量删除对象</li>
 *   <li>GET /api/v1/object/{bucketName}/{objectKey}/url — 生成预签名下载 URL</li>
 *   <li>GET /api/v1/bucket/{bucketName}/stats — 桶统计信息</li>
 * </ul>
 */

@RestController
@RequestMapping("/api/v1")
public class ObjectController {

    @Autowired
    private IOssObjectService objectService;

    // ==================== 对象操作 ====================

    /**
     * 上传一个对象到指定存储桶,内容为空时使用 application/octet-stream 作为默认 Content-Type.
     *
     * @param bucketName 目标存储桶名称
     * @param objectKey 对象 Key
     * @param file 上传的文件内容
     * @param httpRequest HTTP 请求,用于取出当前登录用户
     * @return 上传成功后的对象 VO
     * @throws IOException 文件读取异常
     */
    /**
     * RESTful alias: POST /api/v1/object?bucketName=&objectKey=
     * 上传一个对象到指定存储桶,内容为空时使用 application/octet-stream 作为默认 Content-Type.
     */
    @PostMapping("/object")
    public Result<?> uploadObject(@RequestParam String bucketName,
                                  @RequestParam String objectKey,
                                  @RequestParam("file") MultipartFile file,
                                  HttpServletRequest httpRequest) throws IOException {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);

        InputStream inputStream = file.getInputStream();
        String contentType = file.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        OssObject object = objectService.uploadObject(
                bucketName, objectKey, inputStream, file.getSize(), contentType, user.getId());

        return Result.success(convertObject(object));
    }

    /**
     * RESTful alias: GET /api/v1/object?bucketName=&objectKey=
     * 下载指定对象的二进制内容,并附带 Content-Type、Content-Length 与 ETag 等响应头信息.
     * <p>
     * 原来这里是 {@code Result.success(byte[])} —— 字节被塞进统一响应包装后交给
     * Jackson 序列化,客户端拿到的是一段 JSON 里的 base64/数字数组而不是文件本身,
     * 且响应头被 set 在一个随后就被丢弃的 {@code HttpHeaders} 上,等于没设。
     * 这里改成直接往 Servlet 输出流写,配 {@code StreamingResponseBody} 让容器在
     * 请求线程之外完成传输,大文件不占住业务线程。
     */
    @GetMapping("/object")
    public ResponseEntity<StreamingResponseBody> downloadObject(@RequestParam String bucketName,
                                                                 @RequestParam String objectKey,
                                                                 HttpServletRequest httpRequest)
            throws IOException {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        return streamObject(bucketName, objectKey, user.getId(),
                httpRequest.getHeader(HttpHeaders.RANGE), httpRequest);
    }

    /**
     * RESTful alias: GET /api/v1/object/download?bucketName=&objectKey=&expires=&accessKeyId=&signature=
     * 预签名下载入口 —— {@link com.zifang.z.oss.core.provider.impl.LocalOssProvider}
     * 签出来的 URL 指向这里。签名本身就是凭证,所以走的是签名校验而非 AK/SK 头。
     */
    @GetMapping("/object/download")
    public ResponseEntity<StreamingResponseBody> downloadObjectPresigned(@RequestParam String bucketName,
                                                                         @RequestParam String objectKey,
                                                                         @RequestParam long expires,
                                                                         @RequestParam("accessKeyId") String accessKeyId,
                                                                         @RequestParam String signature,
                                                                         HttpServletRequest httpRequest)
            throws IOException {
        if (!ZOssSignature.verify(accessKeyId, bucketName, objectKey, expires, signature)) {
            throw new ObjectException("预签名 URL 无效或已过期");
        }
        return streamObject(bucketName, objectKey, null, null, httpRequest);
    }

    /**
     * 把对象按字节直出,并支持 HTTP Range(断点续传与视频拖动进度条都靠它)。
     *
     * @param userId     为 null 表示走预签名通道,不做属主校验(签名已覆盖权限)
     * @param rangeHeader {@code Range: bytes=start-end},可空
     */
    private ResponseEntity<StreamingResponseBody> streamObject(String bucketName, String objectKey,
                                                               Long userId, String rangeHeader,
                                                               HttpServletRequest httpRequest)
            throws IOException {
        OssObject meta = userId == null
                ? objectService.getObjectByKey(bucketName, objectKey)
                : objectService.getObject(bucketName, objectKey, userId);
        if (meta == null) {
            throw new ObjectException("Object not found: " + objectKey);
        }

        long fullLength = meta.getContentLength() == null ? 0L : meta.getContentLength();
        long start = 0;
        long end = fullLength - 1;
        boolean partial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash > 0) {
                    start = Long.parseLong(spec.substring(0, dash).trim());
                    String tail = spec.substring(dash + 1).trim();
                    end = tail.isEmpty() ? fullLength - 1 : Long.parseLong(tail);
                } else if (dash == 0) {
                    // bytes=-N: 最后 N 字节
                    long suffix = Long.parseLong(spec.substring(1).trim());
                    start = Math.max(0, fullLength - suffix);
                } else {
                    start = Long.parseLong(spec.trim());
                }
            } catch (NumberFormatException e) {
                // 客户端发了看不懂的 Range,按整块响应而不是 500
                start = 0;
                end = fullLength - 1;
            }
            if (start >= fullLength && fullLength > 0) {
                HttpHeaders h = new HttpHeaders();
                h.add(HttpHeaders.ACCEPT_RANGES, "bytes");
                return new ResponseEntity<>(null, h, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            end = Math.min(end, fullLength - 1);
            partial = true;
        }

        long length = fullLength == 0 ? 0 : (end - start + 1);
        String contentType = meta.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : meta.getContentType();

        final long fStart = start;
        final long fLength = length;

        StreamingResponseBody body = out -> {
            InputStream in = objectService.openObjectStream(bucketName, objectKey,
                    fStart > 0 ? fStart : -1, fLength);
            try (InputStream stream = in) {
                byte[] buf = new byte[64 * 1024];
                long remaining = fLength;
                int read;
                while (remaining > 0 && (read = stream.read(buf, 0,
                        (int) Math.min(buf.length, remaining))) != -1) {
                    out.write(buf, 0, read);
                    remaining -= read;
                }
                out.flush();
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(length);
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (meta.getEtag() != null && !meta.getEtag().isEmpty()) {
            headers.setETag("\"" + meta.getEtag() + "\"");
        }
        String disposition = "inline; filename=\"" + objectKey + "\"";
        headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);

        if (partial) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + start + "-" + end + "/" + fullLength);
            return new ResponseEntity<>(body, headers, HttpStatus.PARTIAL_CONTENT);
        }
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * 删除指定存储桶中的某个对象.
     *
     * @param bucketName 存储桶名称
     * @param objectKey 对象 Key
     * @param httpRequest HTTP 请求,用于取出当前登录用户
     * @return 通用成功响应
     */
    /**
     * RESTful alias: DELETE /api/v1/object?bucketName=&objectKey=
     * 删除指定存储桶中的某个对象.
     */
    @DeleteMapping("/object")
    public Result<?> deleteObject(@RequestParam String bucketName,
                                  @RequestParam String objectKey,
                                  HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        objectService.deleteObject(bucketName, objectKey, user.getId());
        return Result.success();
    }

    /**
     * RESTful alias: GET /api/v1/object/list?bucketName=&prefix=
     * 列出指定存储桶下的对象,可选按前缀过滤.
     */
    @GetMapping("/object/list")
    public Result<List<ObjectVO>> listObjects(@RequestParam String bucketName,
                                              @RequestParam(value = "prefix", required = false) String prefix,
                                              HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        List<OssObject> objects = objectService.listObjects(bucketName, prefix, user.getId());
        return Result.success(objects.stream().map(this::convertObject).collect(Collectors.toList()));
    }

    /**
     * RESTful alias: POST /api/v1/folder?bucketName=&folderKey=
     * 在指定存储桶下创建一个空文件夹对象.
     */
    @PostMapping("/folder")
    public Result<?> createFolder(@RequestParam String bucketName,
                                  @RequestParam String folderKey,
                                  HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssObject object = objectService.createFolder(bucketName, folderKey, user.getId());
        return Result.success(convertObject(object));
    }

    // ==================== 扩展对象操作 ====================


    /**
     * 查询指定对象的元数据(HEAD),资源不存在时返回 404.
     *
     * @param bucketName 存储桶名称
     * @param objectKey 对象 Key
     * @param httpRequest HTTP 请求,用于取出当前登录用户
     * @return 对象元数据 VO 的封装结果
     */
    /**
     * RESTful alias: HEAD /api/v1/object/meta?bucketName=&objectKey=
     * 查询指定对象的元数据(HEAD),资源不存在时返回 404.
     */
    @RequestMapping(value = "/object/meta", method = RequestMethod.HEAD)
    public Result<ObjectMetadataVO> headObject(@RequestParam String bucketName,
                                               @RequestParam String objectKey,
                                               HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssObject object = objectService.getObject(bucketName, objectKey, user.getId());
        if (object == null) {
            return Result.<ObjectMetadataVO>fail("资源不存在").code(ResultCode.NOT_FOUND.getCode());
        }

        ObjectMetadataVO vo = new ObjectMetadataVO();
        vo.setKey(object.getObjectKey());
        vo.setSize(object.getContentLength());
        vo.setContentType(object.getContentType());
        vo.setEtag(object.getEtag());
        vo.setLastModified(object.getUpdateTime());

        return Result.success(vo);
    }


    /**
     * RESTful alias: POST /api/v1/object/copy?bucketName=&objectKey=
     * 将对象复制到另一个存储桶(或同一存储桶下的其他 Key).
     */
    @PostMapping("/object/copy")
    public Result<ObjectVO> copyObject(@RequestParam String bucketName,
                                       @RequestParam String objectKey,
                                       @RequestBody Map<String, String> copyRequest,
                                       HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        String destBucketName = copyRequest.get("destBucketName");
        String destObjectKey = copyRequest.get("destObjectKey");

        OssObject object = objectService.copyObject(bucketName, objectKey, destBucketName, destObjectKey, user.getId());
        return Result.success(convertObject(object));
    }


    /**
     * RESTful alias: POST /api/v1/object/batch-delete?bucketName=
     * 批量删除指定存储桶下的若干对象.
     */
    @PostMapping("/object/batch-delete")
    public Result<?> batchDeleteObjects(@RequestParam String bucketName,
                                        @RequestBody List<String> objectKeys,
                                        HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        objectService.batchDeleteObjects(bucketName, objectKeys, user.getId());
        return Result.success();
    }


    /**
     * RESTful alias: GET /api/v1/object/url?bucketName=&objectKey=&expires=
     * 生成对象的预签名下载 URL,默认有效期为 3600 秒.
     */
    @GetMapping("/object/url")
    public Result<PresignedUrlVO> getObjectUrl(@RequestParam String bucketName,
                                               @RequestParam String objectKey,
                                               @RequestParam(value = "expires", defaultValue = "3600") Integer expires,
                                               HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        String url = objectService.generatePresignedUrl(bucketName, objectKey, expires, user.getId());
        PresignedUrlVO vo = new PresignedUrlVO();
        vo.setUrl(url);
        vo.setExpiresAt(System.currentTimeMillis() + expires * 1000L);
        return Result.success(vo);
    }


    /**
     * RESTful alias: GET /api/v1/bucket/stats?bucketName=
     * 查询指定存储桶的统计信息(对象总数、占用空间、当前 provider).
     * <p>
     * bug-fix: key 必须跟 {@code OssObjectServiceImpl#getBucketStats} 放进 map 的名字一致 ——
     * 这里曾读 {@code "totalObjects"}(map 里只有 {@code "objectCount"})⇒ get 出 null ⇒ NPE ⇒ 500.
     */
    @GetMapping("/bucket/stats")
    public Result<BucketStatsVO> getBucketStats(@RequestParam String bucketName,
                                                HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        Map<String, Object> stats = objectService.getBucketStats(bucketName, user.getId());
        BucketStatsVO vo = new BucketStatsVO();
        vo.setObjectCount(((Number) stats.get("objectCount")).longValue());
        vo.setTotalSize(((Number) stats.get("totalSize")).longValue());
        vo.setActiveProvider((String) stats.get("activeProvider"));
        return Result.success(vo);
    }

    // ==================== 分片上传 ====================

    /**
     * 开启分片上传。大文件走这条路：单次请求只传一片,失败重传也只重传那一片。
     *
     * @return uploadId
     */
    @PostMapping("/object/multipart/init")
    public Result<Map<String, Object>> initiateMultipart(@RequestParam String bucketName,
                                                         @RequestParam String objectKey,
                                                         @RequestParam(value = "contentType", required = false) String contentType,
                                                         HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        String uploadId = objectService.initiateMultipart(bucketName, objectKey, contentType, user.getId());
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("uploadId", uploadId);
        data.put("bucketName", bucketName);
        data.put("objectKey", objectKey);
        return Result.success(data);
    }

    /**
     * 上传一个分片。partNumber 从 1 开始。
     *
     * @return 分片的 etag 与大小,归并时要靠它校验
     */
    @PostMapping("/object/multipart/part")
    public Result<OssPartInfo> uploadPart(@RequestParam String bucketName,
                                          @RequestParam String objectKey,
                                          @RequestParam String uploadId,
                                          @RequestParam int partNumber,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "contentType", required = false) String contentType,
                                          HttpServletRequest httpRequest) throws IOException {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        String ct = contentType != null ? contentType : file.getContentType();
        OssPartInfo part = objectService.uploadPart(bucketName, objectKey, uploadId, partNumber,
                file.getInputStream(), file.getSize(), ct, user.getId());
        return Result.success(part);
    }

    /**
     * 归并分片成最终对象。
     *
     * @param parts 形如 {@code [{"partNumber":1,"etag":"..."}, ...]},可乱序
     */
    @PostMapping("/object/multipart/complete")
    public Result<ObjectVO> completeMultipart(@RequestParam String bucketName,
                                               @RequestParam String objectKey,
                                               @RequestParam String uploadId,
                                               @RequestBody List<Map<String, Object>> parts,
                                               HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        List<OssPartInfo> infos = new java.util.ArrayList<>();
        if (parts != null) {
            for (Map<String, Object> p : parts) {
                Object num = p.get("partNumber");
                if (num == null) {
                    throw new ObjectException("parts[].partNumber 必填");
                }
                OssPartInfo info = new OssPartInfo();
                info.setPartNumber(((Number) num).intValue());
                Object etag = p.get("etag");
                info.setEtag(etag == null ? null : String.valueOf(etag));
                Object size = p.get("size");
                info.setSize(size == null ? 0L : ((Number) size).longValue());
                infos.add(info);
            }
        }
        OssObject object = objectService.completeMultipart(bucketName, objectKey, uploadId, infos, user.getId());
        return Result.success(convertObject(object));
    }

    /**
     * 放弃分片上传。客户端断线不调它是已上传分片占空间的常见泄漏。
     */
    @PostMapping("/object/multipart/abort")
    public Result<?> abortMultipart(@RequestParam String bucketName,
                                    @RequestParam String objectKey,
                                    @RequestParam String uploadId,
                                    HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        objectService.abortMultipart(bucketName, objectKey, uploadId, user.getId());
        return Result.success();
    }

    // ==================== 转换方法 ====================

    /**
     * 将对象存储实体转换为对外 VO.
     *
     * @param object 对象存储实体
     * @return 转换后的 VO 对象
     */
    private ObjectVO convertObject(OssObject object) {
        ObjectVO vo = new ObjectVO();
        vo.setKey(object.getObjectKey());
        vo.setName(object.getObjectName());
        vo.setSize(object.getContentLength());
        vo.setEtag(object.getEtag());
        vo.setContentType(object.getContentType());
        vo.setLastModified(object.getUpdateTime());
        vo.setFolder(object.getIsFolder() == 1);
        return vo;
    }
}