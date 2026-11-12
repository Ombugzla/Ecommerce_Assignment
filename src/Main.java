
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("_________________ZAVHIRA NEXUS E-COMMERCE_________________\n");

        NotificationService notificationService = new NotificationService();
        DeliveryManager deliveryManager = new DeliveryManager(notificationService);

        // Creating customer orders 
        List<CustomerOrder> orders = new ArrayList<>();

        orders.add(new CustomerOrder(
                1, 101, "Larry Mutinda", "Product A", 2
        ));

        orders.add(new CustomerOrder(
                2, 102, "Sandra Chwanya", "Product B", 3
        ));

        orders.add(new CustomerOrder(
                3, 103, "Mary Ng'ang'a", "Product A", 1
        ));

        orders.add(new CustomerOrder(
                4, 104, "Becky Shania", "Product B", 2
        ));
        orders.add(new CustomerOrder(
                5, 105, "Dennis Gitonga", "Product A", 4
        ));

        // Simulate successful payment, packaging
        for (CustomerOrder order : orders) {

            advanceOrder(order, OrderStatus.PAYMENT_PENDING);
            advanceOrder(order, OrderStatus.PAID);
            advanceOrder(order, OrderStatus.PROCESSING);
            advanceOrder(order, OrderStatus.READY_FOR_DELIVERY);

            System.out.println(
                    "Order " + order.getOrderId() +
                    " is ready for delivery."
            );
        }
        System.out.println("\nStarting delivery operations...\n");
        // Submit all orders for delivery
        for (CustomerOrder order : orders) {

            deliveryManager.submitForDelivery(order);
        }
        // Wait for all delivery tasks to finish
        deliveryManager.shutdown();

        System.out.println("                FINAL DELIVERY REPORT");
        System.out.println("----------------------------------------------------------");

        int delivered = 0;
        int failed = 0;
        for (CustomerOrder order : orders) {

            System.out.println(
                    "Order " + order.getOrderId() +
                    " | Customer: " + order.getCustomerName() +
                    " | Status: " + order.getStatus()
            );

            if (order.getStatus() == OrderStatus.DELIVERED) {
                delivered++;
            } else {
                failed++;
            }
        }
        System.out.println("----------------------------------------------------------\n\n");
        System.out.println("Total orders: " + orders.size());
        System.out.println("Successfully delivered: " + delivered);
        System.out.println("Not delivered: " + failed);
        System.out.println("__________________________________________________________");

        System.out.println("\nSimulation completed.");
    }

    private static void advanceOrder(CustomerOrder order, OrderStatus status) {
        if (!order.updateStatus(status)) {
            throw new IllegalStateException(
                    "Cannot move Order " + order.getOrderId()
                            + " to " + status
            );
        }
    }
}
