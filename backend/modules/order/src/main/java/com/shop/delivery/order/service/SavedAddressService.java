package com.shop.delivery.order.service;

import com.shop.delivery.order.entity.SavedAddress;
import com.shop.delivery.order.repository.SavedAddressRepository;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Manages a customer's frequently-used delivery addresses. Addresses are
 * auto-captured on order placement (see {@code SavedAddressListener}) and read
 * back for Mini App autocomplete.
 */
@Service
public class SavedAddressService {

    /** Cap the autocomplete list — a customer never needs more than this. */
    private static final int MAX_LISTED = 10;

    private final SavedAddressRepository repo;

    public SavedAddressService(SavedAddressRepository repo) {
        this.repo = repo;
    }

    /**
     * Upsert an address the customer just used. Deduped by (customer, lat, lng):
     * an existing row bumps its use-count + last-used timestamp (and refreshes
     * the label to the newest resolved address string); otherwise a new row is
     * created. Idempotent-ish — safe to call once per placed order.
     */
    @Transactional
    public void recordUse(Long customerId, String address, BigDecimal lat, BigDecimal lng) {
        if (customerId == null || address == null || lat == null || lng == null) {
            return; // nothing usable to remember
        }
        repo.findByCustomerIdAndLatAndLng(customerId, lat, lng).ifPresentOrElse(
            existing -> {
                existing.setUseCount(existing.getUseCount() + 1);
                existing.setLastUsedAt(Instant.now());
                existing.setAddress(address);
                repo.save(existing);
            },
            () -> {
                SavedAddress sa = new SavedAddress();
                sa.setCustomerId(customerId);
                sa.setAddress(address);
                sa.setLat(lat);
                sa.setLng(lng);
                repo.save(sa);
            });
    }

    @Transactional(readOnly = true)
    public List<SavedAddress> list(Long customerId) {
        return repo.findByCustomerIdOrderByLastUsedAtDesc(customerId, PageRequest.of(0, MAX_LISTED));
    }

    /**
     * Delete one of the customer's saved addresses. Ownership-scoped: a row that
     * doesn't exist OR belongs to someone else is reported as not found (so we
     * never confirm another customer's address ids).
     */
    @Transactional
    public void delete(Long id, Long customerId) {
        SavedAddress sa = repo.findById(id)
            .filter(a -> a.getCustomerId().equals(customerId))
            .orElseThrow(() -> new NotFoundException(
                "SAVED_ADDRESS_NOT_FOUND", "Không tìm thấy địa chỉ đã lưu " + id));
        repo.delete(sa);
    }
}
