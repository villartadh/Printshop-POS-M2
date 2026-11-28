"""Isolated HTTPS transport pilot; not wired to a live subscription.

The caller must supply fresh connection-time DNS observations. No redirects,
proxies, automatic retries, or second DNS lookup are used.
"""
import http.client
import ipaddress
import socket
import ssl

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
    def __init__(self, destination, timeout, context):
        super().__init__(destination.hostname, destination.port,
                         timeout=timeout, context=context)
        self.destination = destination

    def connect(self):
        raw = _dial(self.destination.addresses[0], self.destination.port, self.timeout)
        try:
            self.sock = self._context.wrap_socket(raw, server_hostname=self.host)
        except BaseException:
            raw.close()
            raise


def post_verified(url, resolved_addresses, body, headers, *, context=None):
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
    conn = _PinnedConnection(destination, 5, context)
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
        return response.status, data
    finally:
        conn.close()
