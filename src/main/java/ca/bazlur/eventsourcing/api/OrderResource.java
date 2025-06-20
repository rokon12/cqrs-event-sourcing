package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.CreateOrderRequest;
import ca.bazlur.eventsourcing.api.dto.ErrorResponse;
import ca.bazlur.eventsourcing.api.dto.OrderResponse;
import ca.bazlur.eventsourcing.core.EventSchemaException;
import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.infrastructure.JpaEventStore;
import ca.bazlur.eventsourcing.projections.OrderProjection;
import ca.bazlur.eventsourcing.projections.OrderProjectionModel;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);
    private final JpaEventStore eventStore;
    private final OrderProjection orderProjection;

    public OrderResource(JpaEventStore eventStore, OrderProjection orderProjection) {
        this.eventStore = eventStore;
        this.orderProjection = orderProjection;
    }

    @POST
    @WithSpan("api.create-order")
    public Response createOrder(@Valid CreateOrderRequest request, @Context UriInfo uriInfo) {
        var orderId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        try {
            log.info("Creating order for customer: {}", request.customerId());

            var order = Order.create(orderId, request.customerId(), correlationId);
            var events = order.getUncommittedEvents();
            eventStore.appendEvents(orderId, events, 0);

            // Update projection with new events
            events.forEach(orderProjection::handle);

            var location = uriInfo.getAbsolutePathBuilder()
                    .path(orderId)
                    .build();

            var response = new OrderResponse(orderId, request.customerId(), OrderStatus.DRAFT, Instant.now());

            log.info("Created order: {} for customer: {}", orderId, request.customerId());
            return Response.created(location).entity(response).build();

        } catch (EventSchemaException e) {
            log.warn("Schema validation failed while creating order: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                            "VALIDATION_ERROR",
                            "Invalid event schema: " + e.getMessage(),
                            correlationId
                    ))
                    .build();

        } catch (ConstraintViolationException e) {
            var message = e.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Validation failed");
            log.warn("Validation failed while creating order: {}", message);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                            "VALIDATION_ERROR",
                            message,
                            correlationId
                    ))
                    .build();

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

    @GET
    @Path("/{orderId}")
    @WithSpan("api.get-order")
    public Response getOrder(@PathParam("orderId") String orderId) {
        try {
            var orderProjection = this.orderProjection.getById(orderId);

            if (orderProjection == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(
                                "ORDER_NOT_FOUND",
                                "Order not found: " + orderId,
                                null
                        ))
                        .build();
            }

            var response = mapToOrderResponse(orderProjection);
            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to retrieve order: {}", orderId, e);
            return Response.serverError()
                    .entity(new ErrorResponse(
                            "INTERNAL_ERROR",
                            "Failed to retrieve order",
                            null
                    ))
                    .build();
        }
    }

    @GET
    @WithSpan("api.get-orders")
    public Response getOrders(@QueryParam("customerId") String customerId) {
        try {
            List<OrderProjectionModel> orders;

            if (customerId != null && !customerId.trim().isEmpty()) {
                orders = orderProjection.getByCustomerId(customerId);
            } else {
                orders = orderProjection.getAll();
            }

            var responses = orders.stream()
                    .map(this::mapToOrderResponse)
                    .toList();

            return Response.ok(responses).build();

        } catch (Exception e) {
            log.error("Failed to retrieve orders for customer: {}", customerId, e);
            return Response.serverError()
                    .entity(new ErrorResponse(
                            "INTERNAL_ERROR",
                            "Failed to retrieve orders",
                            null
                    ))
                    .build();
        }
    }

    private OrderResponse mapToOrderResponse(OrderProjectionModel order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
