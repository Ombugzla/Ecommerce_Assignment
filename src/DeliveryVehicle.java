
public class DeliveryVehicle {

    private final int vehicleId;

    public DeliveryVehicle(int vehicleId) {
        if (vehicleId <= 0) {
            throw new IllegalArgumentException("Vehicle ID must be positive");
        }
        this.vehicleId = vehicleId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    // Simulation
    public void deliver(CustomerOrder order) throws InterruptedException {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        System.out.println(
            "Vehicle " + vehicleId +
            " is delivering Order " + order.getOrderId()
        );

        Thread.sleep(120);

        System.out.println(
            "Vehicle " + vehicleId +
            " successfully delivered Order " + order.getOrderId()
        );
    }
}
