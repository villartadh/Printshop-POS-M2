import unittest
from unittest.mock import patch
from callback_delivery import deliver_callback
from callback_destination import Destination


class DeliveryTests(unittest.TestCase):
    url = "https://callback.example/cb"
    destination = Destination("callback.example",443,"/cb",("8.8.8.8",))

    def test_dns_time_consumes_transport_budget(self):
        with patch("callback_delivery.time.monotonic",side_effect=[100,100,102,103]), \
             patch("callback_delivery.resolve_callback",return_value=self.destination) as resolve, \
             patch("callback_delivery.post_verified",return_value=(200,b"{}")) as post:
            self.assertEqual(deliver_callback(self.url,b"{}",{},timeout=5),(200,b"{}"))
            resolve.assert_called_once_with(self.url,timeout=3)
            post.assert_called_once_with(self.url,("8.8.8.8",),b"{}",{},deadline_seconds=3)

    def test_expired_budget_never_posts(self):
        with patch("callback_delivery.time.monotonic",side_effect=[100,100,102]), \
             patch("callback_delivery.resolve_callback",return_value=self.destination), \
             patch("callback_delivery.post_verified") as post:
            with self.assertRaises(TimeoutError):
                deliver_callback(self.url,b"",{},timeout=2)
            post.assert_not_called()

    def test_late_success_is_not_accepted(self):
        with patch("callback_delivery.time.monotonic",side_effect=[100,100,101,106]), \
             patch("callback_delivery.resolve_callback",return_value=self.destination), \
             patch("callback_delivery.post_verified",return_value=(200,b"{}")):
            with self.assertRaises(TimeoutError):
                deliver_callback(self.url,b"",{},timeout=5)

    def test_resolver_failure_never_posts(self):
        with patch("callback_delivery.resolve_callback",side_effect=ValueError("unsafe DNS")), \
             patch("callback_delivery.post_verified") as post:
            with self.assertRaises(ValueError):
                deliver_callback(self.url,b"",{})
            post.assert_not_called()

    def test_invalid_budget_never_resolves(self):
        with patch("callback_delivery.resolve_callback") as resolve:
            for value in [True, 0, -1, 11, float("nan"), float("inf")]:
                with self.assertRaises(ValueError):
                    deliver_callback(self.url,b"",{},timeout=value)
            resolve.assert_not_called()
