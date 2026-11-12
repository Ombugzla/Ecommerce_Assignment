import java.util.HashMap;

public class InventoryManager {

    private HashMap<String, Integer> inventory;

    private HashMap<Integer, Reservation> reservations;

    public InventoryManager() {
        this(100, 50);
    }

    public InventoryManager(int productAStock, int productBStock) {
        if (productAStock < 0 || productBStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        inventory = new HashMap<>();
        reservations = new HashMap<>();

        inventory.put("Product A", productAStock);
        inventory.put("Product B", productBStock);
    }
    private enum ReservationStatus {
        RESERVED,
        RELEASED,
        CONSUMED,
    }
    private static class Reservation {
        private String productName;
        private int quantity;
        private ReservationStatus status;

        public Reservation(String productName, int quantity){
            this.productName = productName;
            this.quantity = quantity;
            this.status = ReservationStatus.RESERVED;
        }
    }
    public synchronized int getAvailableStock(String productName){

        if (productName == null || !inventory.containsKey(productName)){
            return -1;
        }
        return inventory.get(productName);
    }
    public synchronized boolean reserveStock(CustomerOrder order){
        if (order == null || order.getStatus() != OrderStatus.RECEIVED) {
            return false;
        }

        int orderId = order.getOrderId();
        String productName = order.getProductName();
        int quantity = order.getQuantity();

        if (productName == null || productName.trim().isEmpty() || quantity <= 0) {
            return false;
        }

        if (reservations.containsKey(orderId)) {
            return false;
        }

        if(!inventory.containsKey(productName)) {
            return false;
        }

        int availableStock = inventory.get(productName);

        if (availableStock < quantity) {
            return false;
        }

        inventory.put(productName,availableStock - quantity);

        reservations.put(orderId, new Reservation(productName, quantity));
             return true;
    }
    public synchronized boolean releaseStock(CustomerOrder order) {
        if (order == null) {
            return false;
        }
        int orderId = order.getOrderId();
        Reservation reservation = reservations.get(orderId);
        if (reservation == null || reservation.status != ReservationStatus.RESERVED) {
            return false;
        }
        int currentStock = inventory.get(reservation.productName);
        inventory.put(reservation.productName, currentStock + reservation.quantity);
        reservation.status = ReservationStatus.RELEASED;

            return true;

    }

    public synchronized boolean cancelOrder(CustomerOrder order) {
        if (order == null || !order.cancel()) {
            return false;
        }
        releaseStock(order);
        return true;
    }

    public synchronized boolean consumeStock(CustomerOrder order) {
        if (order == null) {
            return false;
        }
        int orderId = order.getOrderId();
        Reservation reservation = reservations.get(orderId);
        if (reservation == null || reservation.status != ReservationStatus.RESERVED) {
            return false;
        }
        reservation.status = ReservationStatus.CONSUMED;
        return true;
    }
}
