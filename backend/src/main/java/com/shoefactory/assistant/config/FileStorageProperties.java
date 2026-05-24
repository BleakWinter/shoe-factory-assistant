package com.shoefactory.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    // 系统生成文件根目录，例如 PDF 预览；默认值来自 application.yml。
    private String rootPath;
    // LibreOffice 命令名或完整路径。
    private String libreOfficeCommand = "soffice";
    // Excel 转 PDF 最长等待时间，防止转换进程卡死。
    private long libreOfficeTimeoutSeconds = 120;

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getLibreOfficeCommand() {
        return libreOfficeCommand;
    }

    public void setLibreOfficeCommand(String libreOfficeCommand) {
        this.libreOfficeCommand = libreOfficeCommand;
    }

    public long getLibreOfficeTimeoutSeconds() {
        return libreOfficeTimeoutSeconds;
    }

    public void setLibreOfficeTimeoutSeconds(long libreOfficeTimeoutSeconds) {
        this.libreOfficeTimeoutSeconds = libreOfficeTimeoutSeconds;
    }

}
