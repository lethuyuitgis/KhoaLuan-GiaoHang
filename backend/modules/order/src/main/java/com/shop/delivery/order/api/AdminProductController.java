package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CreateProductRequest;
import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.api.dto.UpdateProductRequest;
import com.shop.delivery.order.api.mapper.ProductMapper;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin product management.
 * TODO P4: Add @PreAuthorize("hasRole('SHOP_OWNER')") khi có JWT.
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService service;
    private final ProductMapper mapper;

    public AdminProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ProductResponse> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.listAll(pageable).map(mapper::toResponse);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        Product p = service.create(req.name(), req.description(), req.price(), req.imageUrl(), req.stock());
        return mapper.toResponse(p);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        Product p = service.update(id, req.name(), req.description(), req.price(), req.imageUrl(), req.stock());
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
