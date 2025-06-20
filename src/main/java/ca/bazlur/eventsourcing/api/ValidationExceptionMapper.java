package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    private static final Logger log = LoggerFactory.getLogger(ValidationExceptionMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var correlationId = UUID.randomUUID().toString();
        var message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");

        log.warn("Validation failed: {} (correlationId: {})", message, correlationId);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(
                        "VALIDATION_ERROR",
                        message,
                        correlationId
                ))
                .build();
    }
}