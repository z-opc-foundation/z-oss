package com.zifang.z.oss.api.dto;


/**
 * 创建桶请求
 */
public class CreateBucketRequest {

    private String name;

    private String acl;

    private String region;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAcl() {
        return acl;
    }

    public void setAcl(String acl) {
        this.acl = acl;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}