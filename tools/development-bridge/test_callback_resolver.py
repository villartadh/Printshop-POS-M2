import subprocess
import unittest
from unittest.mock import patch
from callback_resolver import resolve_callback


class ResolverTests(unittest.TestCase):
    def test_pinned_observation(self):
        with patch("callback_resolver.subprocess.run") as child:
            child.return_value = subprocess.CompletedProcess([],0,b'["8.8.8.8"]',b'')
            d=resolve_callback("https://callback.example/cb")
            self.assertEqual(d.addresses,("8.8.8.8",))
            self.assertEqual(child.call_args.args[0][-1],"callback.example")
            self.assertEqual(child.call_args.kwargs["timeout"],3)
            self.assertNotIn("shell",child.call_args.kwargs)

    def test_dns_timeout(self):
        with patch("callback_resolver.subprocess.run",side_effect=subprocess.TimeoutExpired([],3)):
            with self.assertRaises(TimeoutError):
                resolve_callback("https://callback.example")

    def test_mixed_answers(self):
        with patch("callback_resolver.subprocess.run") as child:
            child.return_value=subprocess.CompletedProcess([],0,b'["8.8.8.8","127.0.0.1"]',b'')
            with self.assertRaises(ValueError):
                resolve_callback("https://callback.example")

    def test_invalid_url_never_resolves(self):
        with patch("callback_resolver.subprocess.run") as child:
            with self.assertRaises(ValueError):
                resolve_callback("https://127.0.0.1")
            child.assert_not_called()

    def test_bad_result(self):
        for data in [b'{}', b'bad', b'x'*4097, b'[]']:
            with patch("callback_resolver.subprocess.run") as child:
                child.return_value=subprocess.CompletedProcess([],0,data,b'')
                with self.assertRaises(ValueError):
                    resolve_callback("https://callback.example")

    def test_real_child_timeout(self):
        with patch("callback_resolver._RESOLVE","import time; time.sleep(10)"):
            with self.assertRaises(TimeoutError):
                resolve_callback("https://callback.example",timeout=0.2)

    def test_real_child_private_result(self):
        with patch("callback_resolver._RESOLVE","print('[\"127.0.0.1\"]')"):
            with self.assertRaises(ValueError):
                resolve_callback("https://callback.example")
