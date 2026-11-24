import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

public final class Main {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    private static int run(String[] args) {
        if (args.length > 2) {
            System.err.println("Использование: java -cp out Main [папка_данных] [номер_заказа]");
            return 2;
        }
        try {
            Path input = Path.of(args.length > 0 ? args[0] : "demo");
            long id = args.length > 1 ? Long.parseLong(args[1]) : 1001;
            var data = OrderData.load(input);
            var order = data.orders().stream().filter(candidate -> candidate.id() == id).findFirst();
            if (order.isEmpty()) {
                System.out.println("Задание не сформировано: Заказ не найден");
                return 1;
            }
            var service = new OrderService(List.of(
                    new CompletenessCheck(List.of("обхват груди", "обхват талии", "длина изделия")),
                    new MaterialAvailabilityCheck()), Clock.systemDefaultZone());
            var result = service.evaluate(order.orElseThrow(), data);
            System.out.println("ЗАКАЗ № " + id);
            for (var check : result.checks()) {
                System.out.println(check.title() + ": "
                        + (check.passed() ? "ОК" : String.join(", ", check.problems())));
            }
            System.out.println("Материалы: " + rubles(result.cost().materials()));
            System.out.println("Работы: " + rubles(result.cost().operations()));
            System.out.println("ИТОГО: " + rubles(result.cost().total()));
            if (!result.accepted()) {
                System.out.println("Задание не сформировано: " + result.failureReason());
                return 1;
            }
            var task = result.task();
            System.out.println("Производственное задание № " + task.id() + " сформировано");
            System.out.println("Дата выдачи: " + task.issuedAt());
            System.out.println("Срок: " + task.dueDate());
            System.out.println("Описание: " + task.specification());
            return 0;
        } catch (IOException error) {
            System.err.println("Не удалось прочитать файлы данных.");
            return 2;
        } catch (IllegalArgumentException error) {
            System.err.println("Ошибка ввода: " + error.getMessage());
            return 2;
        }
    }

    private static String rubles(BigDecimal kopecks) {
        var symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        var format = new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(kopecks.movePointLeft(2)) + " руб.";
    }
}
