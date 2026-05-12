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
    private final FileStorageProperties properties;

    public FileStorageUtil(FileStorageProperties properties) {
        this.properties = properties;
        this.rootPath = Paths.get(properties.getRootPath()).toAbsolutePath().normalize();
        createDirectories(this.rootPath);
    }

    public StoredFile saveOriginal(MultipartFile file, String fileNo) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = cleanOriginalName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        Path directory = rootPath.resolve("original")
                .resolve(LocalDate.now().format(DATE_DIR_FORMATTER))
                .toAbsolutePath()
                .normalize();
        createDirectories(directory);
        Path target = directory.resolve(fileNo + "." + extension).normalize();
        ensureInRoot(target);
        try {
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BusinessException("保存原始文件失败", ex);
        }
        return new StoredFile(originalName, extension, file.getContentType(), file.getSize(), target);
    }

    public Path allocatePreviewPdfPath(String fileNo, String suffix) {
        String safeSuffix = suffix == null || suffix.isBlank() ? "" : "-" + suffix.toLowerCase(Locale.ROOT);
        Path directory = rootPath.resolve("preview")
                .resolve(LocalDate.now().format(DATE_DIR_FORMATTER))
                .toAbsolutePath()
                .normalize();
        createDirectories(directory);
        Path target = directory.resolve(fileNo + safeSuffix + ".pdf").normalize();
        ensureInRoot(target);
        return target;
    }

    public String buildPreviewUrl(Long fileId) {
        String prefix = properties.getPreviewUrlPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + fileId + "/preview";
    }

    public Path saveOrderLineImage(byte[] content, String fileNo, int rowIndex, String extension) {
        if (content == null || content.length == 0) {
            return null;
        }
        String safeExtension = extension == null || extension.isBlank() ? "png" : extension.toLowerCase(Locale.ROOT);
        Path directory = rootPath.resolve("images")
                .resolve(LocalDate.now().format(DATE_DIR_FORMATTER))
                .toAbsolutePath()
                .normalize();
        createDirectories(directory);
        Path target = directory.resolve(fileNo + "-r" + rowIndex + "." + safeExtension).normalize();
        ensureInRoot(target);
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
        ensureInRoot(path);
        return path;
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

    private void ensureInRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(rootPath)) {
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
