package com.zifang.z.oss.core.file;

/**
 * 文件上传构建器 — 通过 {@code FileStorageService.of(bytes).setPath(...).setSaveFilename(...).upload()} 链式调用。
 *
 * <p>upload() 的真实上传逻辑由 {@link FileStorageServiceImpl} 注入，本类仅持有数据。
 */
public class FileInfo {

    private byte[] bytes;
    private String path;
    private String saveFilename;
    private String url;

    /**
     * 执行上传（由 FileStorageServiceImpl 重写注入真实逻辑）。
     */
    public FileInfo upload() {
        return this;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public FileInfo setPath(String path) {
        this.path = path;
        return this;
    }

    public String getSaveFilename() {
        return saveFilename;
    }

    public FileInfo setSaveFilename(String saveFilename) {
        this.saveFilename = saveFilename;
        return this;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public FileInfo setBytes(byte[] bytes) {
        this.bytes = bytes;
        return this;
    }
}
