package com.shop.delivery.bot.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedUpdateServiceTest {

    @Mock ProcessedUpdateRepository repo;

    @InjectMocks ProcessedUpdateService service;

    @Test
    void markProcessedShouldReturnTrueForNewUpdate() {
        when(repo.existsById(1L)).thenReturn(false);
        when(repo.saveAndFlush(any(ProcessedUpdate.class))).thenReturn(new ProcessedUpdate(1L));

        boolean result = service.markIfNew(1L);

        assertThat(result).isTrue();
    }

    @Test
    void markProcessedShouldReturnFalseForDuplicateDetectedByExistsById() {
        when(repo.existsById(1L)).thenReturn(true);

        boolean result = service.markIfNew(1L);

        assertThat(result).isFalse();
    }

    @Test
    void markProcessedShouldReturnFalseForRaceConditionDuplicate() {
        // existsById says "not there" but a concurrent insert wins the race
        // and saveAndFlush throws DataIntegrityViolationException
        when(repo.existsById(1L)).thenReturn(false);
        when(repo.saveAndFlush(any(ProcessedUpdate.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean result = service.markIfNew(1L);

        assertThat(result).isFalse();
    }
}
