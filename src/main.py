"""КТ2: процедурная модель оформления индивидуального заказа (Python 3.12)."""
import argparse
import json
from datetime import date
from pathlib import Path


def find_by_id(records, identifier, field):
    """Найти запись по ключу без пользовательских классов."""
    for item in records:
        if item[field] == identifier:
            return item
    return None


def check_order_completeness(data, order_id, required_measurements):
    """Проверить карточку заказа и наличие обязательных мерок."""
    order = find_by_id(data['orders'], order_id, 'order_id')
    if order is None:
        return {'ok': False, 'missing': ['заказ не найден']}
    missing = []
    if find_by_id(data['customers'], order['customer_id'], 'customer_id') is None:
        missing.append('карточка клиента')
    if find_by_id(data['garment_models'], order['model_id'], 'model_id') is None:
        missing.append('модель изделия')
    if not order.get('due_date'):
        missing.append('срок готовности')
    if not order.get('requirements'):
        missing.append('пожелания и требования')
    available = {m['name'] for m in data['measurements']
                 if m['order_id'] == order_id and m['value_cm'] > 0}
    for name in required_measurements:
        if name not in available:
            missing.append('мерка: ' + name)
    if not any(line['order_id'] == order_id for line in data['order_materials']):
        missing.append('перечень материалов')
    if not any(line['order_id'] == order_id for line in data['order_operations']):
        missing.append('перечень работ')
    return {'ok': not missing, 'missing': missing}


def check_material_availability(data, order_id):
    """АЛГОРИТМ 1. Для каждой позиции заказа линейно найти материал в каталоге.

    n — позиции материалов данного заказа, m — все материалы в каталоге.
    Время O(n*m) в худшем случае, доп. память O(n).
    """
    shortages = []
    for line in data['order_materials']:
        if line['order_id'] != order_id:
            continue
        material = None
        for candidate in data['materials']:
            if candidate['material_id'] == line['material_id']:
                material = candidate
                break
        if material is None:
            shortages.append({'material_id': line['material_id'], 'reason': 'нет в каталоге'})
        elif material['stock_quantity'] < line['quantity']:
            shortages.append({'material_id': line['material_id'],
                              'reason': 'недостаточный остаток',
                              'required': line['quantity'],
                              'available': material['stock_quantity']})
    return {'ok': not shortages, 'shortages': shortages}


def calculate_order_cost(data, order_id):
    """АЛГОРИТМ 2. Сложить стоимость материалов и работ.

    p — число всех строк материалов, q — число всех строк операций.
    Время O(p+q), доп. память O(1). Цены хранятся в копейках.
    """
    materials_kopecks = 0
    operations_kopecks = 0
    for line in data['order_materials']:
        if line['order_id'] == order_id:
            materials_kopecks += line['quantity'] * line['unit_price_kopecks']
    for line in data['order_operations']:
        if line['order_id'] == order_id:
            operations_kopecks += line['quantity'] * line['unit_price_kopecks']
    return {'materials_kopecks': materials_kopecks,
            'operations_kopecks': operations_kopecks,
            'total_kopecks': materials_kopecks + operations_kopecks}


def form_production_task(data, order_id, required_measurements):
    """Сформировать задание только для согласованного, полного заказа."""
    order = find_by_id(data['orders'], order_id, 'order_id')
    if order is None:
        return {'ok': False, 'reason': 'Заказ не найден'}
    completeness = check_order_completeness(data, order_id, required_measurements)
    if not completeness['ok']:
        return {'ok': False, 'reason': 'Неполные данные: ' + ', '.join(completeness['missing'])}
    availability = check_material_availability(data, order_id)
    if not availability['ok']:
        return {'ok': False, 'reason': 'Не хватает материалов'}
    if order['status'] != 'согласован':
        return {'ok': False, 'reason': 'Заказ не согласован клиентом'}
    customer = find_by_id(data['customers'], order['customer_id'], 'customer_id')
    model = find_by_id(data['garment_models'], order['model_id'], 'model_id')
    result = {'task_id': max((t['task_id'] for t in data['production_tasks']), default=0) + 1,
              'order_id': order_id, 'issued_at': date.today().isoformat(),
              'due_date': order['due_date'],
              'specification': 'Изделие: ' + model['name'] +
                               '; клиент: ' + customer['full_name'] +
                               '; требования: ' + order['requirements']}
    return {'ok': True, 'task': result}


def format_rubles(kopecks):
    return f'{kopecks // 100:,}'.replace(',', ' ') + f',{kopecks % 100:02d} руб.'


def demonstrate(data, order_id):
    required = ['обхват груди', 'обхват талии', 'длина изделия']
    print('ЗАКАЗ №', order_id)
    completeness = check_order_completeness(data, order_id, required)
    print('Комплектность:', 'ОК' if completeness['ok'] else ', '.join(completeness['missing']))
    availability = check_material_availability(data, order_id)
    print('Материалы:', 'достаточно' if availability['ok'] else str(availability['shortages']))
    cost = calculate_order_cost(data, order_id)
    print('Материалы:', format_rubles(cost['materials_kopecks']))
    print('Работы:', format_rubles(cost['operations_kopecks']))
    print('ИТОГО:', format_rubles(cost['total_kopecks']))
    task = form_production_task(data, order_id, required)
    if task['ok']:
        print('Производственное задание №', task['task']['task_id'], 'сформировано')
        print('Дата выдачи:', task['task']['issued_at'])
        print('Срок:', task['task']['due_date'])
        print('Описание:', task['task']['specification'])
    else:
        print('Задание не сформировано:', task['reason'])
    return task


def main():
    parser = argparse.ArgumentParser(description='Процедурная система индивидуальных заказов')
    parser.add_argument('--data', default=str(Path(__file__).with_name('demo_data.json')))
    parser.add_argument('--order', type=int, default=1001)
    args = parser.parse_args()
    try:
        with open(args.data, encoding='utf-8') as stream:
            data = json.load(stream)
    except OSError:
        parser.error('не удалось открыть файл данных')
    except UnicodeDecodeError:
        parser.error('файл данных должен быть в кодировке UTF-8')
    except json.JSONDecodeError as error:
        parser.error(f'некорректный JSON (строка {error.lineno}, столбец {error.colno})')
    task = demonstrate(data, args.order)
    return 0 if task['ok'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
