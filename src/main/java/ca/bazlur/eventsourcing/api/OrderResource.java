package ca.bazlur.eventsourcing.api;

import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.infrastructure.VirtualThreadEventStore;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    
    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);
    
    private final VirtualThreadEventStore eventStore;
    
    @Inject
    public OrderResource(VirtualThreadEventStore eventStore) {
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
    
    @POST
    @Path("/load-test")
    @WithSpan("api.load-test")
    public CompletableFuture<Response> loadTest(@QueryParam("count") @DefaultValue("1000") int count) {
        log.info("Starting load test with {} orders", count);
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                
                CompletableFuture<?>[] futures = IntStream.range(0, count)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            String orderId = UUID.randomUUID().toString();
                            String customerId = "customer-" + i;
                            String correlationId = UUID.randomUUID().toString();
                            
                            Order order = Order.create(orderId, customerId, correlationId);
                            order.addItem(
                                "product-" + orderId,
                                "Test Product",
                                1,
                                BigDecimal.valueOf(99.99),
                                correlationId
                            );
                            
                            // For demo, we'll simulate event storage without actual DB
                            Thread.sleep(java.time.Duration.ofMillis(1));
                            
                        } catch (Exception e) {
                            log.error("Failed to create order in load test", e);
                        }
                    }, executor))
                    .toArray(CompletableFuture[]::new);
                
                CompletableFuture.allOf(futures).join();
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            double throughput = (double) count / (duration / 1000.0);
            
            LoadTestResult result = new LoadTestResult(
                count,
                duration,
                throughput,
                "Load test completed successfully with Virtual Threads"
            );
            
            log.info("Load test completed: {} orders in {}ms ({:.2f} orders/sec)", 
                    count, duration, throughput);
            
            return Response.ok(result).build();
        });
    }
    
    public record CreateOrderRequest(String customerId) {}
    
    public record OrderResponse(String orderId, String customerId, String status) {}
    
    public record ErrorResponse(String message) {}
    
    public record LoadTestResult(
        int orderCount,
        long durationMs,
        double throughputPerSecond,
        String message
    ) {}
}