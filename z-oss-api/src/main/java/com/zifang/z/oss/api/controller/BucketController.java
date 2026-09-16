package com.zifang.z.oss.api.controller;

import com.zifang.util.core.meta.Result;
import com.zifang.z.oss.api.config.AuthenticationInterceptor;
import com.zifang.z.oss.api.dto.CreateBucketRequest;
import com.zifang.z.oss.api.dto.UpdateBucketRequest;
import com.zifang.z.oss.api.vo.BucketAclVO;
import com.zifang.z.oss.api.vo.BucketPolicyVO;
import com.zifang.z.oss.api.vo.BucketVO;
import com.zifang.z.oss.core.domain.entity.OssBucket;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssBucketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 存储桶(Bucket) API 控制器,提供 OSS 存储桶的 CRUD、ACL、Policy 等管理接口.
 * <p>
 * API 基础路径: /api/v1/bucket
 * 所属模块: z-oss-api
 * 鉴权: 由 AuthenticationInterceptor 在请求属性中注入当前 OssUser,所有接口均需登录
 *
 * <p>主要端点:
 * <ul>
 *   <li>POST /api/v1/bucket — 创建存储桶</li>
 *   <li>DELETE /api/v1/bucket/{bucketName} — 删除存储桶</li>
 *   <li>GET /api/v1/bucket — 列出当前用户全部存储桶</li>
 *   <li>GET /api/v1/bucket/{bucketName} — 获取存储桶详情</li>
 *   <li>PUT /api/v1/bucket/{bucketName} — 更新存储桶信息(ACL、region、policy)</li>
 *   <li>GET /api/v1/bucket/{bucketName}/acl — 查询存储桶 ACL</li>
 *   <li>PUT /api/v1/bucket/{bucketName}/acl — 设置存储桶 ACL</li>
 *   <li>GET /api/v1/bucket/{bucketName}/policy — 查询存储桶策略</li>
 *   <li>PUT /api/v1/bucket/{bucketName}/policy — 设置存储桶策略</li>
 *   <li>DELETE /api/v1/bucket/{bucketName}/policy — 删除存储桶策略</li>
 *   <li>HEAD /api/v1/bucket/{bucketName} — 检查存储桶是否存在</li>
 * </ul>
 */

@RestController
@RequestMapping("/api/v1/bucket")
public class BucketController {

    @Autowired
    private IOssBucketService bucketService;


    /**
     * 创建指定名称的存储桶,归属于当前登录用户.
     *
     * @param request     创建存储桶请求参数(含 bucket 名称)
     * @param httpRequest HTTP 请求,用于取出当前登录用户
     * @return 新建存储桶 VO 的封装结果
     */
    @PostMapping
    public Result<BucketVO> createBucket(@RequestBody CreateBucketRequest request,
                                         HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssBucket bucket = bucketService.createBucket(request.getName(), user.getId());
        return Result.success(convertBucket(bucket));
    }


    /**
     * 删除指定名称的存储桶,需校验归属权.
     *
     * @param bucketName 存储桶名称
     * @param httpRequest HTTP 请求,用于取出当前登录用户
     * @return 通用成功响应
     */
    /**
     * RESTful alias: DELETE /api/v1/bucket?bucketName=
     * 删除指定名称的存储桶,需校验归属权.
     */
    @DeleteMapping
    public Result<Object> deleteBucket(@RequestParam String bucketName,
                                       HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        bucketService.deleteBucket(bucketName, user.getId());
        return Result.success();
    }


    /**
     * 统一入口: GET /api/v1/bucket[?bucketName=]
     * <p>
     * 合并之前的两个裸 {@code @GetMapping} (listBuckets + getBucket) —— 它们共享同一
     * 路径会触发 Spring MVC Ambiguous mapping. 统一通过 {@code bucketName} 参数分支:
     * <ul>
     *   <li>不提供 bucketName → 列出当前用户全部存储桶</li>
     *   <li>提供 bucketName   → 获取指定存储桶详情 (RESTful alias)</li>
     * </ul>
     *
     * @param bucketName  可选; 提供时按名称查询
     * @param httpRequest HTTP 请求,用于取出当前登录用户
     * @return 列表或详情 (运行时类型决定 JSON 结构)
     */
    @GetMapping
    public Result<?> listOrGetBucket(@RequestParam(value = "bucketName", required = false) String bucketName,
                                     HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        if (bucketName == null || bucketName.isEmpty()) {
            List<BucketVO> list = bucketService.listUserBuckets(user.getId()).stream()
                    .map(this::convertBucket)
                    .collect(Collectors.toList());
            return Result.<List<BucketVO>>success(list);
        }
        OssBucket bucket = bucketService.validateBucket(bucketName, user.getId());
        return Result.<BucketVO>success(convertBucket(bucket));
    }


    /**
     * RESTful alias: PUT /api/v1/bucket?bucketName=
     * 更新指定存储桶的 ACL、地域及策略等信息.
     */
    @PutMapping
    public Result<BucketVO> updateBucket(@RequestParam String bucketName,
                                         @RequestBody UpdateBucketRequest request,
                                         HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssBucket bucket = bucketService.updateBucket(bucketName, user.getId(), request.getAcl(),
                request.getRegion(), request.getPolicy());
        return Result.success(convertBucket(bucket));
    }


    /**
     * RESTful alias: GET /api/v1/bucket/acl?bucketName=
     * 查询指定存储桶的访问控制列表(ACL).
     */
    @GetMapping("/acl")
    public Result<BucketAclVO> getBucketAcl(@RequestParam String bucketName,
                                            HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssBucket bucket = bucketService.validateBucket(bucketName, user.getId());
        BucketAclVO vo = new BucketAclVO();
        vo.setAcl(bucket.getAcl());
        return Result.success(vo);
    }


    /**
     * RESTful alias: PUT /api/v1/bucket/acl?bucketName=
     * 设置指定存储桶的访问控制列表(ACL).
     */
    @PutMapping("/acl")
    public Result<BucketVO> setBucketAcl(@RequestParam String bucketName,
                                         @RequestBody Map<String, String> aclMap,
                                         HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssBucket bucket = bucketService.updateBucket(bucketName, user.getId(), aclMap.get("acl"),
                null, null);
        return Result.success(convertBucket(bucket));
    }


    /**
     * RESTful alias: GET /api/v1/bucket/policy?bucketName=
     * 查询指定存储桶的访问策略.
     */
    @GetMapping("/policy")
    public Result<BucketPolicyVO> getBucketPolicy(@RequestParam String bucketName,
                                                  HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssBucket bucket = bucketService.validateBucket(bucketName, user.getId());
        BucketPolicyVO vo = new BucketPolicyVO();
        vo.setPolicy(bucket.getPolicy());
        return Result.success(vo);
    }


    /**
     * RESTful alias: PUT /api/v1/bucket/policy?bucketName=
     * 设置指定存储桶的访问策略.
     */
    @PutMapping("/policy")
    public Result<BucketVO> setBucketPolicy(@RequestParam String bucketName,
                                            @RequestBody Map<String, String> policyMap,
                                            HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        OssBucket bucket = bucketService.updateBucket(bucketName, user.getId(), null,
                null, policyMap.get("policy"));
        return Result.success(convertBucket(bucket));
    }


    /**
     * RESTful alias: DELETE /api/v1/bucket/policy?bucketName=
     * 删除指定存储桶的访问策略.
     */
    @DeleteMapping("/policy")
    public Result<Object> deleteBucketPolicy(@RequestParam String bucketName,
                                             HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        bucketService.updateBucket(bucketName, user.getId(), null, null, null);
        return Result.success();
    }


    /**
     * RESTful alias: HEAD /api/v1/bucket?bucketName=
     * 检查指定存储桶是否存在且归属当前用户.
     */
    @RequestMapping(method = RequestMethod.HEAD)
    public Result<Object> headBucket(@RequestParam String bucketName,
                                     HttpServletRequest httpRequest) {
        OssUser user = (OssUser) httpRequest.getAttribute(AuthenticationInterceptor.ATTR_USER);
        bucketService.validateBucket(bucketName, user.getId());
        return Result.success();
    }

    /**
     * 将存储桶实体转换为对外 VO.
     *
     * @param bucket 存储桶实体
     * @return 转换后的 VO 对象
     */
    private BucketVO convertBucket(OssBucket bucket) {
        BucketVO vo = new BucketVO();
        vo.setId(bucket.getId());
        vo.setName(bucket.getName());
        vo.setRegion(bucket.getRegion());
        vo.setAcl(bucket.getAcl());
        vo.setCreateTime(bucket.getCreateTime());
        return vo;
    }
}