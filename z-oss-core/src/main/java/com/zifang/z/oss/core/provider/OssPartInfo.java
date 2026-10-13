package com.zifang.z.oss.core.provider;

/**
 * 分片上传的一个分片信息。
 * <p>
 * 对象存储的分片上传是"客户端分片直传 + 服务端按 partNumber 归并"的模式，
 * 这里描述的是归并时必需的最小元信息：分片序号、ETag 和大小。
 */
public class OssPartInfo {

    /**
     * 分片序号，从 1 开始
     */
    private int partNumber;

    /**
     * 分片 ETag（合并时用于校验）
     */
    private String etag;

    /**
     * 分片字节数
     */
    private long size;

    public OssPartInfo() {
    }

    public OssPartInfo(int partNumber, String etag, long size) {
        this.partNumber = partNumber;
        this.etag = etag;
        this.size = size;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}