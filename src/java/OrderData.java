import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

record OrderData(List<Customer> customers, List<GarmentModel> models, List<Order> orders,
                 List<Material> materials, List<Operation> operations,
                 List<ProductionTask> tasks) {
    static OrderData load(Path folder) throws IOException {
        var customers = load(folder, "customers", 3,
                c -> new Customer(id(c, 0), c[1], c[2]));
        var models = load(folder, "garment_models", 3,
                c -> new GarmentModel(id(c, 0), c[1], c[2]));
        var measurements = load(folder, "measurements", 4,
                c -> new Measurement(id(c, 0), id(c, 1), c[2], new BigDecimal(c[3])));
        var materials = load(folder, "materials", 5,
                c -> new Material(id(c, 0), c[1], c[2], id(c, 3), new BigDecimal(c[4])));
        var orderMaterials = load(folder, "order_materials", 5,
                c -> new OrderMaterial(id(c, 0), id(c, 1), id(c, 2), new BigDecimal(c[3]), id(c, 4)));
        var operations = load(folder, "operations", 3,
                c -> new Operation(id(c, 0), c[1], id(c, 2)));
        var orderOperations = load(folder, "order_operations", 5,
                c -> new OrderOperation(id(c, 0), id(c, 1), id(c, 2), new BigDecimal(c[3]), id(c, 4)));
        var tasks = load(folder, "production_tasks", 5,
                c -> new ProductionTask(id(c, 0), id(c, 1), LocalDate.parse(c[2]),
                        LocalDate.parse(c[3]), c[4]));
        var orders = load(folder, "orders", 6, c -> {
            long orderId = id(c, 0);
            return new Order(orderId, id(c, 1), id(c, 2),
                    c[3].isBlank() ? null : LocalDate.parse(c[3]), c[4], c[5],
                    measurements.stream().filter(m -> m.orderId() == orderId).toList(),
                    orderMaterials.stream().filter(m -> m.orderId() == orderId).toList(),
                    orderOperations.stream().filter(o -> o.orderId() == orderId).toList());
        });
        return new OrderData(customers, models, orders, materials, operations, tasks);
    }

    private static <T> List<T> load(Path folder, String name, int width,
                                    Function<String[], T> decode) throws IOException {
        var rows = Files.readAllLines(folder.resolve(name + ".tsv"));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(name + ".tsv: отсутствует заголовок");
        }
        var result = new java.util.ArrayList<T>();
        for (int index = 1; index < rows.size(); index++) {
            var cells = rows.get(index).split("\t", -1);
            try {
                if (cells.length != width) {
                    throw new IllegalArgumentException("ожидалось столбцов: " + width);
                }
                result.add(decode.apply(cells));
            } catch (IllegalArgumentException | java.time.DateTimeException error) {
                throw new IllegalArgumentException(name + ".tsv, строка " + (index + 1)
                        + ": неверный формат данных", error);
            }
        }
        return List.copyOf(result);
    }

    private static long id(String[] cells, int index) {
        return Long.parseLong(cells[index]);
    }
}
