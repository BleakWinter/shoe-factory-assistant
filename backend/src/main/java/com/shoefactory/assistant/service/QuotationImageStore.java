package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.config.FileStorageProperties;
import com.shoefactory.assistant.dto.QuotationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class QuotationImageStore {
    private static final Logger log = LoggerFactory.getLogger(QuotationImageStore.class);
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private final Path archiveRoot;
    private final Path root;
    private final Path signedRoot;
    public QuotationImageStore(FileStorageProperties properties) {
        archiveRoot = Path.of(properties.getRootPath()).toAbsolutePath().normalize();
        root = archiveRoot.resolve("quotation-images");
        signedRoot = archiveRoot.resolve("quotation-signed-images");
    }
    public QuotationResponse.Image upload(MultipartFile file) {
        String key = upload(file,root,"鞋图");
        return new QuotationResponse.Image(key,url(key));
    }
    public String uploadSigned(MultipartFile file) {
        String key = upload(file,signedRoot,"客户签字报价单照片");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            Path path = signedRoot.resolve(key);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try { Files.deleteIfExists(path); }
                        catch (IOException ex) { log.warn("清理未采用的签字报价单照片失败: {}",key,ex); }
                    }
                }
            });
        }
        return key;
    }
    private String upload(MultipartFile file, Path directory, String label) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择" + label);
        if (file.getSize() > MAX_BYTES) throw new BusinessException(label + "不能超过 10MB");
        try { return save(file.getBytes(),directory,label); }
        catch (IOException ex) { throw new BusinessException("读取" + label + "失败",ex); }
    }
    /** 独立复制订单图片，避免报价依赖仍未创建的订单或后续订单图片变更。 */
    public QuotationResponse.Image copyReference(Path source) {
        try {
            if (!source.toRealPath().startsWith(archiveRoot.toRealPath())) throw new BusinessException("非法鞋图路径");
            if (Files.size(source) > MAX_BYTES) throw new BusinessException("原订单图片超过 10MB，请另行上传鞋图");
            String key = save(Files.readAllBytes(source),root,"鞋图");
            return new QuotationResponse.Image(key,url(key));
        } catch (IOException ex) { throw new BusinessException("原订单鞋图不可用，请重新上传鞋图",ex); }
    }
    private String save(byte[] bytes, Path directory, String label) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new BusinessException(label + "大小无效");
        String extension;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(label + "仅支持 PNG、JPG 格式");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) throw new BusinessException(label + "仅支持 PNG、JPG 格式");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 25_000_000) throw new BusinessException(label + "分辨率过大，请缩小后上传");
                reader.read(0); // 校验完整图片，避免仅有有效文件头的损坏图片入库。
                extension = format.equals("png") ? "png" : "jpg";
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new BusinessException(label + "文件损坏",ex); }
        LocalDate today = LocalDate.now();
        String key = "%04d/%02d/%s.%s".formatted(today.getYear(),today.getMonthValue(),UUID.randomUUID(),extension);
        Path target = checkedPath(key,directory,label);
        try {
            Files.createDirectories(target.getParent());
            if (!target.getParent().toRealPath().startsWith(directory.toRealPath())) throw new BusinessException("非法" + label + "路径");
            Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) { throw new BusinessException("保存" + label + "失败",ex); }
        return key;
    }
    public String url(String key) { return key == null || key.isBlank() ? null : "/api/quotations/images/" + key; }
    public Path require(String key) {
        return require(key,root,"鞋图");
    }
    public Path requireSigned(String key) {
        return require(key,signedRoot,"客户签字报价单照片");
    }
    private Path require(String key, Path directory, String label) {
        Path path = checkedPath(key,directory,label);
        String missing = label + "不存在，请重新上传";
        try {
            if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(directory.toRealPath())) throw new BusinessException(missing);
        } catch (IOException ex) { throw new BusinessException(missing,ex); }
        return path;
    }
    private Path checkedPath(String key, Path directory, String label) {
        if (key == null || !key.matches("\\d{4}/(?:0[1-9]|1[0-2])/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpg)")) throw new BusinessException("非法" + label + "编号");
        Path path = directory.resolve(key).normalize();
        if (!path.startsWith(directory)) throw new BusinessException("非法" + label + "路径");
        return path;
    }
}
