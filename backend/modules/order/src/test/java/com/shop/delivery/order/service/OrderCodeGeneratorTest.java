package com.shop.delivery.order.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCodeGeneratorTest {

    OrderCodeGenerator gen = new OrderCodeGenerator();

    @Test
    void shouldGenerateCodeMatchingExpectedFormat() {
        String code = gen.generate();
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(code).startsWith("DH" + today + "-");
        assertThat(code).hasSize(2 + 8 + 1 + 5);  // "DH" + date + "-" + 5 chars
    }

    @Test
    void generatedCodesShouldBeReasonablyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(gen.generate());
        // 5 chars from 33-char alphabet = 39M combos. 1000 samples should mostly be unique.
        assertThat(seen).hasSizeGreaterThan(995);
    }
}
