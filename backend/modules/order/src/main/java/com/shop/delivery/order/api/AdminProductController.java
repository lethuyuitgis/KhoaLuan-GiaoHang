package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CreateProductRequest;
import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.api.dto.UpdateProductRequest;
import com.shop.delivery.order.api.mapper.ProductMapper;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.service.ProductImageStorage;
import com.shop.delivery.order.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminProductController {

    private final ProductService service;
    private final ProductMapper mapper;
    private final ProductImageStorage imageStorage;

    public AdminProductController(ProductService service, ProductMapper mapper,
                                  ProductImageStorage imageStorage) {
        this.service = service;
        this.mapper = mapper;
        this.imageStorage = imageStorage;
    }

    @GetMapping
    public Page<ProductResponse> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.listAll(pageable).map(mapper::toResponse);
    }

    /** Upload ảnh sản phẩm — trả về URL để đưa vào imageUrl khi tạo/sửa. */
    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Map.of("url", imageStorage.store(file));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        Product p = service.create(req.name(), req.description(), req.price(), req.imageUrl(), req.category(), req.stock());
        return mapper.toResponse(p);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        Product p = service.update(id, req.name(), req.description(), req.price(), req.imageUrl(), req.category(), req.stock());
        return mapper.toResponse(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }
}
