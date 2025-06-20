package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonParseException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Provider
public class JsonParsingExceptionMapper implements ExceptionMapper<JsonParseException> {
    private static final Logger log = LoggerFactory.getLogger(JsonParsingExceptionMapper.class);

    @Override
    public Response toResponse(JsonParseException exception) {
        var correlationId = UUID.randomUUID().toString();
        var message = "Invalid JSON format";

        log.warn("JSON parsing failed: {} (correlationId: {})", exception.getMessage(), correlationId);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(
                        "VALIDATION_ERROR",
                        message,
                        correlationId
                ))
                .build();
    }
}