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
import java.nio.file.Path;
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
            @RequestParam(defaultValue="false") boolean unadoptedOnly,
            @RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(service.list(customerId,developmentNo,developmentNos,startDate,endDate,unadoptedOnly,page,size));
    }
    @GetMapping("/development-options")
    public ApiResponse<java.util.List<DevelopmentNoOptionResponse>> developmentOptions(@RequestParam(required=false) Long customerId) {
        return ApiResponse.ok(service.developmentOptions(customerId));
    }
    @GetMapping("/for-price-config")
    public ApiResponse<Long> forPriceConfig(@RequestParam Long customerId,@RequestParam String developmentNo) {
        return ApiResponse.ok(service.forPriceConfig(customerId,developmentNo));
    }
    @GetMapping("/{id}")
    public ApiResponse<QuotationResponse> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping
    public ApiResponse<QuotationResponse> create(@Valid @RequestBody QuotationSaveRequest request) { return ApiResponse.ok(service.create(request)); }
    @PostMapping("/{id}/versions")
    public ApiResponse<QuotationResponse> version(@PathVariable Long id,@Valid @RequestBody QuotationSaveRequest request) { return ApiResponse.ok(service.createVersion(id,request)); }
    @PostMapping("/{id}/signed-photo-reuse")
    public ApiResponse<QuotationResponse.SignedPhotoReuse> signedPhotoReuse(@PathVariable Long id, @Valid @RequestBody QuotationSaveRequest request) {
        return ApiResponse.ok(service.signedPhotoReuse(id,request));
    }
    @PostMapping("/{id}/adopt")
    public ApiResponse<QuotationResponse> adopt(@PathVariable Long id, @RequestPart(value="file",required=false) MultipartFile file,
            @RequestParam(required=false) Long reuseSignedPhotoFrom) {
        return ApiResponse.ok(service.adopt(id,file,reuseSignedPhotoFrom));
    }
    @GetMapping("/{id}/signed-image")
    public ResponseEntity<UrlResource> signedImage(@PathVariable Long id) throws IOException {
        return imageResponse(service.signedImage(id),ResponseEntity.ok().cacheControl(CacheControl.noStore()));
    }
    @GetMapping("/reference")
    public ApiResponse<QuotationResponse.Reference> reference(@RequestParam Long customerId,@RequestParam String developmentNo) { return ApiResponse.ok(service.reference(customerId,developmentNo)); }
    @PostMapping(value="/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<QuotationResponse.Image> image(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(images.upload(file)); }
    @GetMapping("/images/{year}/{month}/{file}")
    public ResponseEntity<UrlResource> image(@PathVariable String year,@PathVariable String month,@PathVariable String file) throws IOException {
        return imageResponse(images.require(year + "/" + month + "/" + file),ResponseEntity.ok());
    }
    private ResponseEntity<UrlResource> imageResponse(Path path, ResponseEntity.BodyBuilder response) throws IOException {
        return response.contentType(path.getFileName().toString().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
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
