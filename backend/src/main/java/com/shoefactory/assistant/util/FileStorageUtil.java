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
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Component
public class FileStorageUtil {

    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final Path rootPath;
    private final Path orderArchiveRootPath;
    private final Path orderImageArchiveRootPath;
    private final Path printImageSwapPath;
    private final FileStorageProperties properties;

    public FileStorageUtil(FileStorageProperties properties) {
        this.properties = properties;
        this.rootPath = Paths.get(properties.getRootPath()).toAbsolutePath().normalize();
        this.orderArchiveRootPath = archiveRootPath(properties);
        this.orderImageArchiveRootPath = imageArchiveRootPath(properties);
        this.printImageSwapPath = printImageSwapPath(properties);
        createDirectories(this.rootPath);
        createDirectories(this.orderArchiveRootPath);
        createDirectories(this.orderImageArchiveRootPath);
        createDirectories(this.printImageSwapPath);
    }

    public StoredFile saveOriginal(MultipartFile file, String fileNo) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = cleanOriginalName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        LocalDate today = LocalDate.now();
        Path directory = orderArchiveRootPath
                .resolve(String.valueOf(today.getYear()))
                .resolve(cleanDirectoryName(properties.getOrderArchiveCustomer()))
                .resolve(today.getMonthValue() + "月")
                .toAbsolutePath()
                .normalize();
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
        String safeOriginalName = cleanOriginalName(originalName);
        String extension = extractExtension(safeOriginalName);
        LocalDate archiveDate = date == null ? LocalDate.now() : date;
        Path directory = orderArchiveRootPath
                .resolve(String.valueOf(archiveDate.getYear()))
                .resolve(cleanDirectoryName(properties.getOrderArchiveCustomer()))
                .resolve(archiveDate.getMonthValue() + "月")
                .toAbsolutePath()
                .normalize();
        createDirectories(directory);
        Path target = uniqueOriginalPath(directory, safeOriginalName, fileNo, extension);
        ensureAllowedPath(target);
        return target;
    }

    public Path archiveOrderLineImagePath(String sourceFileName, String fileNo, String orderNo, int rowIndex, LocalDate date) {
        String extension = extensionOrDefault(sourceFileName, "png");
        LocalDate archiveDate = date == null ? LocalDate.now() : date;
        Path directory = orderImageArchiveRootPath
                .resolve(String.valueOf(archiveDate.getYear()))
                .resolve(archiveDate.getMonthValue() + "月")
                .resolve(cleanDirectoryName(orderNo == null || orderNo.isBlank() ? fileNo : orderNo))
                .toAbsolutePath()
                .normalize();
        createDirectories(directory);
        Path target = directory.resolve(fileNo + "-r" + rowIndex + "." + extension).normalize();
        ensureAllowedPath(target);
        return target;
    }

    public void moveFile(Path source, Path target) {
        if (source == null || target == null || source.equals(target) || !Files.exists(source)) {
            return;
        }
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
        Path directory = rootPath.resolve("preview")
                .resolve(LocalDate.now().format(DATE_DIR_FORMATTER))
                .toAbsolutePath()
                .normalize();
        createDirectories(directory);
        Path target = directory.resolve(fileNo + safeSuffix + ".pdf").normalize();
        ensureAllowedPath(target);
        return target;
    }

    public String buildPreviewUrl(Long fileId) {
        String prefix = properties.getPreviewUrlPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + fileId + "/preview";
    }

    public Path saveOrderLineImage(byte[] content, String fileNo, String orderNo, int rowIndex, String extension) {
        if (content == null || content.length == 0) {
            return null;
        }
        String safeExtension = extension == null || extension.isBlank() ? "png" : extension.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        Path directory = orderImageArchiveRootPath
                .resolve(String.valueOf(today.getYear()))
                .resolve(today.getMonthValue() + "月")
                .resolve(cleanDirectoryName(orderNo == null || orderNo.isBlank() ? fileNo : orderNo))
                .toAbsolutePath()
                .normalize();
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
        ensureAllowedPath(path);
        return path;
    }

    public boolean isArchivedOriginalPath(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(orderArchiveRootPath);
    }

    public boolean isArchivedOrderImagePath(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(orderImageArchiveRootPath);
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
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String millis = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now());
        return prefix + millis + random;
    }

    private Path archiveRootPath(FileStorageProperties properties) {
        String archiveRoot = properties.getOrderArchiveRootPath();
        if (archiveRoot == null || archiveRoot.isBlank()) {
            return rootPath.resolve("original").toAbsolutePath().normalize();
        }
        return Paths.get(archiveRoot).toAbsolutePath().normalize();
    }

    private Path imageArchiveRootPath(FileStorageProperties properties) {
        String imageArchiveRoot = properties.getOrderImageArchiveRootPath();
        if (imageArchiveRoot == null || imageArchiveRoot.isBlank()) {
            return rootPath.resolve("images").toAbsolutePath().normalize();
        }
        return Paths.get(imageArchiveRoot).toAbsolutePath().normalize();
    }

    private Path printImageSwapPath(FileStorageProperties properties) {
        String swapPath = properties.getPrintImageSwapPath();
        if (swapPath == null || swapPath.isBlank()) {
            return orderImageArchiveRootPath.resolve("swap").toAbsolutePath().normalize();
        }
        return Paths.get(swapPath).toAbsolutePath().normalize();
    }

    private Path uniqueOriginalPath(Path directory, String originalName, String fileNo, String extension) {
        Path target = directory.resolve(originalName).normalize();
        if (!Files.exists(target)) {
            return target;
        }
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
        if (!normalized.startsWith(rootPath)
                && !normalized.startsWith(orderArchiveRootPath)
                && !normalized.startsWith(orderImageArchiveRootPath)
                && !normalized.startsWith(printImageSwapPath)) {
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
