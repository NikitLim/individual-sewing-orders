import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record Customer(long id, String name, String phone) {}
record GarmentModel(long id, String name, String description) {}
record Measurement(long id, long orderId, String name, BigDecimal valueCm) {}
record Material(long id, String name, String unit, long priceKopecks, BigDecimal stock) {}
record Operation(long id, String name, long basePriceKopecks) {}
record ProductionTask(long id, long orderId, LocalDate issuedAt, LocalDate dueDate,
                      String specification) {}
record Order(long id, long customerId, long modelId, LocalDate dueDate,
             String status, String requirements, List<Measurement> measurements,
             List<OrderMaterial> materials, List<OrderOperation> operations) {
    Order {
        measurements = List.copyOf(measurements);
        materials = List.copyOf(materials);
        operations = List.copyOf(operations);
    }
}

abstract class OrderLine {
    private final long id;
    private final long orderId;
    private final BigDecimal quantity;
    private final long unitPriceKopecks;

    OrderLine(long id, long orderId, BigDecimal quantity, long unitPriceKopecks) {
        this.id = id;
        this.orderId = orderId;
        this.quantity = quantity;
        this.unitPriceKopecks = unitPriceKopecks;
    }

    long id() { return id; }
    long orderId() { return orderId; }
    BigDecimal quantity() { return quantity; }
    BigDecimal subtotal() { return quantity.multiply(BigDecimal.valueOf(unitPriceKopecks)); }
    abstract String label();
}

final class OrderMaterial extends OrderLine {
    private final long materialId;

    OrderMaterial(long id, long orderId, long materialId, BigDecimal quantity, long price) {
        super(id, orderId, quantity, price);
        this.materialId = materialId;
    }

    long materialId() { return materialId; }
    @Override String label() { return "Материал"; }
    boolean hasEnoughStock(Material material) {
        return material.stock().compareTo(quantity()) >= 0;
    }
}

final class OrderOperation extends OrderLine {
    private final long operationId;

    OrderOperation(long id, long orderId, long operationId, BigDecimal quantity, long price) {
        super(id, orderId, quantity, price);
        this.operationId = operationId;
    }

    long operationId() { return operationId; }
    @Override String label() { return "Работа"; }
}
