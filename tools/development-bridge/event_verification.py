"""Offline MCP callback verification primitives; no transport or subscription state."""
import base64
import hashlib
import hmac
import json
import re
import secrets
from dataclasses import dataclass

_TOKEN = re.compile(r"[A-Za-z0-9_-]{1,128}")
_MAX_BODY = 256 * 1024


def signed_body(subscription_id, message_id, secret, timestamp, payload):
    if not all(isinstance(x, str) and _TOKEN.fullmatch(x)
               for x in (subscription_id, message_id)):
        raise ValueError("Invalid header identifier")
    if type(timestamp) is not int or timestamp < 0:
        raise ValueError("Invalid signing time")
    if not isinstance(secret, str):
        raise ValueError("Invalid signing key")
    key = base64.b64decode(secret.removeprefix("whsec_"), validate=True)
    if not key:
        raise ValueError("Empty signing key")
    body = json.dumps(payload, ensure_ascii=False, allow_nan=False,
                      separators=(",", ":")).encode("utf-8")
    if len(body) > _MAX_BODY:
        raise ValueError("Payload too large")
    mac = hmac.digest(key, f"{message_id}.{timestamp}.".encode() + body, hashlib.sha256)
    return body, {
        "Content-Type": "application/json",
        "webhook-id": message_id,
        "webhook-timestamp": str(timestamp),
        "webhook-signature": "v1," + base64.b64encode(mac).decode("ascii"),
        "X-MCP-Subscription-Id": subscription_id,
    }


@dataclass
class Verification:
    challenge: str
    expires_at: int
    used: bool = False
    issued: bool = False

    @classmethod
    def begin(cls, now):
        return cls(secrets.token_urlsafe(32), now + 60)

    def request(self, subscription_id, secret, now):
        if self.used or self.issued or now >= self.expires_at:
            raise ValueError("Challenge unavailable")
        request = signed_body(subscription_id, "msg_" + secrets.token_hex(16),
                              secret, now, {"type": "verification", "challenge": self.challenge})
        self.issued = True
        return request

    def consume(self, status, body, now):
        # Consume even on failure; callers must create a fresh challenge.
        if self.used:
            return False
        self.used = True
        if not self.issued or now >= self.expires_at or type(status) is not int or not 200 <= status < 300:
            return False
        if not isinstance(body, bytes) or len(body) > 4096:
            return False
        expected = self.challenge.encode("ascii")
        try:
            pairs = json.loads(body, object_pairs_hook=lambda items: items)
            if (not isinstance(pairs, list) or len(pairs) != 1
                    or not isinstance(pairs[0], tuple) or pairs[0][0] != "challenge"
                    or not isinstance(pairs[0][1], str)):
                return False
            return hmac.compare_digest(expected, pairs[0][1].encode("utf-8"))
        except (ValueError, TypeError, UnicodeError, RecursionError):
            return False
