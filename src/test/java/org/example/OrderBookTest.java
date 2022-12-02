package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.math.BigDecimal;
import java.util.Random;

import static org.example.Level2View.Side.ASK;
import static org.example.Level2View.Side.BID;
import static org.junit.jupiter.api.Assertions.*;


class OrderBookTest {

    OrderBook ob;

    private BigDecimal bd(double i) {
        return new BigDecimal(i);
    }

    @BeforeEach
    void initOrderBook() {
        ob = new OrderBook("SIX", "AAPL");
    }

    @Test
    void testGetter() {
        assertEquals("SIX", ob.getExchange());
        assertEquals("AAPL", ob.getSymbol());
    }

    @Test
    void onEmptyOrderBook() {
        assertThrows(RuntimeException.class, () -> ob.onReplaceOrder(bd(88), 12, 2));
        assertThrows(RuntimeException.class, () -> ob.onCancelOrder(2));
        assertThrows(RuntimeException.class, () -> ob.onTrade(77, 2));

        assertEquals(0, ob.getBookDepth(ASK));
        assertNull(ob.getTopOfBook(ASK));
        assertEquals(0, ob.getSizeForPriceLevel(ASK, bd(99)));
        assertEquals(0, ob.getSizeForPriceLevel(ASK, bd(-1)));

        assertEquals(0, ob.getBookDepth(BID));
        assertNull(ob.getTopOfBook(BID));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(99)));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(-1)));
    }

    @Test
    void onNewOrder() {
        ob.onNewOrder(ASK, bd(99), 34, 1);

        // ASK
        assertEquals(1, ob.getBookDepth(ASK));
        assertEquals(34, ob.getSizeForPriceLevel(ASK, bd(99)));
        assertEquals(bd(99), ob.getTopOfBook(ASK));

        // BID
        assertEquals(0, ob.getBookDepth(BID));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(99)));
        assertNull(ob.getTopOfBook(BID));

        // new order with same id
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(BID, bd(96), 50, 1));
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(ASK, bd(96), 50, 1));
    }

    @Test
    void newOrderWithInvalidIdPriceQuantity() {
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(ASK, bd(91), -5, 3));
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(ASK, bd(-999), 5, 3));
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(ASK, bd(-999), -5, 3));
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(ASK, bd(999), 5, -3));
    }

    @Test
    void onCancelOrder() {
        ob.onNewOrder(ASK, bd(99), 34, 1);
        ob.onCancelOrder(1);

        // cancelling again results in exception due to inactive status
        assertThrows(RuntimeException.class, () -> ob.onCancelOrder(1));

        assertEquals(0, ob.getBookDepth(ASK));
        assertEquals(0, ob.getSizeForPriceLevel(ASK, bd(99)));
        assertNull(ob.getTopOfBook(ASK));

        assertEquals(0, ob.getBookDepth(BID));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(99)));
        assertNull(ob.getTopOfBook(BID));

        // add new order with same id after cancel
        assertThrows(RuntimeException.class, () -> ob.onNewOrder(BID, bd(33), 11, 1));
        assertThrows(RuntimeException.class, () -> ob.onReplaceOrder(bd(33), 4444, 1));
        assertThrows(RuntimeException.class, () -> ob.onTrade(3333, 1));
    }

    @Test
    void onReplaceOrder() {
        ob.onNewOrder(BID, bd(99), 34, 1);
        ob.onReplaceOrder(bd(99), 25, 1);

        assertEquals(1, ob.getBookDepth(BID));
        assertEquals(25, ob.getSizeForPriceLevel(BID, bd(99))); // new quantity
        assertEquals(new BigDecimal(99), ob.getTopOfBook(BID));

        // replace on new price-level and quantity
        ob.onReplaceOrder(bd(95), 3, 1);
        assertEquals(1, ob.getBookDepth(BID));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(99)));
        assertEquals(3, ob.getSizeForPriceLevel(BID, bd(95))); // new quantity
        assertEquals(3, ob.getSizeForPriceLevel(BID, bd(94))); // new quantity
        assertEquals(new BigDecimal(95), ob.getTopOfBook(BID));

        // try replacing order with invalid price/quantity
        assertThrows(RuntimeException.class, () -> ob.onReplaceOrder(bd(96), -555, 1));
        assertThrows(RuntimeException.class, () -> ob.onReplaceOrder(bd(96), 5, -1));
        assertThrows(RuntimeException.class, () -> ob.onReplaceOrder(bd(-46), 5, 1));
    }

    @Test
    void onTrade() {
        ob.onNewOrder(ASK, bd(94), 13, 1);
        ob.onTrade(4, 1); // partial fill

        assertEquals(1, ob.getBookDepth(ASK));
        assertEquals(13 - 4, ob.getSizeForPriceLevel(ASK, bd(94))); // new quantity
        assertEquals(bd(94), ob.getTopOfBook(ASK));

        //try to fill more than order's quantity
        assertThrows(RuntimeException.class, () -> ob.onTrade(99, 1));

        ob.onTrade(13 - 4, 1); // rest filled

        assertEquals(0, ob.getBookDepth(ASK));
        assertEquals(0, ob.getSizeForPriceLevel(ASK, bd(94))); // new quantity
        assertNull(ob.getTopOfBook(ASK));

        // try trade with filled order
        assertThrows(RuntimeException.class, () -> ob.onTrade(-5, 1));

        // try trade with invalid quantity
        ob.onNewOrder(ASK, bd(90), 19, 2);
        assertThrows(RuntimeException.class, () -> ob.onTrade(-5, 2));
    }

    @Test
    void getSizeForPriceLevel() { // test when we cancel some/all orders
        ob.onNewOrder(ASK, bd(94), 13, 1);
        ob.onNewOrder(ASK, bd(95), 23, 2);
        ob.onNewOrder(ASK, bd(96), 43, 3);
        ob.onNewOrder(ASK, bd(97), 53, 4);

        assertEquals(13 + 23 + 43 + 53, ob.getSizeForPriceLevel(ASK, bd(100)));
        assertEquals(13 + 23 + 43, ob.getSizeForPriceLevel(ASK, bd(96.5)));
        assertEquals(13 + 23, ob.getSizeForPriceLevel(ASK, bd(95.5)));
        assertEquals(13, ob.getSizeForPriceLevel(ASK, bd(94.5)));
        assertEquals(0, ob.getSizeForPriceLevel(ASK, bd(91)));

        ob.onNewOrder(BID, bd(94), 13, 81);
        ob.onNewOrder(BID, bd(94), 13, 811); // duplicate
        ob.onNewOrder(BID, bd(95), 23, 82);
        ob.onNewOrder(BID, bd(96), 43, 83);
        ob.onNewOrder(BID, bd(96), 43, 833); // duplicate
        ob.onNewOrder(BID, bd(96), 43, 834); // duplicate
        ob.onNewOrder(BID, bd(97), 53, 84);

        assertEquals(2 * 13 + 23 + 3 * 43 + 53, ob.getSizeForPriceLevel(BID, bd(91)));
        assertEquals(23 + 3 * 43 + 53, ob.getSizeForPriceLevel(BID, bd(94.5)));
        assertEquals(3 * 43 + 53, ob.getSizeForPriceLevel(BID, bd(95.5)));
        assertEquals(53, ob.getSizeForPriceLevel(BID, bd(96.5)));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(100)));

        // let's cancel/replace/trade some orders
        ob.onCancelOrder(81); // cancel worst Bid
        ob.onCancelOrder(834); // cancel on price level where more than one active order
        ob.onReplaceOrder(bd(95.5), 33, 82); // replace order with new quantity and price
        ob.onNewOrder(BID, bd(95.5), 33, 944); // add new order
        ob.onTrade(18, 84); // cross order book (partial fill)

        assertEquals(13 + 2 * 33 + 2 * 43 + 35, ob.getSizeForPriceLevel(BID, bd(91)));
        assertEquals(2 * 33 + 2 * 43 + 35, ob.getSizeForPriceLevel(BID, bd(94.5)));
        assertEquals(2 * 43 + 35, ob.getSizeForPriceLevel(BID, bd(95.8)));
        assertEquals(35, ob.getSizeForPriceLevel(BID, bd(96.8)));
        assertEquals(0, ob.getSizeForPriceLevel(BID, bd(100)));
    }

    @Test
    void getBookDepth() { // test when we cancel all orders
        ob.onNewOrder(ASK, bd(94), 13, 1);
        ob.onNewOrder(ASK, bd(95), 23, 2);
        ob.onNewOrder(ASK, bd(96), 43, 3);
        ob.onNewOrder(ASK, bd(97), 53, 4);

        assertEquals(4, ob.getBookDepth(ASK));
        assertEquals(0, ob.getBookDepth(BID));

        ob.onNewOrder(BID, bd(94), 13, 81);
        ob.onNewOrder(BID, bd(94), 13, 811); // duplicate
        ob.onNewOrder(BID, bd(95), 23, 82);
        ob.onNewOrder(BID, bd(96), 43, 83);
        ob.onNewOrder(BID, bd(96), 43, 833); // duplicate
        ob.onNewOrder(BID, bd(96), 43, 834); // duplicate
        ob.onNewOrder(BID, bd(97), 53, 84);
        ob.onNewOrder(BID, bd(98), 63, 89);

        assertEquals(5, ob.getBookDepth(BID));
    }

    @Test
    void getTopOfBook() { // test when we cancel all orders
        ob.onNewOrder(ASK, bd(94), 13, 1);
        ob.onNewOrder(ASK, bd(95), 23, 2);
        ob.onNewOrder(ASK, bd(96), 43, 3);
        ob.onNewOrder(ASK, bd(97), 53, 4);

        assertEquals(bd(94), ob.getTopOfBook(ASK));
        assertNull(ob.getTopOfBook(BID));

        ob.onNewOrder(BID, bd(94), 13, 81);
        ob.onNewOrder(BID, bd(94), 13, 811); // duplicate
        ob.onNewOrder(BID, bd(95), 23, 82);
        ob.onNewOrder(BID, bd(96), 43, 83);
        ob.onNewOrder(BID, bd(96), 43, 833); // duplicate
        ob.onNewOrder(BID, bd(96), 43, 834); // duplicate
        ob.onNewOrder(BID, bd(97), 53, 84);
        ob.onNewOrder(BID, bd(98), 63, 89);

        assertEquals(bd(98), ob.getTopOfBook(BID));
    }

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    class Performance {

        static OrderBook ob;

        @BeforeAll
        static void initOrderBook() {
            ob = new OrderBook("SIX", "AAPL");
        }

        @Test
        @Order(1)
        void performInsertion1MillionOrders() {
            Random random = new Random();

            long newOrderId = 1;
            // BID
            for (; newOrderId <= 500_000; newOrderId++) {
                BigDecimal newPrice = BigDecimal.valueOf(random.nextDouble() * 150);
                long newQuantity = random.nextLong(0, 10000);
                ob.onNewOrder(BID, newPrice, newQuantity, newOrderId);
            }

            // ASK
            for (; newOrderId <= 1_000_000; newOrderId++) {
                BigDecimal newPrice = (BigDecimal.valueOf(random.nextDouble() * 150)).add(new BigDecimal(155));
                long newQuantity = random.nextLong(0, 10000);
                ob.onNewOrder(ASK, newPrice, newQuantity, newOrderId);
            }
        }

        @RepeatedTest(5)
        @Order(2)
        void performBookDepthASK() {
            ob.getBookDepth(ASK);
        }

        @RepeatedTest(5)
        @Order(3)
        void performBookDepthBID() {
            ob.getBookDepth(BID);
        }

        @RepeatedTest(5)
        @Order(4)
        void performTopOfBookASK() {
            ob.getTopOfBook(ASK);
        }

        @RepeatedTest(5)
        @Order(5)
        void performTopOfBookBID() {
            ob.getTopOfBook(BID);
        }

        @RepeatedTest(5)
        @Order(6)
        void performSizeForPriceLevelWorstASK() {
            ob.getSizeForPriceLevel(ASK, new BigDecimal(305));
        }

        @RepeatedTest(5)
        @Order(7)
        void performSizeForPriceLevelWorstBID() {
            ob.getSizeForPriceLevel(BID, new BigDecimal(1));
        }
    }

}