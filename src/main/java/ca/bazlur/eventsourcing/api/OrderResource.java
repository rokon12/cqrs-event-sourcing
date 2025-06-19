package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.infrastructure.JpaEventStore;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);

    private final JpaEventStore eventStore;

    @Inject
    public OrderResource(JpaEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @POST
    @WithSpan("api.create-order")
    public CompletableFuture<Response> createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        return CompletableFuture.supplyAsync(() -> {
            try {
                Order order = Order.create(orderId, request.customerId(), correlationId);

                return eventStore.appendEvents(orderId, order.getUncommittedEvents(), 0)
                        .thenApply(unused -> {
                            log.info("Created order: {} for customer: {}", orderId, request.customerId());
                            return Response.status(Response.Status.CREATED)
                                    .entity(new OrderResponse(orderId, request.customerId(), "DRAFT"))
                                    .build();
                        })
                        .exceptionally(throwable -> {
                            log.error("Failed to create order", throwable);
                            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .entity(new ErrorResponse("Failed to create order: " + throwable.getMessage()))
                                    .build();
                        })
                        .join();

            } catch (Exception e) {
                log.error("Failed to create order", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse("Failed to create order: " + e.getMessage()))
                        .build();
            }
        });
    }

    public record CreateOrderRequest(String customerId) {
    }

    public record OrderResponse(String orderId, String customerId, String status) {
    }

    public record ErrorResponse(String message) {
    }
}