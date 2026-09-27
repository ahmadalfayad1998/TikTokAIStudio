import os
import sys
import tempfile
import unittest
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))

import visual_provider


class VisualProviderTests(unittest.TestCase):
    def test_pexels_query_removes_rendering_boilerplate(self):
        query = visual_provider._pexels_query(
            "Vertical 9:16 cinematic editorial scene, no text, no watermark, internet outage office workers"
        )
        self.assertNotIn("vertical", query.lower())
        self.assertNotIn("watermark", query.lower())
        self.assertIn("internet", query.lower())

    def test_cache_key_is_stable_and_prompt_sensitive(self):
        a = visual_provider._cache_key("pexels", "internet outage")
        b = visual_provider._cache_key("pexels", "internet outage")
        c = visual_provider._cache_key("pexels", "space station")
        self.assertEqual(a, b)
        self.assertNotEqual(a, c)

    def test_cache_round_trip(self):
        with tempfile.TemporaryDirectory() as d:
            original = visual_provider.CACHE_DIR
            try:
                visual_provider.CACHE_DIR = Path(d)
                payload = {"image": "YWJj", "provider": "test", "credit": "", "source_url": ""}
                visual_provider._write_cache("test", "prompt", payload)
                loaded = visual_provider._read_cache("test", "prompt")
                self.assertEqual("YWJj", loaded["image"])
            finally:
                visual_provider.CACHE_DIR = original


if __name__ == "__main__":
    unittest.main()
