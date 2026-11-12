
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {

    private final Set<String> sentNotifications =
            ConcurrentHashMap.newKeySet();

    public void sendDeliveryNotification(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        int orderId = order.getOrderId();

        sendOnce(order, "DELIVERED",
                "Your order #" + orderId
                        + " has been successfully delivered.\n");
    }

    public void sendDispatchedNotification(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        sendOnce(order, "DISPATCHED",
                "Your order #" + order.getOrderId()
                        + " has been dispatched and is on its way.\n");
    }

    public void sendOutOfStockNotification(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        sendOnce(order, "OUT_OF_STOCK",
                "Sorry, " + order.getProductName()
                        + " is currently out of stock, so we couldn't fulfill "
                        + "your order #" + order.getOrderId() + ".\n"
                        + "Please try again later.\n");
    }

    public void sendPaymentFailedNotification(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        sendOnce(order, "PAYMENT_FAILED",
                "We couldn't process payment for order #"
                        + order.getOrderId() + ". Please try again.\n");
    }

    public void sendCancellationNotification(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        sendOnce(order, "CANCELLED",
                "Your order #" + order.getOrderId()
                        + " has been cancelled.\n");
    }

    private void sendOnce(CustomerOrder order, String event, String details) {
        String notificationKey = order.getOrderId() + ":" + event;
        if (!sentNotifications.add(notificationKey)) {
            return;
        }

        String closing = event.equals("DELIVERED")
                ? "Thank you for shopping with us!\n"
                : "";
        List<String> lines = new ArrayList<>();
        lines.add("Dear " + order.getCustomerName() + ",");
        lines.addAll(List.of(details.split("\\R")));
        lines.add("Product: " + order.getProductName());
        lines.add("Quantity: " + order.getQuantity());
        if (!closing.isEmpty()) {
            lines.add(closing.strip());
        }

        int width = "NOTIFICATION".length();
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        String separator = "_".repeat(width);
        String message = System.lineSeparator()
                + centeredBanner("NOTIFICATION", width)
                + System.lineSeparator() + System.lineSeparator()
                + String.join(System.lineSeparator(), lines)
                + System.lineSeparator() + separator;
        synchronized (System.out) {
            System.out.println(message);
        }
    }

    private String centeredBanner(String title, int width) {
        int remaining = width - title.length();
        int left = remaining / 2;
        int right = remaining - left;
        return "_".repeat(left) + title + "_".repeat(right);
    }
}
