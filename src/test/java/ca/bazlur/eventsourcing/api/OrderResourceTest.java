package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.CreateOrderRequest;
import ca.bazlur.eventsourcing.api.dto.ErrorResponse;
import ca.bazlur.eventsourcing.api.dto.OrderResponse;
import ca.bazlur.eventsourcing.core.EventSchemaException;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.infrastructure.JpaEventStore;
import ca.bazlur.eventsourcing.projections.OrderProjection;
import ca.bazlur.eventsourcing.projections.OrderProjectionModel;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderResourceTest {
    private static final int MAX_CUSTOMER_ID_LENGTH = 100;

    @Mock
    private JpaEventStore eventStore;

    @Mock
    private OrderProjection orderProjection;

    @Mock
    private UriInfo uriInfo;

    private OrderResource orderResource;

    @BeforeEach
    void setUp() {
        orderResource = new OrderResource(eventStore, orderProjection);
    }

    @Test
    void shouldCreateOrder() {
        // Given
        var customerId = "customer-123";
        var request = new CreateOrderRequest(customerId);
        var mockUri = URI.create("http://localhost:8080/api/orders/123");

        var uriBuilder = UriBuilder.fromUri(mockUri);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        var orderResponse = (OrderResponse) response.getEntity();
        assertNotNull(orderResponse.orderId());
        assertEquals(customerId, orderResponse.customerId());
        assertEquals(OrderStatus.DRAFT, orderResponse.status());
        assertNotNull(orderResponse.createdAt());

        verify(eventStore).appendEvents(any(), any(), eq(0L));
        verify(orderProjection).handle(any());
    }

    @Test
    void shouldHandleCreateOrderWithNullCustomerId() {
        // Given
        var request = new CreateOrderRequest(null);

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verifyNoInteractions(eventStore);
        verifyNoInteractions(orderProjection);
    }

    @Test
    void shouldGetOrderById() {
        // Given
        var orderId = UUID.randomUUID().toString();
        var customerId = "customer-123";
        var now = Instant.now();
        var projectionModel = createProjectionModel(orderId, customerId, now);

        when(orderProjection.getById(orderId)).thenReturn(projectionModel);

        // When
        var response = orderResource.getOrder(orderId);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        var orderResponse = (OrderResponse) response.getEntity();
        assertEquals(orderId, orderResponse.orderId());
        assertEquals(customerId, orderResponse.customerId());
        assertEquals(OrderStatus.DRAFT, orderResponse.status());
        assertEquals(now, orderResponse.createdAt());
    }

    @Test
    void shouldReturnNotFoundForNonExistentOrder() {
        // Given
        var orderId = UUID.randomUUID().toString();
        when(orderProjection.getById(orderId)).thenReturn(null);

        // When
        var response = orderResource.getOrder(orderId);

        // Then
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void shouldGetAllOrders() {
        // Given
        var order1 = createProjectionModel(UUID.randomUUID().toString(), "customer-1", Instant.now());
        var order2 = createProjectionModel(UUID.randomUUID().toString(), "customer-2", Instant.now());
        when(orderProjection.getAll()).thenReturn(List.of(order1, order2));

        // When
        var response = orderResource.getOrders(null);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        var orders = (List<OrderResponse>) response.getEntity();
        assertEquals(2, orders.size());
        assertEquals(order1.getId(), orders.get(0).orderId());
        assertEquals(order2.getId(), orders.get(1).orderId());
    }

    @Test
    void shouldGetOrdersByCustomerId() {
        // Given
        var customerId = "customer-123";
        var order = createProjectionModel(UUID.randomUUID().toString(), customerId, Instant.now());
        when(orderProjection.getByCustomerId(customerId)).thenReturn(List.of(order));

        // When
        var response = orderResource.getOrders(customerId);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        var orders = (List<OrderResponse>) response.getEntity();
        assertEquals(1, orders.size());
        assertEquals(customerId, orders.get(0).customerId());
    }

    @Test
    void shouldHandleExceptionDuringOrderCreation() {
        // Given
        var request = new CreateOrderRequest("customer-123");
        doThrow(new RuntimeException("Test error"))
            .when(eventStore)
            .appendEvents(any(), any(), anyLong());

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    void shouldHandleEmptyCustomerId() {
        // Given
        var request = new CreateOrderRequest("  ");  // Empty or blank customer ID

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals("VALIDATION_ERROR", errorResponse.code());
        assertTrue(errorResponse.message().contains("Customer ID"));
        assertNotNull(errorResponse.correlationId());
    }

    @Test
    void shouldIncludeCorrelationIdInErrorResponse() {
        // Given
        var request = new CreateOrderRequest(null);

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertNotNull(errorResponse.correlationId());
    }

    @Test
    void shouldHandleSchemaValidationError() {
        // Given
        var request = new CreateOrderRequest("customer-123");
        doThrow(new EventSchemaException("Invalid event schema"))
            .when(eventStore)
            .appendEvents(any(), any(), anyLong());

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals("VALIDATION_ERROR", errorResponse.code());
        assertTrue(errorResponse.message().contains("Invalid event schema"));
    }

    @Test
    void shouldHandleExceptionDuringOrderRetrieval() {
        // Given
        var orderId = UUID.randomUUID().toString();
        when(orderProjection.getById(orderId))
            .thenThrow(new RuntimeException("Test error"));

        // When
        var response = orderResource.getOrder(orderId);

        // Then
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    void shouldVerifyResponseHeaders() {
        // Given
        var customerId = "customer-123";
        var request = new CreateOrderRequest(customerId);
        var mockUri = URI.create("http://localhost:8080/api/orders/123");
        var uriBuilder = UriBuilder.fromUri(mockUri);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        var headers = response.getMetadata();
        assertTrue(headers.containsKey("Location"));
    }

    @Test
    void shouldValidateCustomerIdLength() {
        // Given
        var request = new CreateOrderRequest("x".repeat(MAX_CUSTOMER_ID_LENGTH + 1)); // Exceed max length
        doThrow(new ConstraintViolationException(
            "Customer ID must not exceed " + MAX_CUSTOMER_ID_LENGTH + " characters",
            Set.of()
        )).when(eventStore).appendEvents(any(), any(), anyLong());

        // When
        var response = orderResource.createOrder(request, uriInfo);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals("VALIDATION_ERROR", errorResponse.code());
        System.out.println("[DEBUG_LOG] Error message: " + errorResponse.message());
        assertTrue(errorResponse.message().contains("Validation failed"));
    }

    private OrderProjectionModel createProjectionModel(String orderId, String customerId, Instant createdAt) {
        var model = new OrderProjectionModel();
        model.setId(orderId);
        model.setCustomerId(customerId);
        model.setStatus(OrderStatus.DRAFT);
        model.setCreatedAt(createdAt);
        model.setItems(new ArrayList<>());
        return model;
    }
}
