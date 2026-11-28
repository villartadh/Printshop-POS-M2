"""Offline orchestration of authenticated subscription verification.

Host-provided authentication, secret lookup, and transport are mandatory
dependencies; none defaults to a live service.
"""
import time
from event_verification import Verification


class SubscriptionLifecycle:
    def __init__(self, store, authenticate, secret_lookup, transport, clock=time.time):
        self.store = store
        self.authenticate = authenticate
        self.secret_lookup = secret_lookup
        self.transport = transport
        self.clock = clock

    def _principal(self, credential):
        principal = self.authenticate(credential)
        if not isinstance(principal, str) or not principal:
            raise PermissionError("Authentication required")
        return principal

    def subscribe(self, credential, callback, event, secret_ref):
        principal = self._principal(credential)
        # Host lookup must enforce ownership of the secret reference.
        secret = self.secret_lookup(principal, secret_ref)
        if not isinstance(secret, str) or not secret:
            raise PermissionError("Signing key unavailable")
        row = self.store.begin(principal, callback, event, secret_ref)
        if row["state"] == "verified":
            return row
        check = Verification.begin(int(self.clock()))
        body, headers = check.request(row["id"], secret, int(self.clock()))
        status, response = self.transport(row["callback"], body, headers)
        if not check.consume(status, response, int(self.clock())):
            raise ValueError("Callback verification failed")
        if not self.store.record_verified(principal, row["id"], row["generation"]):
            raise ValueError("Subscription changed during verification")
        result = self.store.observe(principal, row["id"])
        if result is None or result["state"] != "verified":
            raise ValueError("Subscription changed after verification")
        return result

    def unsubscribe(self, credential, identity, generation):
        return self.store.revoke(self._principal(credential), identity, generation)
