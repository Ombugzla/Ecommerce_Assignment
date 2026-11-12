import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {
    private static final int ORDER_COUNT = 1_000;
    private static final int CUSTOMER_REQUEST_THREADS = 100;

    private static final String[] REQUIRED_CUSTOMER_NAMES = {
            "Gilbert Ongoro",
            "Samuel Mutua",
            "Derrick Kipng'eno",
            "June Nyamoh",
            "Paul Angelo",
            "Dalton Omondi",
            "Larry Mutinda",
            "Sandra Chwanya",
            "Becky Shania",
            "Dennis Gitonga",
    };

    private static final String[] FIRST_NAMES = {
            "Amina", "Brian", "Caroline", "Daniel", "Esther", "Faith",
            "George", "Hannah", "Ian", "Joy", "Kevin", "Linda", "Martin",
            "Naomi", "Oscar", "Peter", "Queen", "Robert", "Sarah", "Terry",
            "Usha", "Victor", "Winnie", "Yusuf", "Zawadi", "Abel", "Chao",
            "Diana", "Eliud", "Fiona", "Grace", "Henry", "Imani", "Joseph",
            "Kendi", "Luka", "Miriam", "Nathan", "Olivia", "Patrick", "Rita","Shadrack",
            "Sharon", "Shallom","Priscilla","Samson","Joseph","John","Angeline","Mark",
            "Alfred","Maxwell","Isaac","Natalie","Nancy","Vincy","Mercy","Hussein",
            "Beryl","Brenda","Bob","Sam","Yvette","Shirley","Denzel","Bravin","Loretta",
            "Angel","Alexander","Mugi","Daisy","Tom", "Princess"
    };

    private static final String[] LAST_NAMES = {
            "Abdi", "Barasa", "Chebet", "Dube", "Elmi", "Farah", "Gitau",
            "Hassan", "Irungu", "Juma", "Kamau", "Limo", "Maina", "Njoroge",
            "Odhiambo", "Otieno", "Kiptoo", "Rono", "Said", "Tadesse",
            "Wekesa", "Yego", "Zuberi", "Achieng", "Biko", "Chumo", "Dawit",
            "Eze", "Furaha", "Githinji", "Hemed", "Kilonzo", "Muriuki",
            "Mutiso", "Nduta", "Onyango", "Parker", "Wanjala", "Mwende","Mutua","Otieno",
            "Okoth","Gituku","Gichuki","Mohamed","Mugi","Askell"
    };

    public static void main(String[] args) throws Exception {
        printCenteredBanner("ZAVHIRA NEXUS E-COMMERCE", '_', 80);
        System.out.println();

        // 1. The two hand-off lines between stages
        BlockingQueue<CustomerOrder> warehouseQueue = new LinkedBlockingQueue<>();
        BlockingQueue<CustomerOrder> deliveryQueue = new LinkedBlockingQueue<>();

        // 2. Create every stage
        InventoryManager inventory = new InventoryManager();
        NotificationService notifications = new NotificationService();
        PaymentProcessor payments =
                new PaymentProcessor(inventory, warehouseQueue, notifications);
        WarehouseWorker warehouse =
                new WarehouseWorker(warehouseQueue, deliveryQueue);
        DeliveryManager delivery =
                new DeliveryManager(notifications, inventory);

        // 3. Start the stages that wait for work
        warehouse.startWorkers();

        // Bridge: nothing else moves orders from deliveryQueue to DeliveryManager
        Thread bridge = new Thread(() -> {
            try {
                while (true) {
                    CustomerOrder order = deliveryQueue.take();
                    delivery.submitForDelivery(order);
                }
            } catch (InterruptedException e) {
                // interrupted at shutdown: stop quietly
            }
        });
        bridge.start();

        // 4. Create 1,000 orders with the requested names mixed into the batch.
        List<CustomerOrder> orders = createOrders();

        // Submit all customer requests concurrently. Synchronized reservation
        // prevents requests from overselling the same remaining stock.
        ExecutorService customerRequests =
                Executors.newFixedThreadPool(CUSTOMER_REQUEST_THREADS);
        for (CustomerOrder order : orders) {
            customerRequests.submit(() -> {
                if (inventory.reserveStock(order)) {
                    payments.submitOrder(order);
                } else if (inventory.cancelOrder(order)) {
                    notifications.sendOutOfStockNotification(order);
                }
            });
        }
        customerRequests.shutdown();
        if (!customerRequests.awaitTermination(60, TimeUnit.SECONDS)) {
            customerRequests.shutdownNow();
            throw new IllegalStateException(
                    "Customer request submission timed out"
            );
        }

        // Allow longer batches more time to complete before stopping workers.
        long timeoutMillis = Math.max(60_000L, orders.size() * 1_000L);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!allFinished(orders) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        // 7. Shut everything down, in pipeline order
        payments.shutdown();
        warehouse.stopWorkers();
        bridge.interrupt();
        delivery.shutdown();

        // 8. Final report
        System.out.println();
        int delivered = 0, paymentFailed = 0, cancelled = 0;
        int deliveryFailed = 0, other = 0;
        List<String> reportLines = new ArrayList<>();
        for (CustomerOrder order : orders) {
            reportLines.add(String.format(
                    "Order %4d | Customer: %-24s | Status: %s",
                    order.getOrderId(), order.getCustomerName(),
                    order.getStatus()
            ));
            switch (order.getStatus()) {
                case DELIVERED: delivered++; break;
                case PAYMENT_FAILED: paymentFailed++; break;
                case CANCELLED: cancelled++; break;
                case DELIVERY_FAILED: deliveryFailed++; break;
                default: other++;
            }
        }
        reportLines.add("Total orders received: " + orders.size());
        reportLines.add("Successful payments: " + payments.getSuccessfulPayments());
        reportLines.add("Failed payments:     " + payments.getFailedPayments());
        reportLines.add("Orders fulfilled:    " + warehouse.getFulfilledOrderCount());
        reportLines.add("Delivered:           " + delivered);
        reportLines.add("Cancelled:           " + cancelled);
        reportLines.add("Delivery failed:     " + deliveryFailed);
        reportLines.add("Not finished:        " + other);
        reportLines.add("Remaining Product A: "
                + inventory.getAvailableStock("Product A"));
        reportLines.add("Remaining Product B: "
                + inventory.getAvailableStock("Product B"));
        reportLines.add("Vehicle utilization (trips):");
        for (int vehicleId = 1; vehicleId <= delivery.getVehicleCount(); vehicleId++) {
            reportLines.add("  Vehicle " + vehicleId + ": "
                    + delivery.getVehicleTrips(vehicleId));
        }
        reportLines.add("Warehouse errors:    " + warehouse.getWorkerErrors());

        int reportWidth = "FINAL REPORT".length();
        for (String line : reportLines) {
            reportWidth = Math.max(reportWidth, line.length());
        }
        String reportSeparator = "-".repeat(reportWidth);
        System.out.println(reportSeparator);
        System.out.println(centerText("FINAL REPORT", reportWidth));
        System.out.println(reportSeparator);
        for (String line : reportLines) {
            System.out.println(line);
        }
        System.out.println("_".repeat(reportWidth));
        System.out.println("\nSimulation completed.");
    }

    private static void printCenteredBanner(String title, char fill, int width) {
        int bannerWidth = Math.max(width, title.length());
        int remaining = bannerWidth - title.length();
        int left = remaining / 2;
        int right = remaining - left;
        System.out.println(String.valueOf(fill).repeat(left)
                + title
                + String.valueOf(fill).repeat(right));
    }

    private static String centerText(String text, int width) {
        int leftPadding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(leftPadding) + text;
    }

    private static boolean allFinished(List<CustomerOrder> orders) {
        for (CustomerOrder order : orders) {
            switch (order.getStatus()) {
                case DELIVERED:
                case DELIVERY_FAILED:
                case PAYMENT_FAILED:
                case CANCELLED:
                    break;          // this one is finished, check the next
                default:
                    return false;   // still moving through the pipeline
            }
        }
        return true;
    }

    private static List<CustomerOrder> createOrders() {
        Random random = new Random();
        List<String> customerNames = new ArrayList<>(
                List.of(REQUIRED_CUSTOMER_NAMES)
        );
        HashSet<String> uniqueNames = new HashSet<>(customerNames);

        while (customerNames.size() < ORDER_COUNT) {
            String name = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]
                    + " "
                    + LAST_NAMES[random.nextInt(LAST_NAMES.length)].trim();
            if (uniqueNames.add(name)) {
                customerNames.add(name);
            }
        }
        Collections.shuffle(customerNames, random);

        List<CustomerOrder> orders = new ArrayList<>(ORDER_COUNT);
        for (int i = 0; i < ORDER_COUNT; i++) {
            String productName = random.nextBoolean()
                    ? "Product A" : "Product B";
            int quantity = random.nextInt(3) + 1;
            orders.add(new CustomerOrder(
                    i + 1,
                    i + 101,
                    customerNames.get(i),
                    productName,
                    quantity
            ));
        }
        return orders;
    }
}