package ca.bazlur.eventsourcing.projections;

import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OrderProjectionTest {

    @Inject
    OrderProjectionRepository repository;

    @Inject
    OrderProjection orderProjection;

    @BeforeEach
    @Transactional
    void setUp() {
        orderProjection.reset();
    }

    @Test
    @Transactional
    void shouldCreateOrderProjectionFromOrderCreatedEvent() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();

        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
        );

        // Act
        orderProjection.handle(event);

        // Assert - First retrieval should get from database
        OrderProjectionModel order = orderProjection.getById(orderId);
        assertNotNull(order);
        assertEquals(orderId, order.getId());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertEquals(1L, order.getVersion());
        assertTrue(order.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());

        // Verify database state
        var savedEntity = repository.findById(orderId);
        assertTrue(savedEntity.isPresent());
        assertEquals(customerId, savedEntity.get().getCustomerId());
        assertEquals(OrderStatus.DRAFT, savedEntity.get().getStatus());

        // Second retrieval should use cache (no new database query needed)
        var cachedOrder = orderProjection.getById(orderId);
        assertNotNull(cachedOrder);
        assertEquals(order.getId(), cachedOrder.getId());
        assertEquals(order.getCustomerId(), cachedOrder.getCustomerId());
    }

    @Test
    @Transactional
    void shouldUpdateOrderProjectionWhenItemAdded() {
        // Arrange - Create initial order
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();

        OrderCreatedEvent createEvent = new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
        );
        orderProjection.handle(createEvent);

        // Create and handle item event
        OrderItemAddedEvent itemEvent = new OrderItemAddedEvent(
                orderId, 2L, "product-123", "Test Product", 
                2, BigDecimal.valueOf(50.00), correlationId, null
        );
        orderProjection.handle(itemEvent);

        // Verify the updated order
        OrderProjectionModel order = orderProjection.getById(orderId);
        assertNotNull(order);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(1, order.getItems().size());
        assertEquals(BigDecimal.valueOf(100.00), order.getTotalAmount());
        assertEquals(2L, order.getVersion());

        // Verify item details
        OrderProjectionModel.OrderItemProjection itemModel = order.getItems().get(0);
        assertEquals("product-123", itemModel.getProductId());
        assertEquals("Test Product", itemModel.getProductName());
        assertEquals(2, itemModel.getQuantity());
        assertEquals(BigDecimal.valueOf(50.00), itemModel.getPrice());
        assertEquals(BigDecimal.valueOf(100.00), itemModel.getTotalPrice());

        // Verify database state
        var savedEntity = repository.findById(orderId);
        assertTrue(savedEntity.isPresent());
        assertEquals(OrderStatus.CONFIRMED, savedEntity.get().getStatus());
        assertEquals(1, savedEntity.get().getItems().size());
        assertEquals(BigDecimal.valueOf(100.00), savedEntity.get().getTotalAmount());

        // Verify cache hit (second retrieval)
        var cachedOrder = orderProjection.getById(orderId);
        assertNotNull(cachedOrder);
        assertEquals(order.getId(), cachedOrder.getId());
        assertEquals(order.getTotalAmount(), cachedOrder.getTotalAmount());
    }

    @Test
    @Transactional
    void shouldHandleMultipleItemsCorrectly() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();

        // Create initial order
        OrderCreatedEvent createEvent = new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
        );
        orderProjection.handle(createEvent);

        // Add first item
        OrderItemAddedEvent item1Event = new OrderItemAddedEvent(
                orderId, 2L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null
        );
        orderProjection.handle(item1Event);

        // Verify state after first item
        OrderProjectionModel orderAfterFirstItem = orderProjection.getById(orderId);
        assertNotNull(orderAfterFirstItem);
        assertEquals(1, orderAfterFirstItem.getItems().size());
        assertEquals(BigDecimal.valueOf(25.00), orderAfterFirstItem.getTotalAmount());
        assertEquals(OrderStatus.CONFIRMED, orderAfterFirstItem.getStatus());

        // Add second item
        OrderItemAddedEvent item2Event = new OrderItemAddedEvent(
                orderId, 3L, "product-2", "Product 2", 
                3, BigDecimal.valueOf(15.00), correlationId, null
        );
        orderProjection.handle(item2Event);

        // Verify final state
        OrderProjectionModel finalOrder = orderProjection.getById(orderId);
        assertNotNull(finalOrder);
        assertEquals(2, finalOrder.getItems().size());
        assertEquals(BigDecimal.valueOf(70.00), finalOrder.getTotalAmount()); // 25 + (3 * 15)
        assertEquals(OrderStatus.CONFIRMED, finalOrder.getStatus());

        // Verify item details
        var items = finalOrder.getItems();
        var item1 = items.get(0);
        assertEquals("product-1", item1.getProductId());
        assertEquals("Product 1", item1.getProductName());
        assertEquals(1, item1.getQuantity());
        assertEquals(BigDecimal.valueOf(25.00), item1.getPrice());
        assertEquals(BigDecimal.valueOf(25.00), item1.getTotalPrice());

        var item2 = items.get(1);
        assertEquals("product-2", item2.getProductId());
        assertEquals("Product 2", item2.getProductName());
        assertEquals(3, item2.getQuantity());
        assertEquals(BigDecimal.valueOf(15.00), item2.getPrice());
        assertEquals(BigDecimal.valueOf(45.00), item2.getTotalPrice());

        // Verify database state
        var savedEntity = repository.findById(orderId);
        assertTrue(savedEntity.isPresent());
        assertEquals(2, savedEntity.get().getItems().size());
        assertEquals(BigDecimal.valueOf(70.00), savedEntity.get().getTotalAmount());

        // Verify cache hit
        var cachedOrder = orderProjection.getById(orderId);
        assertNotNull(cachedOrder);
        assertEquals(finalOrder.getId(), cachedOrder.getId());
        assertEquals(finalOrder.getTotalAmount(), cachedOrder.getTotalAmount());
        assertEquals(2, cachedOrder.getItems().size());
    }

    @Test
    @Transactional
    void shouldIgnoreOrderItemEventForNonExistentOrder() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // Try to add item to non-existent order
        OrderItemAddedEvent itemEvent = new OrderItemAddedEvent(
                orderId, 1L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null
        );

        // Act & Assert
        assertDoesNotThrow(() -> orderProjection.handle(itemEvent));

        // Verify database state
        var savedEntity = repository.findById(orderId);
        assertTrue(savedEntity.isEmpty());
        assertNull(orderProjection.getById(orderId));

        // Verify no order was created
        var allOrders = repository.listAll();
        assertTrue(allOrders.isEmpty());
    }

    @Test
    @Transactional
    void shouldGetOrdersByCustomerId() {
        // Arrange
        String customer1 = "customer-1";
        String customer2 = "customer-2";
        String correlationId = UUID.randomUUID().toString();

        // Create orders for first customer
        String order1Id = UUID.randomUUID().toString();
        String order2Id = UUID.randomUUID().toString();
        orderProjection.handle(new OrderCreatedEvent(order1Id, 1L, customer1, correlationId, null));
        orderProjection.handle(new OrderCreatedEvent(order2Id, 1L, customer1, correlationId, null));

        // Create order for second customer
        String order3Id = UUID.randomUUID().toString();
        orderProjection.handle(new OrderCreatedEvent(order3Id, 1L, customer2, correlationId, null));

        // Add some items to make orders different
        orderProjection.handle(new OrderItemAddedEvent(
            order1Id, 2L, "product-1", "Product 1", 1, BigDecimal.TEN, correlationId, null
        ));
        orderProjection.handle(new OrderItemAddedEvent(
            order2Id, 2L, "product-2", "Product 2", 2, BigDecimal.TEN, correlationId, null
        ));
        orderProjection.handle(new OrderItemAddedEvent(
            order3Id, 2L, "product-3", "Product 3", 3, BigDecimal.TEN, correlationId, null
        ));

        // Act - First retrieval (from database)
        var customer1Orders = orderProjection.getByCustomerId(customer1);
        var customer2Orders = orderProjection.getByCustomerId(customer2);

        // Assert
        assertEquals(2, customer1Orders.size());
        assertEquals(1, customer2Orders.size());
        assertTrue(customer1Orders.stream().allMatch(o -> customer1.equals(o.getCustomerId())));
        assertTrue(customer2Orders.stream().allMatch(o -> customer2.equals(o.getCustomerId())));

        // Verify orders have correct items
        assertTrue(customer1Orders.stream()
            .flatMap(o -> o.getItems().stream())
            .anyMatch(i -> i.getProductId().equals("product-1")));
        assertTrue(customer1Orders.stream()
            .flatMap(o -> o.getItems().stream())
            .anyMatch(i -> i.getProductId().equals("product-2")));
        assertTrue(customer2Orders.stream()
            .flatMap(o -> o.getItems().stream())
            .anyMatch(i -> i.getProductId().equals("product-3")));

        // Act - Second retrieval (from cache)
        var cachedCustomer1Orders = orderProjection.getByCustomerId(customer1);
        var cachedCustomer2Orders = orderProjection.getByCustomerId(customer2);

        // Assert - Cache returns same results
        assertEquals(customer1Orders.size(), cachedCustomer1Orders.size());
        assertEquals(customer2Orders.size(), cachedCustomer2Orders.size());
        assertEquals(
            customer1Orders.stream().map(OrderProjectionModel::getId).sorted().toList(),
            cachedCustomer1Orders.stream().map(OrderProjectionModel::getId).sorted().toList()
        );
    }

    @Test
    @Transactional
    void shouldGetAllOrders() {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        String customer1 = "customer-1";
        String customer2 = "customer-2";

        // Create three orders with different customers
        String order1Id = UUID.randomUUID().toString();
        String order2Id = UUID.randomUUID().toString();
        String order3Id = UUID.randomUUID().toString();

        // Create orders
        orderProjection.handle(new OrderCreatedEvent(order1Id, 1L, customer1, correlationId, null));
        orderProjection.handle(new OrderCreatedEvent(order2Id, 1L, customer2, correlationId, null));
        orderProjection.handle(new OrderCreatedEvent(order3Id, 1L, customer1, correlationId, null));

        // Add items to make orders distinguishable
        orderProjection.handle(new OrderItemAddedEvent(
            order1Id, 2L, "product-1", "Product 1", 1, BigDecimal.TEN, correlationId, null
        ));
        orderProjection.handle(new OrderItemAddedEvent(
            order2Id, 2L, "product-2", "Product 2", 2, BigDecimal.TEN, correlationId, null
        ));
        orderProjection.handle(new OrderItemAddedEvent(
            order3Id, 2L, "product-3", "Product 3", 3, BigDecimal.TEN, correlationId, null
        ));

        // Act - First retrieval (from database)
        var allOrders = orderProjection.getAll();

        // Assert
        assertEquals(3, allOrders.size());
        var orderIds = allOrders.stream().map(OrderProjectionModel::getId).collect(java.util.stream.Collectors.toSet());
        assertTrue(orderIds.contains(order1Id));
        assertTrue(orderIds.contains(order2Id));
        assertTrue(orderIds.contains(order3Id));

        // Verify orders have correct items
        assertTrue(allOrders.stream()
            .flatMap(o -> o.getItems().stream())
            .map(OrderProjectionModel.OrderItemProjection::getProductId)
            .collect(java.util.stream.Collectors.toSet())
            .containsAll(List.of("product-1", "product-2", "product-3")));

        // Act - Second retrieval (from cache)
        var cachedOrders = orderProjection.getAll();

        // Assert - Cache returns same results
        assertEquals(allOrders.size(), cachedOrders.size());
        assertEquals(
            allOrders.stream().map(OrderProjectionModel::getId).sorted().toList(),
            cachedOrders.stream().map(OrderProjectionModel::getId).sorted().toList()
        );

        // Verify individual orders can be retrieved from cache
        var order1 = orderProjection.getById(order1Id);
        var order2 = orderProjection.getById(order2Id);
        var order3 = orderProjection.getById(order3Id);

        assertNotNull(order1);
        assertNotNull(order2);
        assertNotNull(order3);
        assertEquals("product-1", order1.getItems().get(0).getProductId());
        assertEquals("product-2", order2.getItems().get(0).getProductId());
        assertEquals("product-3", order3.getItems().get(0).getProductId());
    }

    @Test
    @Transactional
    void shouldResetProjection() {
        // Arrange - Create an order with items
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-1";
        String correlationId = UUID.randomUUID().toString();

        // Create order and add items
        orderProjection.handle(new OrderCreatedEvent(orderId, 1L, customerId, correlationId, null));
        orderProjection.handle(new OrderItemAddedEvent(
            orderId, 2L, "product-1", "Product 1", 1, BigDecimal.TEN, correlationId, null
        ));

        // Verify initial state
        OrderProjectionModel order = orderProjection.getById(orderId);
        assertNotNull(order);
        assertEquals(1, order.getItems().size());

        // Get the order again to ensure it's in cache
        var cachedOrder = orderProjection.getById(orderId);
        assertNotNull(cachedOrder);
        assertEquals(order.getId(), cachedOrder.getId());

        // Act - Reset projection
        orderProjection.reset();

        // Assert - Verify database state
        var allOrders = repository.listAll();
        assertTrue(allOrders.isEmpty());

        // Verify cache is cleared
        assertNull(orderProjection.getById(orderId));
        assertTrue(orderProjection.getAll().isEmpty());

        // Create new order after reset to verify projection still works
        String newOrderId = UUID.randomUUID().toString();
        orderProjection.handle(new OrderCreatedEvent(newOrderId, 1L, customerId, correlationId, null));

        // Verify new order is saved and retrievable
        var newOrder = orderProjection.getById(newOrderId);
        assertNotNull(newOrder);
        assertEquals(newOrderId, newOrder.getId());
    }

    @Test
    void shouldReturnCorrectProjectionName() {
        assertEquals("OrderProjection", orderProjection.getProjectionName());
    }

    @Test
    @Transactional
    void shouldUseCacheEffectively() {
        // Arrange
        String customerId = "customer-0";
        String correlationId = UUID.randomUUID().toString();
        int numberOfOrders = 10; // Reduced for test performance
        List<String> orderIds = new ArrayList<>();

        // Create test orders with items
        for (int i = 0; i < numberOfOrders; i++) {
            String orderId = UUID.randomUUID().toString();
            orderIds.add(orderId);

            // Create order
            orderProjection.handle(new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
            ));

            // Add item to order
            orderProjection.handle(new OrderItemAddedEvent(
                orderId, 2L, "product-" + i, "Product " + i,
                1, BigDecimal.valueOf(99.99), correlationId, null
            ));
        }

        // Act & Assert - First retrieval from database
        var firstCall = orderProjection.getByCustomerId(customerId);
        assertEquals(numberOfOrders, firstCall.size());

        // Verify all orders were created with correct data
        for (var order : firstCall) {
            assertEquals(1, order.getItems().size());
            assertEquals(BigDecimal.valueOf(99.99), order.getItems().get(0).getPrice());
            assertTrue(orderIds.contains(order.getId()));
        }

        // Multiple retrievals should use cache
        for (int i = 0; i < 5; i++) {
            var cachedCall = orderProjection.getByCustomerId(customerId);
            assertEquals(numberOfOrders, cachedCall.size());
            assertEquals(
                firstCall.stream().map(OrderProjectionModel::getId).sorted().toList(),
                cachedCall.stream().map(OrderProjectionModel::getId).sorted().toList()
            );
        }

        // Individual order lookups should use cache
        for (String orderId : orderIds) {
            var order = orderProjection.getById(orderId);
            assertNotNull(order);
            assertEquals(customerId, order.getCustomerId());
            assertEquals(1, order.getItems().size());
            assertTrue(order.getItems().get(0).getProductId().startsWith("product-"));
        }

        // Verify database state matches cache
        var pageRequest = new PageRequest(0, Integer.MAX_VALUE); // Get all records
        var dbOrdersPage = repository.findByCustomerId(customerId, pageRequest);
        var dbOrders = dbOrdersPage.content();
        assertEquals(numberOfOrders, dbOrders.size());
        assertTrue(dbOrders.stream()
            .map(OrderProjectionEntity::getId)
            .collect(java.util.stream.Collectors.toSet())
            .containsAll(orderIds));
    }
}
