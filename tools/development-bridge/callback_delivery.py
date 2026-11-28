"""Unwired callback delivery pilot using one monotonic DNS/TLS budget."""
import time

from callback_resolver import resolve_callback
from callback_transport import post_verified


def deliver_callback(url, body, headers, *, timeout=10):
    if type(timeout) not in (float, int) or not 0 < timeout <= 10:
        raise ValueError("Invalid delivery budget")
    deadline = time.monotonic() + timeout

    def remaining():
        budget = deadline - time.monotonic()
        if budget <= 0:
            raise TimeoutError("Callback delivery deadline exceeded")
        return budget

    # DNS replies are never cached or accepted from the event payload.
    destination = resolve_callback(url, timeout=min(3, remaining()))
    result = post_verified(url, destination.addresses, body, headers,
                           deadline_seconds=remaining())
    remaining()
    return result
