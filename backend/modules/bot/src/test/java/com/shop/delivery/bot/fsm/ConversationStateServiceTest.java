package com.shop.delivery.bot.fsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStateServiceTest {

    @Mock ConversationStateRepository repo;
    @InjectMocks ConversationStateService service;

    @Test
    void put_newUser_createsRow() {
        when(repo.findById(1001L)).thenReturn(Optional.empty());

        service.put(1001L, "CUSTOMER_RATING_COMMENT", Map.of("orderId", "abc-123"));

        ArgumentCaptor<ConversationState> captor = ArgumentCaptor.forClass(ConversationState.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTelegramUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getState()).isEqualTo("CUSTOMER_RATING_COMMENT");
        assertThat(captor.getValue().getData()).containsEntry("orderId", "abc-123");
    }

    @Test
    void put_existingUser_overwritesState() {
        ConversationState existing = new ConversationState();
        existing.setTelegramUserId(1001L);
        existing.setState("OLD_STATE");
        existing.setData(Map.of("x", 1));
        when(repo.findById(1001L)).thenReturn(Optional.of(existing));

        service.put(1001L, "NEW_STATE", Map.of("y", 2));

        ArgumentCaptor<ConversationState> captor = ArgumentCaptor.forClass(ConversationState.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo("NEW_STATE");
        assertThat(captor.getValue().getData()).containsOnly(Map.entry("y", 2));
    }

    @Test
    void get_returnsEmpty_whenNoRow() {
        when(repo.findById(1001L)).thenReturn(Optional.empty());

        assertThat(service.get(1001L)).isEmpty();
    }

    @Test
    void clear_deletesRow() {
        when(repo.existsById(1001L)).thenReturn(true);
        service.clear(1001L);
        verify(repo).deleteById(1001L);
    }
}
