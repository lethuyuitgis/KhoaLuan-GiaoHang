package com.shop.delivery.order.service;

import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.repository.ProductRepository;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Product create(String name, String description, BigDecimal price, String imageUrl, Integer stock) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setImageUrl(imageUrl);
        p.setStock(stock != null ? stock : 0);
        p.setActive(true);
        return repo.save(p);
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Sản phẩm " + id + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public Page<Product> listActive(Pageable pageable) {
        return repo.findAllByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> listAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    @Transactional
    public Product update(Long id, String name, String description, BigDecimal price, String imageUrl, Integer stock) {
        Product p = findById(id);
        if (name != null) p.setName(name);
        if (description != null) p.setDescription(description);
        if (price != null) p.setPrice(price);
        if (imageUrl != null) p.setImageUrl(imageUrl);
        if (stock != null) p.setStock(stock);
        return repo.save(p);
    }

    @Transactional
    public void deactivate(Long id) {
        Product p = findById(id);
        p.setActive(false);
        repo.save(p);
    }

    @Transactional
    public void activate(Long id) {
        Product p = findById(id);
        p.setActive(true);
        repo.save(p);
    }
}
