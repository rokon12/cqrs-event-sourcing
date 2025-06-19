package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.CreateOrderRequest;
import ca.bazlur.eventsourcing.api.dto.OrderResponse;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OrderResourceIntegrationTest {

    @Test
    void shouldCreateOrderAndRetrieveItById() {
        CreateOrderRequest request = new CreateOrderRequest("customer-123");

        // Create order
        String orderId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .body("customerId", equalTo("customer-123"))
                .body("status", equalTo("DRAFT"))
                .body("orderId", notNullValue())
                .extract()
                .path("orderId");

        // Retrieve order by ID
        given()
                .when()
                .get("/api/orders/{orderId}", orderId)
                .then()
                .statusCode(200)
                .body("orderId", equalTo(orderId))
                .body("customerId", equalTo("customer-123"))
                .body("status", equalTo("DRAFT"))
                .body("createdAt", notNullValue());
    }

    @Test
    void shouldReturnNotFoundForNonExistentOrder() {
        given()
                .when()
                .get("/api/orders/non-existent-order")
                .then()
                .statusCode(404)
                .body("code", equalTo("ORDER_NOT_FOUND"))
                .body("message", containsString("Order not found"));
    }

    @Test
    void shouldGetAllOrders() {
        // Create multiple orders
        CreateOrderRequest request1 = new CreateOrderRequest("customer-1");
        CreateOrderRequest request2 = new CreateOrderRequest("customer-2");

        String orderId1 = given()
                .contentType(ContentType.JSON)
                .body(request1)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("orderId");

        String orderId2 = given()
                .contentType(ContentType.JSON)
                .body(request2)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Get all orders
        List<String> orderIds = given()
                .when()
                .get("/api/orders")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .extract()
                .jsonPath()
                .getList("orderId", String.class);

        assertTrue(orderIds.contains(orderId1));
        assertTrue(orderIds.contains(orderId2));
    }

    @Test
    void shouldGetOrdersByCustomerId() {
        String customerId = "customer-filter-test";
        CreateOrderRequest request = new CreateOrderRequest(customerId);

        // Create orders for specific customer
        String orderId1 = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("orderId");

        String orderId2 = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Create order for different customer
        CreateOrderRequest otherRequest = new CreateOrderRequest("other-customer");
        given()
                .contentType(ContentType.JSON)
                .body(otherRequest)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201);

        // Get orders by customer ID
        List<OrderResponse> customerOrders = given()
                .queryParam("customerId", customerId)
                .when()
                .get("/api/orders")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .extract()
                .jsonPath()
                .getList(".", OrderResponse.class);

        // Verify all returned orders belong to the specified customer
        assertTrue(customerOrders.stream()
                .allMatch(order -> customerId.equals(order.customerId())));
        
        // Verify our orders are included
        List<String> orderIds = customerOrders.stream()
                .map(OrderResponse::orderId)
                .toList();
        assertTrue(orderIds.contains(orderId1));
        assertTrue(orderIds.contains(orderId2));
    }

    @Test
    void shouldHandleInvalidCreateOrderRequest() {
        // Test with null customerId
        CreateOrderRequest invalidRequest = new CreateOrderRequest(null);

        given()
                .contentType(ContentType.JSON)
                .body(invalidRequest)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(400);
    }

    @Test
    void shouldHandleEmptyCustomerIdQuery() {
        // Create some orders first
        CreateOrderRequest request = new CreateOrderRequest("test-customer");
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201);

        // Query with empty customerId should return all orders
        given()
                .queryParam("customerId", "")
                .when()
                .get("/api/orders")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void shouldMaintainConsistencyBetweenWriteAndReadModels() {
        String customerId = "consistency-test-customer";
        CreateOrderRequest request = new CreateOrderRequest(customerId);

        // Create order
        String orderId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Immediately query the order - should be available due to synchronous projection updates
        OrderResponse retrievedOrder = given()
                .when()
                .get("/api/orders/{orderId}", orderId)
                .then()
                .statusCode(200)
                .extract()
                .as(OrderResponse.class);

        // Verify consistency
        assertEquals(orderId, retrievedOrder.orderId());
        assertEquals(customerId, retrievedOrder.customerId());
        assertEquals(OrderStatus.DRAFT, retrievedOrder.status());
        assertNotNull(retrievedOrder.createdAt());
    }

    @Test
    void shouldHandleHighVolumeOfOrders() {
        String customerId = "load-test-customer";
        int numberOfOrders = 100;

        // Create many orders quickly
        for (int i = 0; i < numberOfOrders; i++) {
            CreateOrderRequest request = new CreateOrderRequest(customerId + "-" + i);
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/orders")
                    .then()
                    .statusCode(201);
        }

        // Verify all orders can be retrieved
        List<OrderResponse> allOrders = given()
                .when()
                .get("/api/orders")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(numberOfOrders))
                .extract()
                .jsonPath()
                .getList(".", OrderResponse.class);

        // Verify we can find orders for each customer
        for (int i = 0; i < numberOfOrders; i++) {
            String expectedCustomerId = customerId + "-" + i;
            assertTrue(allOrders.stream()
                    .anyMatch(order -> expectedCustomerId.equals(order.customerId())),
                    "Should find order for customer: " + expectedCustomerId);
        }
    }

    @Test
    void shouldReturnValidTimestamps() {
        CreateOrderRequest request = new CreateOrderRequest("timestamp-test-customer");

        Instant beforeCreation = Instant.now();
        
        String createdAtStr = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .body("createdAt", notNullValue())
                .extract()
                .path("createdAt");

        Instant afterCreation = Instant.now();
        Instant createdAt = Instant.parse(createdAtStr);

        // Verify timestamp is within reasonable bounds
        assertTrue(createdAt.isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(createdAt.isBefore(afterCreation.plusSeconds(1)));
    }
}