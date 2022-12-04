package org.example;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.example.Level2View.Side.ASK;


/**
 * An order book is the list of orders that a trading venue (in particular stock exchanges) uses to record the interest
 * of buyers and sellers in a particular financial instrument (symbol). A matching engine uses the book to determine
 * which orders can be fully or partially executed.
 *
 * @author Aleksandar Spasojevic
 */
public class OrderBook implements Level2View {
    private final String exchange;
    private final String symbol;
    private final NavigableMap<BigDecimal, Long> bids = new TreeMap<>();
    private final NavigableMap<BigDecimal, Long> asks = new TreeMap<>();
    private final Map<Long, Order> orders = new HashMap<>();

    /**
     * Constructs an empty order book for symbol on specified exchange.
     *
     * @param exchange trading venue
     * @param symbol   financial instrument
     */
    public OrderBook(String exchange, String symbol) {
        this.exchange = exchange;
        this.symbol = symbol;
    }

    /**
     * Returns exchange's name
     *
     * @return exchange's name
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * Returns financial instrument's symbol on exchange
     *
     * @return financial instrument's symbol on exchange
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Act on when new order has arrived
     *
     * @param side     {@code BID} or {@code ASK} {@link Level2View.Side}
     * @param price    of order
     * @param quantity of order
     * @param orderId  of order
     * @throws RuntimeException if invalid input for orderId, price, quantity or if order is present in order book
     */
    @Override
    public void onNewOrder(Side side, BigDecimal price, long quantity, long orderId) {
        if (orders.containsKey(orderId))
            throw new RuntimeException("Order with orderId=" + orderId + " already exists");

        var order = new Order(side, orderId, price, quantity);
        orders.put(orderId, order);
        add(order);
    }

    /**
     * Act on when order has to be cancelled
     *
     * @param orderId of order
     * @throws RuntimeException if order not present in order book or if order is not active or if removing order from
     *                          order book not successful (should not happen)
     */
    @Override
    public void onCancelOrder(long orderId) {
        if (!orders.containsKey(orderId))
            throw new RuntimeException("No order with orderId=" + orderId);

        Order order = orders.get(orderId);

        if (!order.isActive())
            throw new RuntimeException("cancel on inactive order not allowed");

        remove(order);
        order.cancel();
    }

    /**
     * Act on when order has to be replaced. No change in orderId.
     *
     * @param price    of changed order (new price due to replace). Causes change in price level of order book
     * @param quantity of changed order (new quantity due to replace)
     * @param orderId  of order to be replaced
     * @throws RuntimeException if invalid input for orderId, price, quantity or if order not present in order book or
     *                          if order is not active or if removing order from order book not successful (should not
     *                          happen)
     */
    @Override
    public void onReplaceOrder(BigDecimal price, long quantity, long orderId) {
        Order.validate(orderId, price, quantity);

        if (!orders.containsKey(orderId))
            throw new RuntimeException("No order with orderId=" + orderId);

        var order = orders.get(orderId);

        if (!order.isActive())
            throw new RuntimeException("replace on inactive order not allowed");

        remove(order);
        order.setQuantity(quantity);
        order.setPrice(price);
        add(order);
    }

    /**
     * Act on matched order (trade)
     *
     * @param quantity       to deduct from order (to be filled)
     * @param restingOrderId of order that has been crossed
     * @throws IllegalArgumentException if order's quantity &lt; quantity to be filled or if quantity &le; 0 or
     * @throws RuntimeException         if order not present in order book or if order is not active
     *                                  or if quantity &gt; order's quantity
     */
    @Override
    public void onTrade(long quantity, long restingOrderId) {
        if (quantity <= 0)
            throw new IllegalArgumentException("quantity must be greater than 0");

        if (!orders.containsKey(restingOrderId))
            throw new RuntimeException("No restingOrder with orderId=" + restingOrderId);

        var order = orders.get(restingOrderId);

        if (!order.isActive())
            throw new RuntimeException("fill inactive order not allowed");

        if (quantity > order.getQuantity())
            throw new IllegalArgumentException("cannot fill order due to quantity > order's quantity");

        remove(order);
        var leftover = order.getQuantity() - quantity;
        order.setQuantity(leftover);
        if (leftover > 0) add(order); // partial fill
    }

    /**
     * Quantity of price level
     *
     * @param side  {@code BID} or {@code ASK} {@link Level2View.Side}
     * @param price level
     * @return quantity of price level
     */
    @Override
    public long getSizeForPriceLevel(Side side, BigDecimal price) {
        var bidsOrAsks = side == ASK ? asks.headMap(price, true) : bids.tailMap(price, true);
        return bidsOrAsks.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * Get number of price levels available
     *
     * @param side {@code BID} or {@code ASK} {@link Level2View.Side}
     * @return number (count) of price levels available
     * @implNote If BID/ASK contains more than Integer.MAX_VALUE elements, returns Integer.MAX_VALUE
     */
    @Override
    public long getBookDepth(Side side) {
        var bidsOrAsks = side == ASK ? asks : bids;
        return bidsOrAsks.size();
    }

    /**
     * Get highest {@code BID} or lowest {@code ASK}
     *
     * @param side {@code BID} or {@code ASK} {@link Level2View.Side}
     * @return either highest {@code BID} or lowest {@code ASK}
     */
    @Override
    public BigDecimal getTopOfBook(Side side) {
        return switch (side) {
            case BID -> bids.isEmpty() ? null : bids.lastKey();
            case ASK -> asks.isEmpty() ? null : asks.firstKey();
        };
    }

    private void add(Order order) {
        var bidsOrAsks = order.getSide() == ASK ? asks : bids;
        bidsOrAsks.merge(order.getPrice(), order.getQuantity(), Long::sum);
    }

    private void remove(Order order) {
        var bidsOrAsks = order.getSide() == ASK ? asks : bids;
        bidsOrAsks.compute(order.getPrice(), (priceLevel, currentQuantity) -> {
            if (currentQuantity == null || currentQuantity < order.getQuantity())
                throw new RuntimeException("cannot remove order from Order book");
            var newQuantity = currentQuantity - order.getQuantity();
            return newQuantity > 0 ? newQuantity : null;
        });
    }
}
