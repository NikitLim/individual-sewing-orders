"""Тесты стандартной библиотеки: python -m unittest -v."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from main import (check_order_completeness, check_material_availability,
                  calculate_order_cost, form_production_task)


class ProceduralChecks(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(Path(__file__).with_name('demo_data.json').read_text(encoding='utf-8'))
        self.required = ['обхват груди', 'обхват талии', 'длина изделия']

    def test_completeness(self):
        self.assertTrue(check_order_completeness(self.data, 1001, self.required)['ok'])
        self.data['measurements'].pop()
        self.assertFalse(check_order_completeness(self.data, 1001, self.required)['ok'])

    def test_availability(self):
        self.assertTrue(check_material_availability(self.data, 1001)['ok'])
        self.data['materials'][0]['stock_quantity'] = 2
        self.assertFalse(check_material_availability(self.data, 1001)['ok'])

    def test_cost(self):
        total = calculate_order_cost(self.data, 1001)
        self.assertEqual(total['materials_kopecks'], 885000)
        self.assertEqual(total['operations_kopecks'], 790000)
        self.assertEqual(total['total_kopecks'], 1675000)

    def test_task_requires_approval(self):
        self.assertTrue(form_production_task(self.data, 1001, self.required)['ok'])
        self.data['orders'][0]['status'] = 'проект'
        self.assertFalse(form_production_task(self.data, 1001, self.required)['ok'])


class CommandLineChecks(unittest.TestCase):
    def run_cli(self, *args):
        return subprocess.run(
            [sys.executable, '-X', 'utf8', str(Path(__file__).with_name('main.py')), *args],
            capture_output=True, encoding='utf-8', check=False,
        )

    def test_exit_code_reports_whether_task_was_created(self):
        self.assertEqual(self.run_cli().returncode, 0)
        rejected = self.run_cli('--order', '-1')
        self.assertEqual(rejected.returncode, 1)
        self.assertIn('Задание не сформировано: Заказ не найден', rejected.stdout)

    def test_input_file_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input.json'
            cases = [
                (None, 'не удалось открыть файл данных'),
                (b'\xff', 'UTF-8'),
                (b'{', 'строка 1, столбец 2'),
            ]
            for contents, message in cases:
                with self.subTest(message=message):
                    if contents is not None:
                        path.write_bytes(contents)
                    result = self.run_cli('--data', str(path))
                    self.assertEqual(result.returncode, 2)
                    self.assertIn(message, result.stderr)
                    self.assertNotIn('Traceback', result.stderr)


if __name__ == '__main__':
    unittest.main()
