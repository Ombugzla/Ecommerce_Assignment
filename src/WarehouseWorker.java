import java.util.*; 
import java.util.concurrent.*; 

public class WarehouseWorker {
    private static final int WORKERS = 5;
    private static final long PICK_DELAY_MILLIS = 150;
    private static final long PACK_DELAY_MILLIS = 200;
    private final BlockingQueue<CustomerOrder> warehouseQueue;
    private final BlockingQueue<CustomerOrder> deliveryQueue;
    private final ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final java.util.concurrent.atomic.AtomicInteger fulfilledOrders =
            new java.util.concurrent.atomic.AtomicInteger();

    public WarehouseWorker(BlockingQueue<CustomerOrder> warehouseQueue, BlockingQueue<CustomerOrder> deliveryQueue) {
        this.warehouseQueue = warehouseQueue;
        this.deliveryQueue = deliveryQueue;
    }
  
    public void startWorkers() {        
        for (int i = 0; i < WORKERS; i++) {
            pool.submit(this::workerLoop);   //5 workers star working
        }
    }
    private void workerLoop() {
        try {
            while (true) {
                CustomerOrder order = warehouseQueue.take(); //worker from pool takes order from queue
                try {
                    processOrder(order);
                } catch (RuntimeException e) {
                    errors.add("Order " + order.getOrderId() + ": " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // stopWorkers() was called
        }
    }
    public boolean processOrder(CustomerOrder order) {    // Claim order: one worker one PAID -> PROCESSING
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (!order.updateStatus(OrderStatus.PROCESSING)) {
            return false; // cancelled, failed, or already taken
        }

        try {
            System.out.println("Picking Order " + order.getOrderId());
            Thread.sleep(PICK_DELAY_MILLIS);
            System.out.println("Packaging Order " + order.getOrderId());
            Thread.sleep(PACK_DELAY_MILLIS);

            if (!order.updateStatus(OrderStatus.READY_FOR_DELIVERY)) {
                return false; // cancelled while we worked
            }

            deliveryQueue.put(order);
            fulfilledOrders.incrementAndGet();
            return true;

        } catch (InterruptedException e) {
            order.updateStatus(OrderStatus.PAID);
            errors.add("Interrupted: Order " + order.getOrderId());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    public List<String> getWorkerErrors() {
        synchronized (errors) {
            return new ArrayList<>(errors);
        }
    }

    public int getFulfilledOrderCount() {
        return fulfilledOrders.get();
    }

    public void stopWorkers() {
        pool.shutdownNow();        //stops the processing of orders
    }
}
