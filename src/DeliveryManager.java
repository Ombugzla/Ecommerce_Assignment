
import java.util.Set;
import java.util.concurrent.*;

public class DeliveryManager {

    private static final int VEHICLE_COUNT = 4;
    private final ExecutorService deliveryPool =
            Executors.newFixedThreadPool(VEHICLE_COUNT);
    private final Semaphore vehicleSemaphore =
            new Semaphore(VEHICLE_COUNT);

    private final BlockingQueue<DeliveryVehicle> availableVehicles =
            new ArrayBlockingQueue<>(VEHICLE_COUNT);

    private final Set<Integer> scheduledOrders =
            ConcurrentHashMap.newKeySet();
    private final NotificationService notificationService;

    public DeliveryManager(NotificationService notificationService) {
        if (notificationService == null) {
            throw new IllegalArgumentException(
                    "Notification service cannot be null"
            );
        }
        this.notificationService = notificationService;
        for (int i = 1; i <= VEHICLE_COUNT; i++) {
            availableVehicles.add(new DeliveryVehicle(i));
        }
    }
    public boolean submitForDelivery(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException(
                    "Order cannot be null."
            );
        }
        if (order.getStatus() != OrderStatus.READY_FOR_DELIVERY) {

            System.out.println(
                "Order " + order.getOrderId() +
                " is not ready for delivery."
            );

            return false;
        }
        if (!scheduledOrders.add(order.getOrderId())) {
            System.out.println(
                "Order " + order.getOrderId() +
                " has already been scheduled."
            );

            return false;
        }
        try {

            deliveryPool.submit(() -> processDelivery(order));

            return true;

        } catch (RejectedExecutionException e) {

            scheduledOrders.remove(order.getOrderId());

            System.err.println(
                "Could not schedule Order " + order.getOrderId()
            );

            return false;
        }
    }
    private void processDelivery(CustomerOrder order) {

        DeliveryVehicle vehicle = null;

        boolean permitAcquired = false;

        try {
            vehicleSemaphore.acquire();

            permitAcquired = true;
            vehicle = availableVehicles.take();

            if (order.getStatus() != OrderStatus.READY_FOR_DELIVERY) {

                System.out.println(
                    "Order " + order.getOrderId() +
                    " is no longer ready for delivery."
                );

                return;
            }
            if (!order.updateStatus(OrderStatus.DISPATCHED)) {
                return;
            }

            System.out.println(
                "Order " + order.getOrderId() +
                " dispatched using Vehicle " +
                vehicle.getVehicleId()
            );
            vehicle.deliver(order);
            if (order.updateStatus(OrderStatus.DELIVERED)) {
                try {
                    notificationService.sendDeliveryNotification(order);
                } catch (RuntimeException e) {
                    System.err.println(
                        "Could not notify customer for Order " +
                        order.getOrderId() + ": " + e.getMessage()
                    );
                }
            }
        } catch (InterruptedException e) {
            order.updateStatus(OrderStatus.DELIVERY_FAILED);
            Thread.currentThread().interrupt();
            System.err.println(
                "Delivery interrupted for Order " +
                order.getOrderId()
            );

            if (order.getStatus() == OrderStatus.READY_FOR_DELIVERY) {

                scheduledOrders.remove(order.getOrderId());
            }
        } catch (RuntimeException e) {
            if (order.getStatus() == OrderStatus.DISPATCHED) {
                order.updateStatus(OrderStatus.DELIVERY_FAILED);
            }
            System.err.println(
                "Delivery failed for Order " +
                order.getOrderId() + ": " + e.getMessage()
            );

        } finally {

            if (vehicle != null) {

                availableVehicles.offer(vehicle);
            }
            if (permitAcquired) {

                vehicleSemaphore.release();
            }
        }
    }
    public void shutdown() {
        deliveryPool.shutdown();
        try {

            if (!deliveryPool.awaitTermination(
                    60, TimeUnit.SECONDS)) {

                System.out.println(
                    "Delivery shutdown timed out. " +
                    "Requesting remaining tasks to stop."
                );

                deliveryPool.shutdownNow();
            }

        } catch (InterruptedException e) {

            deliveryPool.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }
}
