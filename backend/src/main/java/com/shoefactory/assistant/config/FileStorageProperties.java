package com.shoefactory.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    private String rootPath;
    private String libreOfficeCommand = "soffice";
    private long libreOfficeTimeoutSeconds = 120;
    private String previewUrlPrefix = "/api/print-previews";

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

    public String getPreviewUrlPrefix() {
        return previewUrlPrefix;
    }

    public void setPreviewUrlPrefix(String previewUrlPrefix) {
        this.previewUrlPrefix = previewUrlPrefix;
    }
}
