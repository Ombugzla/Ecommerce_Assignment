import java.util.*; 
import java.util.concurrent.*; 

public class WarehouseWorker {
    private static final int WORKERS = 5;
    private final BlockingQueue<CustomerOrder> warehouseQueue;
    private final BlockingQueue<CustomerOrder> deliveryQueue;
    private final ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

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
            Thread.sleep(1000);
            System.out.println("Packaging Order " + order.getOrderId());
            Thread.sleep(1000);

            if (!order.updateStatus(OrderStatus.READY_FOR_DELIVERY)) {
                return false; // cancelled while we worked
            }

            deliveryQueue.put(order);
            return true;

        } catch (InterruptedException e) {
            order.updateStatus(OrderStatus.PAID);
            errors.add("Interrupted: Order " + order.getOrderId());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    public List<String> getWorkerErrors() {
        return new ArrayList<>(errors);             //shows errors
    }
    public void stopWorkers() {
        pool.shutdownNow();        //stops the processing of orders
    }
}
