
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class DeliveryManager {

    private static final int VEHICLE_COUNT = 4;
    private static final DeliveryRequest STOP_REQUEST =
            new DeliveryRequest(null);
    private final ExecutorService deliveryPool =
            Executors.newFixedThreadPool(VEHICLE_COUNT);
    private final BlockingQueue<DeliveryRequest> deliveryQueue =
            new LinkedBlockingQueue<>();
    private final Semaphore vehicleSemaphore =
            new Semaphore(VEHICLE_COUNT, true);
    private final AtomicIntegerArray vehicleTrips =
            new AtomicIntegerArray(VEHICLE_COUNT);
    private final Set<Integer> scheduledOrders =
            ConcurrentHashMap.newKeySet();
    private final NotificationService notificationService;
    private final InventoryManager inventoryManager;
    private boolean acceptingDeliveries = true;
    private boolean shutdownStarted;

    public DeliveryManager(NotificationService notificationService) {
        this(notificationService, null);
    }

    public DeliveryManager(
            NotificationService notificationService,
            InventoryManager inventoryManager) {
        if (notificationService == null) {
            throw new IllegalArgumentException(
                    "Notification service is required"
            );
        }
        this.notificationService = notificationService;
        this.inventoryManager = inventoryManager;
        for (int i = 1; i <= VEHICLE_COUNT; i++) {
            DeliveryVehicle vehicle = new DeliveryVehicle(i);
            deliveryPool.submit(() -> deliveryWorker(vehicle));
        }
    }

    public synchronized boolean submitForDelivery(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException(
                    "Order cannot be null."
            );
        }
        if (!acceptingDeliveries) {
            System.out.println("Delivery manager is shutting down.");
            return false;
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
        deliveryQueue.offer(new DeliveryRequest(order));
        return true;
    }

    private void deliveryWorker(DeliveryVehicle vehicle) {
        try {
            while (true) {
                DeliveryRequest request = deliveryQueue.take();
                if (request == STOP_REQUEST) {
                    return;
                }
                processDelivery(request.order, vehicle);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processDelivery(CustomerOrder order, DeliveryVehicle vehicle) {
        boolean permitAcquired = false;
        try {
            vehicleSemaphore.acquire();
            permitAcquired = true;

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
            if (inventoryManager != null
                    && !inventoryManager.consumeStock(order)) {
                order.updateStatus(OrderStatus.DELIVERY_FAILED);
                System.err.println(
                        "Could not consume reserved stock for Order "
                                + order.getOrderId()
                );
                return;
            }
            vehicleTrips.incrementAndGet(vehicle.getVehicleId() - 1);

            System.out.println(
                "Order " + order.getOrderId() +
                " dispatched using Vehicle " +
                vehicle.getVehicleId()
            );
            try {
                notificationService.sendDispatchedNotification(order);
            } catch (RuntimeException e) {
                System.err.println(
                        "Could not send dispatch notification for Order "
                                + order.getOrderId() + ": " + e.getMessage()
                );
            }
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
            if (order.getStatus() == OrderStatus.DISPATCHED) {
                order.updateStatus(OrderStatus.DELIVERY_FAILED);
            }
            Thread.currentThread().interrupt();
            System.err.println(
                "Delivery interrupted for Order " +
                order.getOrderId()
            );
        } catch (RuntimeException e) {
            if (order.getStatus() == OrderStatus.DISPATCHED) {
                order.updateStatus(OrderStatus.DELIVERY_FAILED);
            }
            System.err.println(
                "Delivery failed for Order " +
                order.getOrderId() + ": " + e.getMessage()
            );
        } finally {
            if (permitAcquired) {
                vehicleSemaphore.release();
            }
        }
    }

    public int getVehicleTrips(int vehicleId) {
        if (vehicleId < 1 || vehicleId > VEHICLE_COUNT) {
            throw new IllegalArgumentException("Invalid vehicle ID");
        }
        return vehicleTrips.get(vehicleId - 1);
    }

    public int getVehicleCount() {
        return VEHICLE_COUNT;
    }

    public void shutdown() {
        synchronized (this) {
            if (!shutdownStarted) {
                acceptingDeliveries = false;
                for (int i = 0; i < VEHICLE_COUNT; i++) {
                    deliveryQueue.offer(STOP_REQUEST);
                }
                shutdownStarted = true;
            }
        }
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

    private static final class DeliveryRequest {
        private final CustomerOrder order;

        private DeliveryRequest(CustomerOrder order) {
            this.order = order;
        }
    }
}
