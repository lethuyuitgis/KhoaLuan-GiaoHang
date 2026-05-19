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
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = DataIntegrityViolationException.class)
    public boolean markIfNew(Long updateId) {
        // Pre-check: cheap PK lookup avoids the common case of an INSERT
        // that fails on duplicate (which would otherwise mark the tx as
        // rollback-only). REQUIRES_NEW means this transaction is independent
        // from any caller transaction.
        if (repo.existsById(updateId)) {
            return false;
        }
        try {
            // saveAndFlush forces the INSERT to execute immediately so a
            // race-condition duplicate (inserted between existsById and here)
            // throws DataIntegrityViolationException here, inside the try,
            // rather than at transaction commit time. noRollbackFor above
            // keeps the tx committable even after the constraint violation.
            repo.saveAndFlush(new ProcessedUpdate(updateId));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
