import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

record Cost(BigDecimal materials, BigDecimal operations) {
    BigDecimal total() { return materials.add(operations); }
}

record OrderResult(List<CheckResult> checks, Cost cost, ProductionTask task, String failureReason) {
    boolean accepted() { return task != null; }
}

final class OrderService {
    private final List<OrderCheck> checks;
    private final Clock clock;

    OrderService(List<OrderCheck> checks, Clock clock) {
        this.checks = List.copyOf(checks);
        this.clock = clock;
    }

    OrderResult evaluate(Order order, OrderData data) {
        var results = checks.stream().map(check -> check.check(order, data)).toList();
        var cost = new Cost(sum(order.materials()), sum(order.operations()));
        for (CheckResult result : results) {
            if (!result.passed()) return new OrderResult(results, cost, null, result.failureReason());
        }
        if (!order.status().equals("согласован")) {
            return new OrderResult(results, cost, null, "Заказ не согласован клиентом");
        }
        var customer = data.customers().stream().filter(c -> c.id() == order.customerId())
                .findFirst().orElseThrow();
        var model = data.models().stream().filter(m -> m.id() == order.modelId())
                .findFirst().orElseThrow();
        long taskId = data.tasks().stream().mapToLong(ProductionTask::id).max().orElse(0) + 1;
        String specification = "Изделие: " + model.name() + "; клиент: " + customer.name()
                + "; требования: " + order.requirements();
        var task = new ProductionTask(taskId, order.id(), LocalDate.now(clock), order.dueDate(), specification);
        return new OrderResult(results, cost, task, "");
    }

    private BigDecimal sum(List<? extends OrderLine> lines) {
        var sum = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            sum = sum.add(line.subtotal());
        }
        return sum;
    }
}
