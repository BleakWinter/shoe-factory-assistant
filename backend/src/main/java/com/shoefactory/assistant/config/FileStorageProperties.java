package com.shoefactory.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    private String rootPath;
    private String orderArchiveRootPath;
    private String orderArchiveCustomer = "达维";
    private String orderImageArchiveRootPath;
    private String printImageSwapPath;
    private String libreOfficeCommand = "soffice";
    private long libreOfficeTimeoutSeconds = 120;
    private String previewUrlPrefix = "/api/print-previews";

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getOrderArchiveRootPath() {
        return orderArchiveRootPath;
    }

    public void setOrderArchiveRootPath(String orderArchiveRootPath) {
        this.orderArchiveRootPath = orderArchiveRootPath;
    }

    public String getOrderArchiveCustomer() {
        return orderArchiveCustomer;
    }

    public void setOrderArchiveCustomer(String orderArchiveCustomer) {
        this.orderArchiveCustomer = orderArchiveCustomer;
    }

    public String getOrderImageArchiveRootPath() {
        return orderImageArchiveRootPath;
    }

    public void setOrderImageArchiveRootPath(String orderImageArchiveRootPath) {
        this.orderImageArchiveRootPath = orderImageArchiveRootPath;
    }

    public String getPrintImageSwapPath() {
        return printImageSwapPath;
    }

    public void setPrintImageSwapPath(String printImageSwapPath) {
        this.printImageSwapPath = printImageSwapPath;
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
