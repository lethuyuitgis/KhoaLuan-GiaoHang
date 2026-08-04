package com.shop.delivery.order.service;

import com.shop.delivery.order.entity.SavedAddress;
import com.shop.delivery.order.repository.SavedAddressRepository;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedAddressServiceTest {

    @Mock SavedAddressRepository repo;
    @InjectMocks SavedAddressService service;

    private final Long customerId = 42L;
    private final BigDecimal lat = new BigDecimal("21.0285000");
    private final BigDecimal lng = new BigDecimal("105.8542000");

    @Test
    void recordUseCreatesNewWhenNoneExists() {
        when(repo.findByCustomerIdAndLatAndLng(customerId, lat, lng)).thenReturn(Optional.empty());

        service.recordUse(customerId, "12 Hàng Đào", lat, lng);

        ArgumentCaptor<SavedAddress> cap = ArgumentCaptor.forClass(SavedAddress.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(cap.getValue().getAddress()).isEqualTo("12 Hàng Đào");
        assertThat(cap.getValue().getUseCount()).isEqualTo(1);
    }

    @Test
    void recordUseBumpsExistingOnSameCoords() {
        SavedAddress existing = new SavedAddress();
        existing.setCustomerId(customerId);
        existing.setAddress("old label");
        existing.setUseCount(3);
        existing.setLastUsedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(repo.findByCustomerIdAndLatAndLng(customerId, lat, lng)).thenReturn(Optional.of(existing));

        service.recordUse(customerId, "new label", lat, lng);

        assertThat(existing.getUseCount()).isEqualTo(4);
        assertThat(existing.getAddress()).isEqualTo("new label");
        assertThat(existing.getLastUsedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        verify(repo).save(existing);
    }

    @Test
    void recordUseIgnoresNullInputs() {
        service.recordUse(customerId, null, lat, lng);
        verify(repo, never()).save(any());
    }

    @Test
    void deleteRejectsWhenNotOwner() {
        SavedAddress other = new SavedAddress();
        other.setId(9L);
        other.setCustomerId(999L); // belongs to someone else
        when(repo.findById(9L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.delete(9L, customerId))
            .isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void deleteRemovesOwnedAddress() {
        SavedAddress mine = new SavedAddress();
        mine.setId(5L);
        mine.setCustomerId(customerId);
        when(repo.findById(5L)).thenReturn(Optional.of(mine));

        service.delete(5L, customerId);

        verify(repo).delete(mine);
    }
}
