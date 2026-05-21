package com.shoefactory.assistant.util;

import java.nio.file.Path;

public class StoredFile {

    // 保存文件后返回的结果对象，给打印任务保存原稿信息使用。
    private final String originalName;
    private final String extension;
    private final String mimeType;
    private final long size;
    private final Path path;

    public StoredFile(String originalName, String extension, String mimeType, long size, Path path) {
        this.originalName = originalName;
        this.extension = extension;
        this.mimeType = mimeType;
        this.size = size;
        this.path = path;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public Path getPath() {
        return path;
    }
}
