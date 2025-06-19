package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.CreateOrderRequest;
import ca.bazlur.eventsourcing.api.dto.ErrorResponse;
import ca.bazlur.eventsourcing.api.dto.OrderResponse;
import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.infrastructure.JpaEventStore;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);
    private final JpaEventStore eventStore;

    public OrderResource(JpaEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @POST
    @WithSpan("api.create-order")
    public Response createOrder(@Valid CreateOrderRequest request, @Context UriInfo uriInfo) {
        var orderId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        try {
            log.info("Creating order for customer: {}", request.customerId());

            var order = Order.create(orderId, request.customerId(), correlationId);
            eventStore.appendEvents(orderId, order.getUncommittedEvents(), 0);

            var location = uriInfo.getAbsolutePathBuilder()
                    .path(orderId)
                    .build();

            var response = new OrderResponse(orderId, request.customerId(), OrderStatus.DRAFT, Instant.now());

            log.info("Created order: {} for customer: {}", orderId, request.customerId());
            return Response.created(location).entity(response).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request while creating order: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                            "VALIDATION_ERROR",
                            e.getMessage(),
                            correlationId
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Failed to create order", e);
            return Response.serverError()
                    .entity(new ErrorResponse(
                            "INTERNAL_ERROR",
                            "Failed to create order",
                            correlationId
                    ))
                    .build();
        }
    }
}



