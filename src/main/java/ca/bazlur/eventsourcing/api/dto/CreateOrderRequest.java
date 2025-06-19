package ca.bazlur.eventsourcing.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank(message = "Customer ID is required")
        String customerId
) {
}
