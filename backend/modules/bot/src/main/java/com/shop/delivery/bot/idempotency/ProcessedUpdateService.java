package com.shop.delivery.bot.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedUpdateService {

    private final ProcessedUpdateRepository repo;

    public ProcessedUpdateService(ProcessedUpdateRepository repo) {
        this.repo = repo;
    }

    /**
     * Đánh dấu update đã xử lý. Trả true nếu là update mới, false nếu đã xử lý rồi.
     * Dùng REQUIRES_NEW để insert idempotent của riêng nó không bị rollback chung
     * với business transaction phía gọi.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markIfNew(Long updateId) {
        try {
            repo.save(new ProcessedUpdate(updateId));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
