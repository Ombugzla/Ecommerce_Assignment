import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PaymentProcessor {
    private final ExecutorService paymentPool = Executors.newFixedThreadPool(3);
    private final InventoryManager inventoryManager;
    private final BlockingQueue<CustomerOrder> warehouseQueue;
    private final AtomicInteger successfulPayments = new AtomicInteger(0);
    private final AtomicInteger failedPayments = new AtomicInteger(0);
    private final Random random = new Random();


public PaymentProcessor(InventoryManager inventoryManager, BlockingQueue<CustomerOrder> warehouseQueue) {
    if (inventoryManager == null || warehouseQueue == null) {
        throw new IllegalArgumentException(
                "Inventory manager and warehouse queue are required"
        );
    }
    this.inventoryManager = inventoryManager;
    this.warehouseQueue = warehouseQueue;
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

        Thread.sleep(15);

        boolean paymentApproved = random.nextInt(100) < 87;

        if (paymentApproved && order.updateStatus(OrderStatus.PAID)) {
            successfulPayments.incrementAndGet();
            warehouseQueue.put(order);
            return true;
        } else {
            if(order.getStatus() != OrderStatus.CANCELLED) {
                order.updateStatus(OrderStatus.PAYMENT_FAILED);
            }
            failedPayments.incrementAndGet();
            inventoryManager.releaseStock(order);
            return false;       
        }

    }
    catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        order.updateStatus(OrderStatus.PAYMENT_FAILED);
        failedPayments.incrementAndGet();
        inventoryManager.releaseStock(order);
        return false;
    }
    catch (RuntimeException e) {
        System.err.println("Error processing payment for order " + order.getOrderId() + ": " + e.getMessage());
        order.updateStatus(OrderStatus.PAYMENT_FAILED);
        failedPayments.incrementAndGet();
        inventoryManager.releaseStock(order);
        return false;
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