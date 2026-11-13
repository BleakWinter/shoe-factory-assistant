from io import BytesIO
from unittest import TestCase

from fastapi.testclient import TestClient
from PIL import Image

from shoe_factory_data.main import app


class DeliveryRenderTest(TestCase):
    def setUp(self):
        self.client = self.enterContext(TestClient(app))
        self.path = "/reports/delivery/render"
        self.group = {
            "orderNo": "E2682",
            "deliveryDate": "2026-11-09",
            "overdueDays": 4,
            "styles": [{"styleNo": "JCD-409-5-01", "pendingPairs": 16}],
        }

    def render(self, groups):
        response = self.client.post(self.path, json={"reportDate": "2026-11-13", "orderGroups": groups})
        self.assertEqual(response.status_code, 200, response.text if response.status_code != 200 else "")
        self.assertEqual(response.headers["content-type"], "image/png")
        with Image.open(BytesIO(response.content)) as image:
            self.assertEqual(image.format, "PNG")
            self.assertEqual(image.width, 820)
            return image.copy()

    def test_preserves_request_order_and_merges_order_cells(self):
        group = {**self.group, "styles": [*self.group["styles"], {"styleNo": "JCD-409-5-02", "pendingPairs": 30}]}
        known = {**self.group, "deliveryDate": "2027-01-06", "overdueDays": 0}
        with self.render([known, group]) as image:
            self.assertEqual(image.height, 321)
            self.assertEqual(image.getpixel((33, 145)), (255, 255, 255))
            self.assertEqual(image.getpixel((33, 200)), (255, 241, 237))
            self.assertEqual(image.getpixel((33, 242)), (255, 241, 237))
            self.assertEqual(image.getpixel((257, 242)), (213, 225, 218))

    def test_renders_empty_report_and_unknown_date(self):
        with self.render([]) as empty, self.render([{**self.group, "deliveryDate": None, "overdueDays": 0}]) as unknown:
            self.assertNotEqual(empty.tobytes(), unknown.tobytes())

    def test_wraps_long_identifiers(self):
        group = {**self.group, "orderNo": "达为" * 64, "styles": [{"styleNo": "A" * 128, "pendingPairs": 16}]}
        with self.render([group]) as image:
            self.assertGreater(image.height, 215)

    def test_invalid_fields_and_oversized_report_return_422(self):
        invalid_groups = [
            [{**self.group, "styles": [{"styleNo": "JCD-409-5-01", "pendingPairs": "16"}]}],
            [{**self.group, "styles": []}],
            [{**self.group, "deliveryDate": None}],
            [self.group] * 160,
        ]
        for groups in invalid_groups:
            with self.subTest(rows=len(groups)):
                response = self.client.post(self.path, json={"reportDate": "2026-11-13", "orderGroups": groups})
                self.assertEqual(response.status_code, 422)
                self.assertIn("detail", response.json())
