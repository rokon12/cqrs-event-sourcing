package ca.bazlur.eventsourcing.api.dto;

public record ErrorResponse(
        String code,
        String message,
        String correlationId
) {}

