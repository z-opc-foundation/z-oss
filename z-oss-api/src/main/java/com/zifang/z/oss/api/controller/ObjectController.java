package com.zifang.z.oss.api.controller;

import com.zifang.util.core.meta.Result;
import com.zifang.util.core.meta.ResultCode;
import com.zifang.z.oss.api.config.AuthenticationInterceptor;
import com.zifang.z.oss.api.vo.BucketStatsVO;
import com.zifang.z.oss.api.vo.ObjectMetadataVO;
import com.zifang.z.oss.api.vo.ObjectVO;
import com.zifang.z.oss.api.vo.PresignedUrlVO;
import com.zifang.z.oss.core.domain.entity.OssObject;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
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
     */
    @GetMapping("/object")
    public Result<?> downloadObject(@RequestParam String bucketName,
                                    @RequestParam String objectKey,
                                    HttpServletRequest httpRequest) throws IOException {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);

        InputStream inputStream = objectService.downloadObject(bucketName, objectKey, user.getId());
        OssObject objectMeta = objectService.getObject(bucketName, objectKey, user.getId());

        byte[] data = toByteArray(inputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(objectMeta.getContentType()));
        headers.setContentLength(objectMeta.getContentLength());
        headers.setETag("\"" + objectMeta.getEtag() + "\"");

        return Result.success(data);
    }

    /**
     * 将输入流中的全部内容读取为字节数组.
     *
     * @param input 输入流
     * @return 读取得到的字节数组
     * @throws IOException 输入流读取异常
     */
    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024 * 8];
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
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
     * 查询指定存储桶的统计信息(对象总数、占用空间、最近修改时间).
     */
    @GetMapping("/bucket/stats")
    public Result<BucketStatsVO> getBucketStats(@RequestParam String bucketName,
                                                HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        Map<String, Object> stats = objectService.getBucketStats(bucketName, user.getId());
        BucketStatsVO vo = new BucketStatsVO();
        vo.setTotalObjects(((Number) stats.get("totalObjects")).longValue());
        vo.setTotalSize(((Number) stats.get("totalSize")).longValue());
        Object lastModified = stats.get("lastModified");
        if (lastModified instanceof LocalDateTime) {
            vo.setLastModified((LocalDateTime) lastModified);
        }
        return Result.success(vo);
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