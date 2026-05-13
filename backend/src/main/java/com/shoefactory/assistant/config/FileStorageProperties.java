package com.shoefactory.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    // 系统生成文件根目录，例如 PDF 预览；默认值来自 application.yml。
    private String rootPath;
    // 原始订单 Excel 归档根目录，例如 D:/清化资料。
    private String orderArchiveRootPath;
    // 原始订单归档时的客户目录名，例如“达维”。
    private String orderArchiveCustomer = "达维";
    // Excel 内嵌图片提取后的归档根目录。
    private String orderImageArchiveRootPath;
    // 订单/装箱单 PDF 生成后的归档根目录。
    private String pdfArchiveRootPath;
    // 后续“替换图片参与打印”功能预留目录。
    private String printImageSwapPath;
    // LibreOffice 命令名或完整路径。
    private String libreOfficeCommand = "soffice";
    // Excel 转 PDF 最长等待时间，防止转换进程卡死。
    private long libreOfficeTimeoutSeconds = 120;
    // PDF 预览 URL 前缀，最终会拼成 /api/print-previews/{id}/preview。
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

    public String getPdfArchiveRootPath() {
        return pdfArchiveRootPath;
    }

    public void setPdfArchiveRootPath(String pdfArchiveRootPath) {
        this.pdfArchiveRootPath = pdfArchiveRootPath;
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
