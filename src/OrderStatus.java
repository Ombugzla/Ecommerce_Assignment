
public enum OrderStatus {
    //Different statuses to be used


    // Order received by the system
    RECEIVED,

    // Waiting for payment validation
    PAYMENT_PENDING,

    // Payment successfully validated
    PAID,

    // Inventory reserved and the warehouse is processing the order
    PROCESSING,

    // Order picked and packaged
    READY_FOR_DELIVERY,

    // Order left the warehouse
    DISPATCHED,

    // Customer has received the order
    DELIVERED,

    // Delivery could not be completed
    DELIVERY_FAILED,

    //Payment unsuccessful
    PAYMENT_FAILED,

    //Order cancelled
    CANCELLED
}
