package ca.bazlur.eventsourcing.domain.order;

import java.math.BigDecimal;

public class OrderItem {
    private final String productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal price;
    
    public OrderItem(String productId, String productName, int quantity, BigDecimal price) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
    }
    
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
}