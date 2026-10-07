package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.config.FileStorageProperties;
import com.shoefactory.assistant.dto.QuotationResponse;
import org.springframework.stereotype.Service;
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
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private final Path archiveRoot;
    private final Path root;
    public QuotationImageStore(FileStorageProperties properties) {
        archiveRoot = Path.of(properties.getRootPath()).toAbsolutePath().normalize();
        root = archiveRoot.resolve("quotation-images");
    }
    public QuotationResponse.Image upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择鞋图");
        if (file.getSize() > MAX_BYTES) throw new BusinessException("鞋图不能超过 10MB");
        try { return save(file.getBytes()); }
        catch (IOException ex) { throw new BusinessException("读取鞋图失败",ex); }
    }
    /** 独立复制订单图片，避免报价依赖仍未创建的订单或后续订单图片变更。 */
    public QuotationResponse.Image copyReference(Path source) {
        try {
            if (!source.toRealPath().startsWith(archiveRoot.toRealPath())) throw new BusinessException("非法鞋图路径");
            if (Files.size(source) > MAX_BYTES) throw new BusinessException("原订单图片超过 10MB，请另行上传鞋图");
            return save(Files.readAllBytes(source));
        } catch (IOException ex) { throw new BusinessException("原订单鞋图不可用，请重新上传鞋图",ex); }
    }
    private QuotationResponse.Image save(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new BusinessException("鞋图大小无效");
        String extension;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("鞋图仅支持 PNG、JPG 格式");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) throw new BusinessException("鞋图仅支持 PNG、JPG 格式");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 25_000_000) throw new BusinessException("鞋图分辨率过大，请缩小后上传");
                reader.read(0); // 校验完整图片，避免仅有有效文件头的损坏图片入库。
                extension = format.equals("png") ? "png" : "jpg";
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new BusinessException("鞋图文件损坏",ex); }
        LocalDate today = LocalDate.now();
        String key = "%04d/%02d/%s.%s".formatted(today.getYear(),today.getMonthValue(),UUID.randomUUID(),extension);
        Path target = checkedPath(key);
        try {
            Files.createDirectories(target.getParent());
            if (!target.getParent().toRealPath().startsWith(root.toRealPath())) throw new BusinessException("非法鞋图路径");
            Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) { throw new BusinessException("保存鞋图失败",ex); }
        return new QuotationResponse.Image(key,url(key));
    }
    public String url(String key) { return key == null || key.isBlank() ? null : "/api/quotations/images/" + key; }
    public Path require(String key) {
        Path path = checkedPath(key);
        try {
            if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath())) throw new BusinessException("鞋图不存在，请重新上传");
        } catch (IOException ex) { throw new BusinessException("鞋图不存在，请重新上传",ex); }
        return path;
    }
    private Path checkedPath(String key) {
        if (key == null || !key.matches("\\d{4}/(?:0[1-9]|1[0-2])/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpg)")) throw new BusinessException("非法鞋图编号");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new BusinessException("非法鞋图路径");
        return path;
    }
}
