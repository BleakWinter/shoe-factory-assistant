package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.service.PrintPreviewService;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/print-previews")
public class PrintPreviewController {

    // 只负责把已经生成好的 PDF 文件返回给浏览器预览。
    private final PrintPreviewService printPreviewService;

    public PrintPreviewController(PrintPreviewService printPreviewService) {
        this.printPreviewService = printPreviewService;
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<UrlResource> preview(@PathVariable Long id) throws MalformedURLException {
        // Content-Disposition inline 让浏览器直接打开 PDF，而不是强制下载。
        Path previewPdf = printPreviewService.loadPreviewPdf(id);
        UrlResource resource = new UrlResource(previewPdf.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(fileSize(previewPdf))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(previewPdf.getFileName().toString(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1;
        }
    }
}
