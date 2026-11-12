import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PaymentProcessor {
    private final ExecutorService paymentPool = Executors.newFixedThreadPool(3);
    private final InventoryManager inventoryManager;
    private final BlockingQueue<CustomerOrder> warehouseQueue;
    private final NotificationService notificationService;
    private final AtomicInteger successfulPayments = new AtomicInteger(0);
    private final AtomicInteger failedPayments = new AtomicInteger(0);
    private final Random random = new Random();

public PaymentProcessor(
        InventoryManager inventoryManager,
        BlockingQueue<CustomerOrder> warehouseQueue) {
    this(inventoryManager, warehouseQueue, new NotificationService());
}

public PaymentProcessor(
        InventoryManager inventoryManager,
        BlockingQueue<CustomerOrder> warehouseQueue,
        NotificationService notificationService) {
    if (inventoryManager == null || warehouseQueue == null
            || notificationService == null) {
        throw new IllegalArgumentException(
                "Inventory manager, warehouse queue, and notification service are required"
        );
    }
    this.inventoryManager = inventoryManager;
    this.warehouseQueue = warehouseQueue;
    this.notificationService = notificationService;
}

public void submitOrder(CustomerOrder order) {
    if (order == null) {
        throw new IllegalArgumentException("Order cannot be null");
    }
    paymentPool.submit(() -> processPayment(order));
}

public boolean processPayment(CustomerOrder order) {
    if (order == null) {
        throw new IllegalArgumentException("Order cannot be null");
    }
    try{
        if(order.getStatus() == OrderStatus.CANCELLED){
            inventoryManager.releaseStock(order);
            return false;
        }

        if (!order.updateStatus(OrderStatus.PAYMENT_PENDING)) {
            return false;
        }

        Thread.sleep(90);

        boolean paymentApproved = random.nextInt(100) < 87;

        if (paymentApproved) {
            synchronized (order) {
                if (!order.updateStatus(OrderStatus.PAID)) {
                    return false;
                }
                warehouseQueue.offer(order);
                successfulPayments.incrementAndGet();
            }
            return true;
        }

        markPaymentFailed(order);
        return false;
    }
    catch (InterruptedException e) {
        markPaymentFailed(order);
        Thread.currentThread().interrupt();
        return false;
    }
    catch (RuntimeException e) {
        System.err.println("Error processing payment for order " + order.getOrderId() + ": " + e.getMessage());
        markPaymentFailed(order);
        return false;
    }
}

private void markPaymentFailed(CustomerOrder order) {
    if (order.updateStatus(OrderStatus.PAYMENT_FAILED)) {
        failedPayments.incrementAndGet();
        inventoryManager.releaseStock(order);
        notificationService.sendPaymentFailedNotification(order);
    }
}

public int getSuccessfulPayments() {
    return successfulPayments.get();
}
public int getFailedPayments() {
    return failedPayments.get();
}
public void shutdown() {
    paymentPool.shutdown();
    try {
        if (!paymentPool.awaitTermination(19, TimeUnit.SECONDS)) {
            paymentPool.shutdownNow();
        }
    } catch (InterruptedException e) {
        paymentPool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
}