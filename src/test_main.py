"""Тесты стандартной библиотеки: python -m unittest -v."""
import copy
import json
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


if __name__ == '__main__':
    unittest.main()
