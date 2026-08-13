package com.shop.delivery.order.service;

import com.shop.delivery.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageStorageTest {

    @TempDir Path tmp;

    private ProductImageStorage storage() {
        return new ProductImageStorage(tmp.toString());
    }

    @Test
    void storesJpegAndReturnsWebPathWithUuidName() throws Exception {
        // Tên file client gửi lên KHÔNG được dùng lại (chống ../../ path traversal) —
        // đường dẫn trả về phải là UUID + đuôi theo content-type.
        var file = new MockMultipartFile("file", "../../evil.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String url = storage().store(file);

        assertThat(url).matches("/api/files/products/[0-9a-f-]{36}\\.jpg");
        Path saved = tmp.resolve("products").resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(Files.readAllBytes(saved)).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsNonImageContentType() {
        var file = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> storage().store(file))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("JPG, PNG");
    }

    @Test
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> storage().store(file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsOversizedFile() {
        var file = new MockMultipartFile("file", "x.png", "image/png", new byte[6 * 1024 * 1024]);
        assertThatThrownBy(() -> storage().store(file))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("5MB");
    }
}
