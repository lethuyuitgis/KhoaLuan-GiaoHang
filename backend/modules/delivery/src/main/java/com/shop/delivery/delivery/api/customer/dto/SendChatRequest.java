package com.shop.delivery.delivery.api.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a chat message the customer sends from the Mini App. */
public record SendChatRequest(
    @NotBlank(message = "Tin nhắn không được trống")
    @Size(max = 1000, message = "Tin nhắn tối đa 1000 ký tự")
    String body
) {}
