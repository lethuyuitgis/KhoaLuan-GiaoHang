package com.shop.delivery.order.service;

import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.repository.ProductRepository;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository repo;
    @InjectMocks ProductService service;

    Product sample;

    @BeforeEach
    void setup() {
        sample = new Product();
        sample.setId(1L);
        sample.setName("Áo thun nam M");
        sample.setPrice(new BigDecimal("199000"));
        sample.setStock(10);
        sample.setActive(true);
    }

    @Test
    void createShouldPersistNewProduct() {
        when(repo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        Product result = service.create("Áo polo", "Cotton 100%",
            new BigDecimal("250000"), "https://img/url.jpg", "drink", 20);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getName()).isEqualTo("Áo polo");
        assertThat(result.getPrice()).isEqualByComparingTo("250000");
        assertThat(result.getStock()).isEqualTo(20);
        assertThat(result.getCategory()).isEqualTo("drink");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void createDefaultsCategoryToFoodWhenOmitted() {
        // Miniapp lọc theo category thật (V18) — sản phẩm không khai báo phải rơi
        // vào 'food' chứ không được để null (vi phạm NOT NULL + vỡ filter).
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.create("Áo polo", null,
            new BigDecimal("250000"), null, null, null);

        assertThat(result.getCategory()).isEqualTo("food");
    }

    @Test
    void findByIdReturnsProduct() {
        when(repo.findById(1L)).thenReturn(Optional.of(sample));
        Product result = service.findById(1L);
        assertThat(result.getName()).isEqualTo("Áo thun nam M");
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    void listActiveReturnsOnlyActiveProducts() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findAllByActiveTrue(pageable))
            .thenReturn(new PageImpl<>(List.of(sample)));

        Page<Product> result = service.listActive(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isActive()).isTrue();
    }

    @Test
    void updateShouldModifyExistingProduct() {
        when(repo.findById(1L)).thenReturn(Optional.of(sample));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, "New Name", "New Desc",
            new BigDecimal("300000"), null, "dessert", 5);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("300000");
        assertThat(captor.getValue().getCategory()).isEqualTo("dessert");
        assertThat(captor.getValue().getStock()).isEqualTo(5);
    }

    @Test
    void deactivateSetsActiveFalseNotDelete() {
        when(repo.findById(1L)).thenReturn(Optional.of(sample));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(1L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }
}
