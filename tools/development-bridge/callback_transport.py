"""Isolated HTTPS transport pilot; not wired to a live subscription.

The caller must supply fresh connection-time DNS observations. No redirects,
proxies, automatic retries, or second DNS lookup are used.
"""
import http.client
import ipaddress
import socket
import ssl
import threading
import time

from callback_destination import validate_destination

_MAX_RESPONSE = 4096
_ALLOWED_HEADERS = {"content-type", "webhook-id", "webhook-timestamp",
                    "webhook-signature", "x-mcp-subscription-id"}


def _dial(address, port, timeout):
    ip = ipaddress.ip_address(address)
    sock = socket.socket(socket.AF_INET if ip.version == 4 else socket.AF_INET6,
                         socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect((str(ip), port))
        return sock
    except BaseException:
        sock.close()
        raise


class _PinnedConnection(http.client.HTTPSConnection):
    def __init__(self, destination, timeout, context, deadline):
        super().__init__(destination.hostname, destination.port,
                         timeout=timeout, context=context)
        self.destination = destination
        self.deadline = deadline
        self.guard = None
        self.timer = None

    def connect(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("Callback deadline exceeded")
        raw = _dial(self.destination.addresses[0], self.destination.port, min(self.timeout, remaining))
        try:
            remaining = self.deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Callback deadline exceeded")
            # The duplicate owns the same underlying connection even when SSL
            # detaches the raw socket during wrapping. Shutdown wakes blocked I/O.
            self.guard = raw.dup()
            def expire():
                try:
                    self.guard.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            self.timer = threading.Timer(remaining, expire)
            self.timer.daemon = True
            self.timer.start()
            self.sock = self._context.wrap_socket(raw, server_hostname=self.host)
        except BaseException:
            raw.close()
            raise

    def finish(self):
        if self.timer is not None:
            self.timer.cancel()
            self.timer.join()
        if self.guard is not None:
            self.guard.close()
        self.close()


def post_verified(url, resolved_addresses, body, headers, *, context=None, deadline_seconds=10):
    if type(deadline_seconds) not in (int, float) or not 0 < deadline_seconds <= 10:
        raise ValueError("Invalid callback deadline")
    deadline = time.monotonic() + deadline_seconds
    destination = validate_destination(url, resolved_addresses)
    if not isinstance(body, bytes) or len(body) > 256 * 1024:
        raise ValueError("Invalid callback body")
    if (not isinstance(headers, dict)
            or {k.lower() for k in headers} != _ALLOWED_HEADERS
            or len(headers) != len(_ALLOWED_HEADERS)
            or any(not isinstance(v, str) or not v.isascii()
                   or any(ord(c) < 32 or ord(c) == 127 for c in v)
                   for v in headers.values())):
        raise ValueError("Invalid callback headers")
    context = context or ssl.create_default_context()
    if not context.check_hostname or context.verify_mode != ssl.CERT_REQUIRED:
        raise ValueError("TLS verification required")
    conn = _PinnedConnection(destination, 5, context, deadline)
    response = None
    try:
        conn.request("POST", destination.target, body=body, headers=headers)
        response = conn.getresponse()
        if 300 <= response.status < 400:
            raise ValueError("Redirect forbidden")
        if response.length is not None and response.length > _MAX_RESPONSE:
            raise ValueError("Response too large")
        data = response.read(_MAX_RESPONSE + 1)
        if len(data) > _MAX_RESPONSE:
            raise ValueError("Response too large")
        if time.monotonic() >= deadline:
            raise TimeoutError("Callback deadline exceeded")
        return response.status, data
    except (OSError, http.client.HTTPException, ValueError) as exc:
        if time.monotonic() >= deadline:
            raise TimeoutError("Callback deadline exceeded") from exc
        raise
    finally:
        if response is not None:
            response.close()
        conn.finish()
