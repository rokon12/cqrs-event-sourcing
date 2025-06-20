package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.api.dto.CreateOrderRequest;
import ca.bazlur.eventsourcing.api.dto.OrderResponse;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ca.bazlur.eventsourcing.test.PostgresTestResource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@Transactional
@QuarkusTestResource(PostgresTestResource.class)
class OrderResourceIntegrationTest {
    @Inject
    EntityManager entityManager;

    @BeforeAll
    static void setup() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    @Transactional
    void setupAndCleanDatabase() {
        // Clean up in correct order to handle foreign key constraints
        entityManager.createNativeQuery("DELETE FROM order_item_projections").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM order_projections CASCADE").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM events").executeUpdate();
    }

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
                .log().ifValidationFails()
                .statusCode(201)
                .body("customerId", equalTo("customer-123"))
                .body("status", equalTo("DRAFT"))
                .body("orderId", notNullValue())
                .extract()
                .path("orderId");

        // Wait for order to be available in read model
        await().atMost(2, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .untilAsserted(() ->
                   given()
                       .when()
                       .get("/api/orders/{orderId}", orderId)
                       .then()
                       .log().ifValidationFails()
                       .statusCode(200)
                       .body("orderId", equalTo(orderId))
                       .body("customerId", equalTo("customer-123"))
                       .body("status", equalTo("DRAFT"))
                       .body("createdAt", notNullValue())
               );
    }

    @Test
    void shouldReturnNotFoundForNonExistentOrder() {
        String nonExistentId = "non-existent-order";

        given()
            .when()
            .get("/api/orders/{orderId}", nonExistentId)
            .then()
            .log().ifValidationFails()
            .statusCode(404)
            .body("code", equalTo("ORDER_NOT_FOUND"))
            .body("message", containsString("Order not found"));
    }

    @Test
    void shouldGetAllOrders() {
        // Create first order
        CreateOrderRequest request1 = new CreateOrderRequest("customer-1");
        String orderId1 = given()
                .contentType(ContentType.JSON)
                .body(request1)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Create second order
        CreateOrderRequest request2 = new CreateOrderRequest("customer-2");
        String orderId2 = given()
                .contentType(ContentType.JSON)
                .body(request2)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Wait for orders to be available in read model and verify
        await().atMost(2, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   List<String> orderIds = given()
                           .when()
                           .get("/api/orders")
                           .then()
                           .log().ifValidationFails()
                           .statusCode(200)
                           .body("size()", greaterThanOrEqualTo(2))
                           .extract()
                           .jsonPath()
                           .getList("orderId", String.class);

                   assertTrue(orderIds.contains(orderId1), "First order ID not found in response");
                   assertTrue(orderIds.contains(orderId2), "Second order ID not found in response");
               });
    }

    @Test
    void shouldGetOrdersByCustomerId() {
        String customerId = "customer-filter-test";
        CreateOrderRequest request = new CreateOrderRequest(customerId);

        // Create first order for the customer
        String orderId1 = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Create second order for the same customer
        String orderId2 = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
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
                .log().ifValidationFails()
                .statusCode(201);

        // Wait for orders to be available and verify customer-specific orders
        await().atMost(2, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   List<OrderResponse> customerOrders = given()
                           .queryParam("customerId", customerId)
                           .when()
                           .get("/api/orders")
                           .then()
                           .log().ifValidationFails()
                           .statusCode(200)
                           .body("size()", greaterThanOrEqualTo(2))
                           .extract()
                           .jsonPath()
                           .getList(".", OrderResponse.class);

                   // Verify all returned orders belong to the specified customer
                   assertTrue(customerOrders.stream()
                           .allMatch(order -> customerId.equals(order.customerId())),
                           "All orders should belong to customer: " + customerId);

                   // Verify specific orders are included
                   List<String> orderIds = customerOrders.stream()
                           .map(OrderResponse::orderId)
                           .toList();
                   assertTrue(orderIds.contains(orderId1), "First order ID not found in response");
                   assertTrue(orderIds.contains(orderId2), "Second order ID not found in response");
               });
    }

    @Test
    void shouldHandleInvalidCreateOrderRequest() {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(null);

        given()
                .contentType(ContentType.JSON)
                .body(invalidRequest)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", notNullValue());
    }

    @Test
    void shouldHandleEmptyCustomerIdQuery() {
        // Create test order
        CreateOrderRequest request = new CreateOrderRequest("test-customer");
        String orderId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Wait for order to be available and verify empty customer ID query
        await().atMost(2, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .untilAsserted(() ->
                   given()
                       .queryParam("customerId", "")
                       .when()
                       .get("/api/orders")
                       .then()
                       .log().ifValidationFails()
                       .statusCode(200)
                       .body("size()", greaterThanOrEqualTo(1))
                       .body("find { it.orderId == '" + orderId + "' }", notNullValue())
               );
    }

    @Test
    void shouldMaintainConsistencyBetweenWriteAndReadModels() {
        String customerId = "consistency-test-customer";
        CreateOrderRequest request = new CreateOrderRequest(customerId);

        // Create order in write model
        String orderId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .path("orderId");

        // Verify consistency between write and read models
        await().atMost(2, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   OrderResponse retrievedOrder = given()
                           .when()
                           .get("/api/orders/{orderId}", orderId)
                           .then()
                           .log().ifValidationFails()
                           .statusCode(200)
                           .extract()
                           .as(OrderResponse.class);

                   assertEquals(orderId, retrievedOrder.orderId(), "Order ID should match");
                   assertEquals(customerId, retrievedOrder.customerId(), "Customer ID should match");
                   assertEquals(OrderStatus.DRAFT, retrievedOrder.status(), "Order status should be DRAFT");
                   assertNotNull(retrievedOrder.createdAt(), "Created timestamp should not be null");
               });
    }

    @Test
    void shouldHandleHighVolumeOfOrders() {
        String customerId = "load-test-customer";
        int numberOfOrders = 100;
        List<String> expectedCustomerIds = new ArrayList<>();

        // Create multiple orders
        for (int i = 0; i < numberOfOrders; i++) {
            String currentCustomerId = customerId + "-" + i;
            expectedCustomerIds.add(currentCustomerId);

            CreateOrderRequest request = new CreateOrderRequest(currentCustomerId);
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/orders")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(201);
        }

        // Wait for all orders to be available and verify
        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   List<OrderResponse> allOrders = given()
                           .when()
                           .get("/api/orders")
                           .then()
                           .log().ifValidationFails()
                           .statusCode(200)
                           .body("size()", greaterThanOrEqualTo(numberOfOrders))
                           .extract()
                           .jsonPath()
                           .getList(".", OrderResponse.class);

                   // Verify all expected customer orders exist
                   Set<String> retrievedCustomerIds = allOrders.stream()
                           .map(OrderResponse::customerId)
                           .collect(Collectors.toSet());

                   assertTrue(retrievedCustomerIds.containsAll(expectedCustomerIds),
                           "All expected customer orders should be found");
               });
    }

    @Test
    void shouldReturnValidTimestamps() {
        String customerId = "timestamp-test-customer";
        CreateOrderRequest request = new CreateOrderRequest(customerId);
        Instant beforeCreation = Instant.now();

        // Create order and verify timestamp
        String createdAtStr = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .body("createdAt", notNullValue())
                .extract()
                .path("createdAt");

        Instant afterCreation = Instant.now();
        Instant createdAt = Instant.parse(createdAtStr);

        // Verify timestamp is within reasonable bounds
        assertTrue(createdAt.isAfter(beforeCreation.minusSeconds(1)),
                "Created timestamp should not be before test start");
        assertTrue(createdAt.isBefore(afterCreation.plusSeconds(1)),
                "Created timestamp should be before test end");
    }
}
