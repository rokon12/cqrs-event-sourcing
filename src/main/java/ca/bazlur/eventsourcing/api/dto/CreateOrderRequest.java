package ca.bazlur.eventsourcing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank(message = "Customer ID is required")
        @Size(max = 100, message = "Customer ID must not exceed 100 characters")
        String customerId
) {
}
