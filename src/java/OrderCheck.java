import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

interface OrderCheck {
    CheckResult check(Order order, OrderData data);
}

record CheckResult(String title, List<String> problems, String failureReason) {
    CheckResult {
        problems = List.copyOf(problems);
    }

    boolean passed() { return problems.isEmpty(); }
}

final class CompletenessCheck implements OrderCheck {
    private final List<String> required;

    CompletenessCheck(List<String> required) { this.required = List.copyOf(required); }

    @Override public CheckResult check(Order order, OrderData data) {
        var missing = new ArrayList<String>();
        if (data.customers().stream().noneMatch(c -> c.id() == order.customerId()))
            missing.add("карточка клиента");
        if (data.models().stream().noneMatch(m -> m.id() == order.modelId()))
            missing.add("модель изделия");
        if (order.dueDate() == null) missing.add("срок готовности");
        if (order.requirements().isEmpty()) missing.add("пожелания и требования");
        Set<String> available = order.measurements().stream()
                .filter(m -> m.valueCm().signum() > 0)
                .map(Measurement::name).collect(Collectors.toSet());
        for (String name : required) {
            if (!available.contains(name)) missing.add("мерка: " + name);
        }
        if (order.materials().isEmpty()) missing.add("перечень материалов");
        if (order.operations().isEmpty()) missing.add("перечень работ");
        return new CheckResult("Комплектность", missing, "Неполные данные: " + String.join(", ", missing));
    }
}

final class MaterialAvailabilityCheck implements OrderCheck {
    @Override public CheckResult check(Order order, OrderData data) {
        var shortages = new ArrayList<String>();
        for (OrderMaterial line : order.materials()) {
            Material found = null;
            for (Material candidate : data.materials()) {
                if (candidate.id() == line.materialId()) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) {
                shortages.add(line.label() + " " + line.materialId() + ": нет в каталоге");
            } else if (!line.hasEnoughStock(found)) {
                shortages.add(line.label() + " " + found.id() + ": требуется "
                        + line.quantity() + ", в наличии " + found.stock());
            }
        }
        return new CheckResult("Материалы", shortages, "Не хватает материалов");
    }
}
