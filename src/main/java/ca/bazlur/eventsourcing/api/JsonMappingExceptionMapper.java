package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.ErrorResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Provider
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {
    private static final Logger log = LoggerFactory.getLogger(JsonMappingExceptionMapper.class);

    @Override
    public Response toResponse(JsonMappingException exception) {
        var correlationId = UUID.randomUUID().toString();
        var message = "Invalid request format";

        log.warn("JSON mapping failed: {} (correlationId: {})", exception.getMessage(), correlationId);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(
                        "VALIDATION_ERROR",
                        message,
                        correlationId
                ))
                .build();
    }
}