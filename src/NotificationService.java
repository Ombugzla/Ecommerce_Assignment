
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {

    private final Set<Integer> notifiedOrders =
            ConcurrentHashMap.newKeySet();


    public void sendDeliveryNotification(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        int orderId = order.getOrderId();

        if (notifiedOrders.add(orderId)) {
            String message = "\n______________________NOTIFICATION________________________\n\n"
                    + "Dear " + order.getCustomerName() + ",\n"
                    + "Your order #" + orderId
                    + " has been successfully delivered.\n"
                    + "Product: " + order.getProductName() + "\n"
                    + "Quantity: " + order.getQuantity() + "\n"
                    + "Thank you for shopping with us!\u2764\uFE0F\n"
                    + "__________________________________________________________\n";
            System.out.println(message);

        } else {

            System.out.println(
                "Notification already sent for Order " + orderId
            );
        }
    }
}
