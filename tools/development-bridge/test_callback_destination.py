import unittest
from callback_destination import validate_destination


class CallbackDestinationTests(unittest.TestCase):
    def test_public_synthetic_observation(self):
        d = validate_destination("https://callback.example/path?x=1", ["8.8.8.8", "2606:4700::1111"])
        self.assertEqual((d.hostname, d.port, d.target), ("callback.example",443,"/path?x=1"))
        self.assertEqual(d.addresses, ("8.8.8.8","2606:4700::1111"))

    def test_mixed_dns_results_fail_closed(self):
        for addr in ["127.0.0.1","10.0.0.1","100.64.0.1","169.254.169.254",
                     "192.0.2.1","224.0.0.1","0.0.0.0","::1","fc00::1",
                     "fe80::1%eth0","ff02::1","::ffff:8.8.8.8","2002:0808:0808::1",
                     "64:ff9b::808:808","64:ff9b:1::1","2001::1","3fff::1"]:
            with self.subTest(addr=addr), self.assertRaises(ValueError):
                validate_destination("https://callback.example/", ["8.8.8.8",addr])

    def test_url_rejections(self):
        for url in ["http://callback.example","https://localhost","https://127.0.0.1",
                    "https://callback.example:444","https://callback.example:","https://x@callback.example",
                    "https://callback.example#fragment","https://callback.example/\n",
                    "https://callback.example\\@other.example","https://callback.example.",
                    "https://callback.example%00","https://é.example"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                validate_destination(url, ["8.8.8.8"])

    def test_missing_observation(self):
        for values in [[],["8.8.8.8"]*33,None,["invalid"]]:
            with self.assertRaises(ValueError):
                validate_destination("https://callback.example", values)

    def test_dedup_and_default_path(self):
        d=validate_destination("https://CALLBACK.example:443", ["8.8.8.8"]*2)
        self.assertEqual(d.addresses,("8.8.8.8",))
        self.assertEqual(d.target,"/")
