
public class CustomerOrder {

    private final int orderId;
    private final int customerId;
    private final String productName;
    private final int quantity;
    private final String customerName;
    private OrderStatus status;

    public CustomerOrder(int orderId, int customerId,
            String customerName, String productName, int quantity) {
        if (orderId <= 0 || customerId <= 0) {
            throw new IllegalArgumentException("IDs must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name cannot be blank"
            );
        }
        String normalizedProductName = productName.trim();
        if (!normalizedProductName.equals("Product A")
                && !normalizedProductName.equals("Product B")) {
            throw new IllegalArgumentException(
                    "Product not available."
            );
        }
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException(
                    "Customer name cannot be blank"
            );
        }

        this.orderId = orderId;
        this.customerId = customerId;
        this.productName = normalizedProductName;
        this.quantity = quantity;
        this.customerName = customerName.trim();
        this.status = OrderStatus.RECEIVED;
    }

    public void displayOrder() {
        System.out.println("Customer name: " + customerName);
        System.out.println("Order ID: " + orderId);
        System.out.println("Customer ID: " + customerId);
        System.out.println("Product: " + productName);
        System.out.println("Quantity: " + quantity);

    }

    public int getQuantity() {
        return quantity;
    }

    public String getProductName() {
        return productName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getCustomerId() {
        return customerId;
    }

    public int getOrderId() {
        return orderId;
    }

    public synchronized OrderStatus getStatus() {
        return status;
    }

    public synchronized boolean updateStatus(OrderStatus newStatus) {
        if (newStatus == null || newStatus == OrderStatus.CANCELLED
                || !canTransitionTo(newStatus)) {
            return false;
        }
        status = newStatus;
        return true;
    }

    synchronized boolean cancel() {
        if (!canTransitionTo(OrderStatus.CANCELLED)) {
            return false;
        }
        status = OrderStatus.CANCELLED;
        return true;
    }

    private boolean canTransitionTo(OrderStatus newStatus) {
        switch (status) {
            case RECEIVED:
                return newStatus == OrderStatus.PAYMENT_PENDING
                        || newStatus == OrderStatus.CANCELLED;
            case PAYMENT_PENDING:
                return newStatus == OrderStatus.PAID
                        || newStatus == OrderStatus.PAYMENT_FAILED
                        || newStatus == OrderStatus.CANCELLED;
            case PAID:
                return newStatus == OrderStatus.PROCESSING
                        || newStatus == OrderStatus.PAYMENT_FAILED
                        || newStatus == OrderStatus.CANCELLED;
            case PROCESSING:
                return newStatus == OrderStatus.READY_FOR_DELIVERY
                        || newStatus == OrderStatus.PAID
                        || newStatus == OrderStatus.CANCELLED;
            case READY_FOR_DELIVERY:
                return newStatus == OrderStatus.DISPATCHED
                        || newStatus == OrderStatus.PROCESSING
                        || newStatus == OrderStatus.CANCELLED;
            case DISPATCHED:
                return newStatus == OrderStatus.DELIVERED
                        || newStatus == OrderStatus.DELIVERY_FAILED;
            case DELIVERED:
            case DELIVERY_FAILED:
            case PAYMENT_FAILED:
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }
}
