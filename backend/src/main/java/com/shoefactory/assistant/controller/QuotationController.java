package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.*;
import com.shoefactory.assistant.service.QuotationImageStore;
import com.shoefactory.assistant.service.QuotationService;
import com.shoefactory.assistant.service.QuotationExcelService;
import jakarta.validation.Valid;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/quotations")
public class QuotationController {
    private final QuotationService service;
    private final QuotationImageStore images;
    private final QuotationExcelService excel;
    public QuotationController(QuotationService service, QuotationImageStore images, QuotationExcelService excel) {
        this.service=service; this.images=images; this.excel=excel;
    }
    @GetMapping
    public ApiResponse<PageResponse<QuotationResponse>> list(@RequestParam(required=false) Long customerId,
            @RequestParam(required=false) String developmentNo,
            @RequestParam(required=false) String developmentNos,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(service.list(customerId,developmentNo,developmentNos,startDate,endDate,page,size));
    }
    @GetMapping("/development-options")
    public ApiResponse<java.util.List<DevelopmentNoOptionResponse>> developmentOptions(@RequestParam(required=false) Long customerId) {
        return ApiResponse.ok(service.developmentOptions(customerId));
    }
    @GetMapping("/{id}")
    public ApiResponse<QuotationResponse> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping
    public ApiResponse<QuotationResponse> create(@Valid @RequestBody QuotationSaveRequest request) { return ApiResponse.ok(service.create(request)); }
    @PostMapping("/{id}/versions")
    public ApiResponse<QuotationResponse> version(@PathVariable Long id,@Valid @RequestBody QuotationSaveRequest request) { return ApiResponse.ok(service.createVersion(id,request)); }
    @PostMapping("/{id}/adopt")
    public ApiResponse<QuotationResponse> adopt(@PathVariable Long id) { return ApiResponse.ok(service.adopt(id)); }
    @GetMapping("/reference")
    public ApiResponse<QuotationResponse.Reference> reference(@RequestParam Long customerId,@RequestParam String developmentNo) { return ApiResponse.ok(service.reference(customerId,developmentNo)); }
    @PostMapping(value="/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<QuotationResponse.Image> image(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(images.upload(file)); }
    @GetMapping("/images/{year}/{month}/{file}")
    public ResponseEntity<UrlResource> image(@PathVariable String year,@PathVariable String month,@PathVariable String file) throws IOException {
        var path=images.require(year + "/" + month + "/" + file);
        return ResponseEntity.ok().contentType(file.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .contentLength(Files.size(path)).header("X-Content-Type-Options","nosniff").body(new UrlResource(path.toUri()));
    }
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable Long id) {
        var quotation=service.get(id);
        String filename="报价-" + (quotation.developmentNo().isBlank() ? "未填款号" : quotation.developmentNo().replaceAll("[\\\\/:*?\"<>|]","_")) + "-V" + quotation.versionNo() + ".xlsx";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
                .body(excel.export(quotation));
    }
}
