"""Offline validation of callback URL and connection-time DNS observations.

No DNS resolution or connection is performed. A transport must connect to one
of the returned addresses, preserve TLS hostname verification and reject redirects.
"""
from dataclasses import dataclass
import ipaddress
import re
from urllib.parse import urlsplit

_LABEL = re.compile(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
_MAX_ADDRESSES = 32


@dataclass(frozen=True)
class Destination:
    hostname: str
    port: int
    target: str
    addresses: tuple[str, ...]


def parse_callback_url(url):
    if (not isinstance(url, str) or len(url) > 4096
            or not url.isascii() or any(ord(c) <= 32 or ord(c) == 127 for c in url)
            or "\\" in url or "#" in url):
        raise ValueError("Invalid callback URL")
    u = urlsplit(url)
    host = u.hostname
    if (u.scheme != "https" or not host or u.username is not None
            or u.password is not None or u.port not in (None, 443)
            or u.netloc.lower() not in (host, host + ":443")):
        raise ValueError("Only HTTPS DNS callbacks on port 443 are supported")
    if len(host) > 253 or "." not in host or not all(
            _LABEL.fullmatch(label) for label in host.split(".")):
        raise ValueError("Invalid DNS name")
    # IP literals, single-label names and ambiguous numeric hostnames are rejected.
    if host.split(".")[-1].isdigit():
        raise ValueError("Numeric hostname")
    return host, (u.path or "/") + ("?" + u.query if u.query else "")


def validate_destination(url, resolved_addresses):
    host, target = parse_callback_url(url)
    if not isinstance(resolved_addresses, (tuple, list)) or not 1 <= len(resolved_addresses) <= _MAX_ADDRESSES:
        raise ValueError("Missing or excessive DNS results")
    addresses = []
    for value in resolved_addresses:
        if not isinstance(value, str) or "%" in value:
            raise ValueError("Invalid address")
        address = ipaddress.ip_address(value)
        # Reject transition mechanisms and special-purpose ranges in addition to
        # the standard library's global check (multicast can report as global).
        if (not address.is_global or address.is_multicast or address.is_reserved
                or address.is_loopback or address.is_link_local or address.is_unspecified
                or (address.version == 6 and (address not in ipaddress.ip_network("2000::/3")
                    or address in ipaddress.ip_network("2001::/23")
                    or address in ipaddress.ip_network("3fff::/20")
                    or address.ipv4_mapped is not None
                    or address.sixtofour is not None or address.teredo is not None
                    or address in ipaddress.ip_network("64:ff9b::/96")
                    or address in ipaddress.ip_network("64:ff9b:1::/48")))):
            raise ValueError("Non-public or unsupported callback address")
        addresses.append(str(address))
    return Destination(host, 443, target,
                       tuple(dict.fromkeys(addresses)))
