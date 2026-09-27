import os
import sys
import unittest
from pathlib import Path

BACKEND_DIR=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(BACKEND_DIR))

import avatar_provider

class AvatarProviderTests(unittest.TestCase):
    def test_payload_uses_microsoft_male_arabic_voice(self):
        payload=avatar_provider._talk_payload(
            "مرحبا بكم",
            "https://example.com/man.png",
            "ar-SA-HamedNeural",
            0.95,
        )
        provider=payload["script"]["provider"]
        self.assertEqual("microsoft",provider["type"])
        self.assertEqual("ar-SA-HamedNeural",provider["voice_id"])
        self.assertEqual("https://example.com/man.png",payload["source_url"])

    def test_payload_rejects_empty_text(self):
        with self.assertRaises(RuntimeError):
            avatar_provider._talk_payload(
                "   ",
                "https://example.com/man.png",
                "ar-SA-HamedNeural",
                1.0,
            )

    def test_settings_require_server_secrets(self):
        previous={k:os.environ.get(k) for k in ("DID_API_KEY","DID_PRESENTER_URL")}
        try:
            os.environ.pop("DID_API_KEY",None)
            os.environ.pop("DID_PRESENTER_URL",None)
            with self.assertRaises(RuntimeError):
                avatar_provider._settings()
        finally:
            for key,value in previous.items():
                if value is None:
                    os.environ.pop(key,None)
                else:
                    os.environ[key]=value

if __name__=="__main__":
    unittest.main()
