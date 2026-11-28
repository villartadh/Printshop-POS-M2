import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from subscription_lifecycle import SubscriptionLifecycle
from subscription_store import SubscriptionStore


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.store = SubscriptionStore(Path(tmp.name)/"subscriptions.db")
        self.auth = Mock(side_effect=lambda c: {"owner_token":"owner","other_token":"other"}.get(c))
        self.keys = Mock(side_effect=lambda p,r: "whsec_c3ludGhldGlj" if p=="owner" and r=="ref" else None)
        self.transport = Mock(side_effect=lambda url,body,headers:
            (200,json.dumps({"challenge":json.loads(body)["challenge"]}).encode()))
        self.lifecycle = SubscriptionLifecycle(self.store,self.auth,self.keys,self.transport,clock=lambda:100)

    def subscribe(self, credential="owner_token"):
        return self.lifecycle.subscribe(credential,"https://callback.example/cb","ci_completed","ref")

    def test_challenge_and_idempotency(self):
        row=self.subscribe()
        self.assertEqual(row["state"],"verified")
        self.assertEqual(row["generation"],2)
        self.assertEqual(self.subscribe(),row)
        self.transport.assert_called_once()
        self.assertEqual(self.transport.call_args.args[2]["X-MCP-Subscription-Id"],row["id"])

    def test_authentication_rejected_before_lookup(self):
        with self.assertRaises(PermissionError):
            self.subscribe("invalid")
        self.keys.assert_not_called()
        self.transport.assert_not_called()

    def test_unauthorized_secret_reference(self):
        with self.assertRaises(PermissionError):
            self.subscribe("other_token")
        self.transport.assert_not_called()

    def test_wrong_challenge_stays_pending(self):
        self.transport.side_effect=None
        self.transport.return_value=(200,b'{"challenge":"wrong"}')
        with self.assertRaisesRegex(ValueError,"verification failed"):
            self.subscribe()
        row=self.store.begin("owner","https://callback.example/cb","ci_completed","ref")
        self.assertEqual(row["state"],"pending")

    def test_revocation_during_callback(self):
        def race(url,body,headers):
            self.assertTrue(self.store.revoke("owner",headers["X-MCP-Subscription-Id"],1))
            return 200,json.dumps({"challenge":json.loads(body)["challenge"]}).encode()
        self.transport.side_effect=race
        with self.assertRaisesRegex(ValueError,"changed"):
            self.subscribe()

    def test_unsubscribe_principal_scope(self):
        row=self.subscribe()
        self.assertFalse(self.lifecycle.unsubscribe("other_token",row["id"],2))
        self.assertTrue(self.lifecycle.unsubscribe("owner_token",row["id"],2))
        with self.assertRaises(ValueError):
            self.subscribe()
