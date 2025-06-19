package ca.bazlur.eventsourcing;

import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    
    @Test
    void shouldCreateOrderWithCorrectInitialState() {
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();
        
        Order order = Order.create(orderId, customerId, correlationId);
        
        assertEquals(orderId, order.getId());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertEquals(1, order.getUncommittedEvents().size());
    }
    
    @Test
    void shouldAddItemAndUpdateState() {
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();
        
        Order order = Order.create(orderId, customerId, correlationId);
        order.addItem("product-1", "Test Product", 2, BigDecimal.valueOf(50.00), correlationId);
        
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(1, order.getItems().size());
        assertEquals(BigDecimal.valueOf(100.00), order.getTotalAmount());
        assertEquals(2, order.getUncommittedEvents().size());
    }
    
    @Test
    void shouldCreateMillionOrdersQuickly() {
        int numberOfOrders = 1_000_000;
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfOrders; i++) {
            String orderId = "order-" + i;
            String customerId = "customer-" + i;
            String correlationId = UUID.randomUUID().toString();
            
            Order order = Order.create(orderId, customerId, correlationId);
            order.addItem("product-" + i, "Product " + i, 1, BigDecimal.valueOf(99.99), correlationId);
            
            assertNotNull(order);
            assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double throughput = (double) numberOfOrders / (duration / 1000.0);
        
        System.out.printf("Created %,d orders in %,d ms (%.2f orders/sec)%n", 
                         numberOfOrders, duration, throughput);
        
        assertTrue(throughput > 10_000, "Should create more than 10k orders/sec");
    }
}