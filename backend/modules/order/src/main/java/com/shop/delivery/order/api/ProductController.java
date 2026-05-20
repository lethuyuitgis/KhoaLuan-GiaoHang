package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.api.mapper.ProductMapper;
import com.shop.delivery.order.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final ProductMapper mapper;

    public ProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ProductResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.listActive(pageable).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return mapper.toResponse(service.findById(id));
    }
}
