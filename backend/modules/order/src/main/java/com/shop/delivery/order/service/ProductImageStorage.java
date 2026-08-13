package com.shop.delivery.order.service;

import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu ảnh sản phẩm do admin upload vào đĩa ({@code app.uploads-dir}) và trả về
 * đường dẫn web {@code /api/files/products/<uuid>.<ext>} — được serve tĩnh qua
 * {@link com.shop.delivery.order.config.UploadsWebConfig}. Tên file luôn là
 * UUID sinh mới nên không dùng gì từ tên file người dùng gửi lên (chống path
 * traversal), loại file xác định theo Content-Type khai báo trong request.
 */
@Service
public class ProductImageStorage {

    /** Content-Type → đuôi file. Chỉ nhận định dạng ảnh phổ biến. */
    private static final Map<String, String> ALLOWED = Map.of(
        "image/jpeg", ".jpg",
        "image/png",  ".png",
        "image/webp", ".webp"
    );
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final Path productDir;

    public ProductImageStorage(@Value("${app.uploads-dir:uploads}") String uploadsDir) {
        this.productDir = Path.of(uploadsDir, "products");
    }

    /** @return đường dẫn web tương đối của ảnh vừa lưu. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("IMAGE_EMPTY", "Chưa chọn file ảnh");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("IMAGE_TOO_LARGE", "Ảnh tối đa 5MB");
        }
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) {
            throw new ValidationException("IMAGE_TYPE_INVALID",
                "Chỉ nhận ảnh JPG, PNG hoặc WebP (nhận được: " + file.getContentType() + ")");
        }
        String filename = UUID.randomUUID() + ext;
        try {
            Files.createDirectories(productDir);
            try (var in = file.getInputStream()) {
                Files.copy(in, productDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được file ảnh", e);
        }
        return "/api/files/products/" + filename;
    }
}
