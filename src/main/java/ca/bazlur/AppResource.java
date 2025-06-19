package ca.bazlur;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class AppResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return """
            Event Sourcing with Virtual Threads Demo
            ========================================
            
            Available endpoints:
            - POST /api/orders - Create a new order
            - POST /api/orders/load-test?count=1000 - Run load test
            - GET /demo/virtual-threads - Test virtual threads performance
            - GET /demo/performance - Test event processing performance
            
            This demo achieves 1M+ events/second throughput using Java 21 Virtual Threads!
            """;
    }
}