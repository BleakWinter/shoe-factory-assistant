package com.shoefactory.assistant.util;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.config.FileStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Component
public class FileStorageUtil {

    // rootPath 是归档根目录，下面按 年份/月/类型 保存文件。
    private final Path rootPath;

    public FileStorageUtil(FileStorageProperties properties) {
        this.rootPath = Paths.get(properties.getRootPath()).toAbsolutePath().normalize();
        createDirectories(this.rootPath);
    }

    public StoredFile saveOriginal(MultipartFile file, String fileNo) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = cleanOriginalName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        LocalDate today = LocalDate.now();

        // 原始 Excel 归档格式：根目录/年份/月/original。
        Path directory = archiveDirectory(today, "original");
        createDirectories(directory);
        Path target = uniqueOriginalPath(directory, originalName, fileNo, extension);
        ensureAllowedPath(target);
        try {
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BusinessException("保存原始文件失败", ex);
        }
        return new StoredFile(originalName, extension, file.getContentType(), file.getSize(), target);
    }

    public Path archiveOriginalPath(String originalName, String fileNo, LocalDate date) {
        // 旧路径迁移时使用：按指定日期计算原始 Excel 应该归档到哪里。
        String safeOriginalName = cleanOriginalName(originalName);
        String extension = extractExtension(safeOriginalName);
        LocalDate archiveDate = date == null ? LocalDate.now() : date;
        Path directory = archiveDirectory(archiveDate, "original");
        createDirectories(directory);
        Path target = uniqueOriginalPath(directory, safeOriginalName, fileNo, extension);
        ensureAllowedPath(target);
        return target;
    }

    public Path archiveOrderDetailImagePath(String sourceFileName, String fileNo, String orderNo, int rowIndex, LocalDate date) {
        // 旧图片迁移时使用：按文件编号和行号生成稳定图片路径。
        String extension = extensionOrDefault(sourceFileName, "png");
        LocalDate archiveDate = date == null ? LocalDate.now() : date;
        Path directory = archiveDirectory(archiveDate, "images");
        createDirectories(directory);
        Path target = directory.resolve(fileNo + "-r" + rowIndex + "." + extension).normalize();
        ensureAllowedPath(target);
        return target;
    }

    public void moveFile(Path source, Path target) {
        if (source == null || target == null || source.equals(target) || !Files.exists(source)) {
            return;
        }
        // 移动任何本地文件前都做白名单检查，防止数据库里的异常路径越权。
        ensureAllowedPath(source);
        ensureAllowedPath(target);
        createDirectories(target.getParent());
        try {
            Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("移动文件失败: " + source + " -> " + target, ex);
        }
    }

    public Path allocatePreviewPdfPath(String fileNo, String suffix) {
        String safeSuffix = suffix == null || suffix.isBlank() ? "" : "-" + suffix.toLowerCase(Locale.ROOT);
        // PDF 预览是可再生成文件，统一归档到 根目录/年份/月/pdf 下。
        Path directory = archiveDirectory(LocalDate.now(), "pdf");
        createDirectories(directory);
        Path target = directory.resolve(fileNo + safeSuffix + ".pdf").normalize();
        ensureAllowedPath(target);
        return target;
    }

    public Path allocateOrderPdfPath(String orderNo, String printType) {
        String safeOrderNo = cleanDirectoryName(orderNo == null || orderNo.isBlank()
                ? FileStorageUtil.newBusinessNo("ORDER")
                : orderNo);
        String suffix = printType == null || printType.isBlank() ? "preview" : printType.toLowerCase(Locale.ROOT);
        // 固定保存为 根目录/年份/月/pdf/订单号-order.pdf。
        Path directory = archiveDirectory(LocalDate.now(), "pdf");
        createDirectories(directory);
        Path target = directory.resolve(safeOrderNo + "-" + suffix + ".pdf").normalize();
        ensureAllowedPath(target);
        return target;
    }

    public Path saveOrderDetailImage(byte[] content, String fileNo, String orderNo, int rowIndex, String extension) {
        if (content == null || content.length == 0) {
            return null;
        }
        String safeExtension = extension == null || extension.isBlank() ? "png" : extension.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();

        // Excel 内嵌图片归档格式：根目录/年份/月/images。
        Path directory = archiveDirectory(today, "images");
        createDirectories(directory);
        Path target = directory.resolve(fileNo + "-r" + rowIndex + "." + safeExtension).normalize();
        ensureAllowedPath(target);
        try {
            Files.write(target, content);
            return target;
        } catch (IOException ex) {
            throw new BusinessException("保存订单图片失败", ex);
        }
    }

    public Path resolvePath(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new BusinessException("文件路径为空");
        }
        Path path = Paths.get(pathValue).toAbsolutePath().normalize();
        // 统一入口：任何从数据库/请求里拿到的本地路径，都必须先过白名单。
        ensureAllowedPath(path);
        return path;
    }

    public boolean isArchivedOriginalPath(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(rootPath);
    }

    public boolean isArchivedOrderImagePath(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(rootPath);
    }

    public void copyPdf(Path source, Path target) {
        createDirectories(target.getParent());
        try {
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("复制 PDF 文件失败", ex);
        }
    }

    public String extractExtension(String filename) {
        String cleanName = cleanOriginalName(filename);
        int index = cleanName.lastIndexOf('.');
        if (index < 0 || index == cleanName.length() - 1) {
            throw new BusinessException("文件缺少扩展名");
        }
        return cleanName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String extensionOrDefault(String filename, String fallback) {
        try {
            return extractExtension(filename);
        } catch (BusinessException ex) {
            return fallback;
        }
    }

    public void ensureExists(Path path) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("文件不存在: " + path);
        }
    }

    public static String newBusinessNo(String prefix) {
        // 编号格式：业务前缀 + 毫秒时间戳 + 8 位随机串，方便排序也避免冲突。
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String millis = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now());
        return prefix + millis + random;
    }

    private Path archiveDirectory(LocalDate date, String type) {
        LocalDate archiveDate = date == null ? LocalDate.now() : date;
        return rootPath
                .resolve(String.valueOf(archiveDate.getYear()))
                .resolve(archiveDate.getMonthValue() + "月")
                .resolve(type)
                .toAbsolutePath()
                .normalize();
    }

    private Path uniqueOriginalPath(Path directory, String originalName, String fileNo, String extension) {
        Path target = directory.resolve(originalName).normalize();
        if (!Files.exists(target)) {
            return target;
        }
        // 同名订单重复上传时追加文件编号，不覆盖旧原稿。
        String baseName = originalName.substring(0, originalName.length() - extension.length() - 1);
        return directory.resolve(baseName + "-" + fileNo + "." + extension).normalize();
    }

    private String cleanOriginalName(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "file" : originalFilename);
        filename = filename.replace("\\", "/");
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }
        if (filename.isBlank()) {
            return "file";
        }
        return filename;
    }

    private String cleanDirectoryName(String value) {
        String cleanName = cleanOriginalName(value == null || value.isBlank() ? "达维" : value);
        return cleanName.replace(":", "").replace("*", "").replace("?", "");
    }

    private void ensureAllowedPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        // 只允许访问系统配置的归档根目录，避免接口读取任意本机文件。
        if (!normalized.startsWith(rootPath)) {
            throw new BusinessException("非法文件路径: " + path);
        }
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new BusinessException("创建文件目录失败: " + directory, ex);
        }
    }
}
