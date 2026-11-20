"""Testes offline, sem enviar audio ou chamar servicos externos."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from reconhecer_audio import parse_result


class ParseResultTests(unittest.TestCase):
    def test_match_with_genre(self):
        data = {"track": {
            "title": "Acurrucar",
            "subtitle": "Ed Maverick",
            "genres": {"primary": "Alternative Folk"},
            "key": "406719839",
        }}
        actual = parse_result(data, "sample.mp3")
        self.assertEqual(actual["status"], "matched")
        self.assertEqual(actual["title"], "Acurrucar")
        self.assertEqual(actual["artist"], "Ed Maverick")
        self.assertEqual(actual["genre"], "Alternative Folk")

    def test_no_match_does_not_invent_metadata(self):
        actual = parse_result({"matches": []}, "unknown.mp3")
        self.assertEqual(actual["status"], "not_found")
        self.assertIsNone(actual["title"])
        self.assertIsNone(actual["genre"])

    def test_genre_may_be_missing(self):
        actual = parse_result({"track": {"title": "Teste"}}, "test.wav")
        self.assertEqual(actual["status"], "matched")
        self.assertIsNone(actual["genre"])


if __name__ == "__main__":
    unittest.main()
