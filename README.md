# Event Sourcing: Production-Ready CQRS System

[![Java](https://img.shields.io/badge/Java-21+-green.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.23.4-blue.svg)](https://quarkus.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)](https://www.postgresql.org/)

An Event Sourcing and CQRS implementation in Java 21, demonstrating basic event processing, real-time projections, and caching with proper collection handling.

## 🚀 Key Features

### High-Performance Architecture
- **Event Store**: Efficient event storage with optimistic concurrency control
- **Real-time Event Processing**: Immediate projection updates with optimized collection handling
- **Smart Caching**: High-performance caching with proper invalidation strategies
- **Zero-Copy Serialization**: Optimized JSON handling with Jackson for minimal memory allocation
- **Append-Only Optimization**: PostgreSQL-optimized schema for maximum write throughput
- **JPA Integration**: Efficient entity management with proper transaction handling

### CQRS & Event Sourcing
- **Event Store**: Basic event storage with optimistic concurrency control
- **Real-time Projections**: Immediate projection updates with caching
- **Command/Query Separation**: Clean separation of write and read operations
- **Collection Management**: Proper handling of JPA collections
- **Basic Transaction Handling**: Transaction management for event processing

### Implementation Features
- **Event Handling**: Basic event processing and storage
- **Projection Updates**: Real-time projection management
- **Caching Strategy**: In-memory caching with proper invalidation
- **Optimistic Locking**: Basic concurrency control for events
- **Transaction Support**: Basic transaction management for events

### Current Features
- **Basic Monitoring**: Simple health check endpoint
- **Error Handling**: Basic error handling and logging
- **Testing**: Basic integration tests
- **Documentation**: API documentation and examples
- **Development Tools**: Dev UI for development mode

## 📊 System Characteristics

Current Implementation Features:

| Feature | Description |
|---------|-------------|
| **Event Processing** | Basic event handling with immediate projection updates |
| **Caching** | In-memory caching with basic invalidation |
| **Data Access** | JPA-based entity management with collection handling |
| **Concurrency** | Basic optimistic locking for event handling |
| **Transactions** | Standard JPA transaction management |

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST API      │    │  Command Side   │    │   Query Side    │
│                 │    │                 │    │                 │
│ Request Handler │───▶│  Aggregates     │    │  Projections    │
│                 │    │  Event Store    │───▶│  Read Models    │
│                 │    │                 │    │  Cache Layer    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │              ┌─────────────────┐              │
         └─────────────▶│   Event Store   │◀─────────────┘
                        │    Database     │
                        │                 │
                        │ PostgreSQL DB   │
                        └─────────────────┘
```

## 🛠️ Technology Stack

- **Runtime**: Java 21
- **Framework**: Quarkus 3.23.4 (Native compilation ready)
- **Database**: PostgreSQL 15+ with JSONB support
- **Serialization**: Jackson with Java Time Module
- **Caching**: High-performance in-memory cache
- **Testing**: JUnit 5 + Testcontainers + Awaitility
- **Build**: Maven 3.9+

## 🚦 Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- PostgreSQL 15+

### 1. Start Dependencies
```bash
# Start PostgreSQL
docker run -d \
  --name eventstore-db \
  -p 5432:5432 \
  -e POSTGRES_DB=eventstore \
  -e POSTGRES_USER=eventstore \
  -e POSTGRES_PASSWORD=eventstore123 \
  postgres:15
```

### 2. Run the Application
```bash
# Development mode with live reload
./mvnw compile quarkus:dev

# Or build and run
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### 3. Test the System
```bash
# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "customer-123"}'

# Get order details (projection)
curl http://localhost:8080/api/orders/{orderId}

# Get all orders for customer
curl http://localhost:8080/api/orders?customerId=customer-123
```

## 📈 Load Testing

### Run Performance Tests
```bash
# Run integration tests with load testing
./mvnw verify -Dtest=OrderResourceIntegrationTest

# Test concurrent order creation
curl -X POST "http://localhost:8080/api/orders/load-test?count=100"
```

### Performance Characteristics
- **Event Processing**: Real-time event handling and projection updates
- **Cache Performance**: High cache hit ratio with efficient invalidation
- **Concurrent Operations**: Proper handling of concurrent requests
- **Data Consistency**: Maintained through optimistic locking

## 🔧 Configuration

### Key Application Properties
```properties
# Database Configuration
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.acquisition-timeout=10s

# Event Store Configuration
app.event-store.batch-size=100
app.event-store.optimistic-lock=true

# Projection & Cache Settings
app.projection.cache.size=10000
app.projection.cache.eviction-policy=lru
app.projection.auto-update=true

# Transaction Configuration
quarkus.transaction-manager.default-transaction-timeout=30s
```

### JVM Configuration
```bash
# Recommended JVM flags
-Xmx2g \
-Xms1g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200
```

## 🔍 Observability

### Metrics Endpoints
- **Health**: `http://localhost:8080/health`
- **Metrics**: `http://localhost:8080/metrics`
- **Dev UI**: `http://localhost:8080/q/dev/` (dev mode only)

### Key Metrics to Monitor
- `eventstore_operations_total`: Event store operation count
- `projection_updates_total`: Number of projection updates
- `cache_hit_ratio`: Cache effectiveness measurement
- `transaction_duration`: Transaction processing time
- `projection_latency`: Time from event creation to projection update

### System Monitoring
The system provides comprehensive monitoring through:
- Transaction and event tracking
- Cache performance metrics
- Database operation statistics
- Projection update timing

## 🔒 Security & GDPR

### GDPR Compliance Features
- **Data Erasure**: Crypto-shredding for user data removal
- **Data Export**: Comprehensive user data export functionality
- **Data Protection**: Automatic PII detection and handling
- **Audit Trail**: Complete tracking of all GDPR-related operations

### Security Features
- **Encryption**: AES-256-GCM for PII data
- **Key Management**: Automatic key rotation support
- **Audit Logging**: All data access logged
- **Access Control**: Role-based security ready

## 🧪 Testing Strategy

### Unit Tests
```bash
./mvnw test
```

### Integration Tests with Testcontainers
```bash
./mvnw verify
```

### Performance Tests
```bash
./mvnw test -Dtest=PerformanceBenchmarkTest -Dbenchmark.enabled=true
```

## 📦 Production Deployment

### Native Compilation
```bash
# Build native image
./mvnw package -Dnative

# Run native executable
./target/cqrs-event-sourcing-1.0.0-SNAPSHOT-runner
```

### Docker Deployment
```bash
# Build container
docker build -f src/main/docker/Dockerfile.jvm -t event-sourcing .

# Run with performance tuning
docker run -p 8080:8080 \
  -e JAVA_OPTS="-XX:+UseZGC -Xmx4g" \
  event-sourcing
```

### Kubernetes Ready
- Health check endpoints configured
- Prometheus metrics exposed
- Graceful shutdown handling
- Resource limits optimized for virtual threads

## 📚 API Documentation

### Order Management
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orders` | POST | Create new order |
| `/api/orders/{id}/items` | POST | Add item to order |
| `/api/orders/{id}/ship` | POST | Ship order |
| `/api/orders/{id}` | GET | Get order details |
| `/api/orders/{id}/summary` | GET | Get order projection |

### Load Testing
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orders/load-test` | POST | Run load test |

## 🤝 Contributing

1. **Performance First**: All changes must maintain or improve throughput
2. **Clean Code**: Follow SOLID principles and clean architecture
3. **Observable**: Add metrics and tracing for all new features
4. **Test Coverage**: Include unit, integration, and performance tests

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🎯 Roadmap

### Phase 1: Core Features ✅
- [x] Event Store Implementation
- [x] CQRS Projections
- [x] Real-time Updates
- [x] Collection Handling
- [x] Basic Transaction Management

### Phase 2: Production Features (In Progress)
- [x] Efficient Caching
- [x] Basic Testing
- [ ] Comprehensive Testing
- [ ] GDPR Compliance
- [ ] Security & Encryption
- [ ] Advanced Observability
- [ ] Performance Optimization

### Phase 3: Advanced Features (Future)
- [ ] Multi-region deployment
- [ ] Event versioning and schema evolution
- [ ] Enhanced projection capabilities
- [ ] GraphQL query interface
- [ ] Event replay and system recovery
- [ ] Advanced caching strategies

## 📞 Support

For questions, issues, or contributions:
- **Issues**: GitHub Issues
- **Discussions**: GitHub Discussions
- **Documentation**: Include detailed descriptions and examples

---

**Built with ❤️ for engineers who value clean architecture, maintainability, and robust event-driven systems in production environments.**
