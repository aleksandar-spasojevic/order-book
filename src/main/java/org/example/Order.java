package org.example;

import org.example.Level2View.Side;

import java.math.BigDecimal;

final class Order {
    private final Side side;
    private long id;
    private BigDecimal price;
    private long quantity;
    private Status status = Status.ACTIVE;

    Order(Side side, long orderId, BigDecimal price, long quantity) {
        this.side = side;

        setId(orderId);
        setPrice(price);
        setQuantity(quantity);
    }

    static void validate(long orderId, BigDecimal price, long quantity) {
        validateId(orderId);
        validatePrice(price);
        validateQuantity(quantity);
    }

    Side getSide() {
        return side;
    }

    long getId() {
        return id;
    }

    private void setId(long orderId) {
        validateId(orderId);
        this.id = orderId;
    }

    private static void validateId(long orderId) {
        if (orderId < 1) throw new IllegalArgumentException("id < 1");
    }

    BigDecimal getPrice() {
        return price; // BigDecimal is immutable
    }

    void setPrice(BigDecimal price) {
        validatePrice(price);
        this.price = price;
    }

    private static void validatePrice(BigDecimal price) {
        if (price.signum() < 1) throw new IllegalArgumentException("price <= 0");
    }

    long getQuantity() {
        return quantity;
    }

    void setQuantity(long quantity) {
        validateQuantity(quantity);
        if (quantity == 0) status = Status.INACTIVE;
        this.quantity = quantity;
    }

    private static void validateQuantity(long quantity){
        if (quantity < 0) throw new IllegalArgumentException("quantity < 0");
    }

    void cancel() {
        status = Status.INACTIVE;
    }

    boolean isActive() {
        return status == Status.ACTIVE;
    }

    private enum Status {
        ACTIVE, INACTIVE
    }
}