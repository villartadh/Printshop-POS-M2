"""Bounded system DNS resolution pilot; no callback dispatch is enabled."""
import json
import subprocess
import sys
import time

from callback_destination import parse_callback_url, validate_destination

# Fixed child code: the hostname is an argument, never executable code.
_RESOLVE = """
import json, socket, sys
records = socket.getaddrinfo(sys.argv[1] + '.', 443,
    type=socket.SOCK_STREAM, proto=socket.IPPROTO_TCP)
addresses = sorted({r[4][0] for r in records})
if not 1 <= len(addresses) <= 32:
    raise ValueError('invalid DNS result count')
print(json.dumps(addresses))
"""


def resolve_callback(url, *, timeout=3):
    host, _ = parse_callback_url(url)
    if type(timeout) not in (int, float) or not 0 < timeout <= 5:
        raise ValueError("Invalid DNS deadline")
    deadline = time.monotonic() + timeout
    # Process isolation permits terminating a blocked OS resolver rather than
    # leaving an unbounded background thread. No shell and no environment code.
    try:
        result = subprocess.run([sys.executable, "-I", "-c", _RESOLVE, host],
                                capture_output=True, timeout=timeout, check=True)
    except subprocess.TimeoutExpired as exc:
        raise TimeoutError("Callback DNS deadline exceeded") from exc
    except subprocess.CalledProcessError as exc:
        raise ValueError("Callback DNS resolution failed") from exc
    if time.monotonic() >= deadline:
        raise TimeoutError("Callback DNS deadline exceeded")
    if len(result.stdout) > 4096:
        raise ValueError("Invalid DNS response")
    try:
        addresses = json.loads(result.stdout)
    except (ValueError, UnicodeError) as exc:
        raise ValueError("Invalid DNS response") from exc
    return validate_destination(url, addresses)
