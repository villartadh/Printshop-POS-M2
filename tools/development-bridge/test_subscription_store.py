import tempfile
import unittest
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from subscription_store import SubscriptionStore


class SubscriptionTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path=Path(tmp.name)/"subscriptions.db"
        self.store=SubscriptionStore(self.path)

    def begin(self, principal="owner", secret_ref="synthetic_key_ref"):
        return self.store.begin(principal,"https://callback.example/cb","ci_completed",secret_ref)

    def test_restart_and_idempotency(self):
        row=self.begin()
        self.assertEqual(self.begin(),row)
        fresh=SubscriptionStore(self.path)
        self.assertEqual(fresh.observe("owner",row["id"]),row)
        self.assertEqual(row["state"],"pending")

    def test_principal_isolation(self):
        row=self.begin()
        other=self.begin("other")
        self.assertNotEqual(row["id"],other["id"])
        self.assertIsNone(self.store.observe("other",row["id"]))
        self.assertFalse(self.store.record_verified("other",row["id"],1))
        self.assertFalse(self.store.revoke("other",row["id"],1))

    def test_generation_fence(self):
        row=self.begin()
        self.assertTrue(self.store.record_verified("owner",row["id"],1))
        self.assertFalse(self.store.revoke("owner",row["id"],1))
        self.assertTrue(self.store.revoke("owner",row["id"],2))
        self.assertFalse(self.store.record_verified("owner",row["id"],3))
        with self.assertRaises(ValueError):
            self.begin()

    def test_concurrent_idempotent_creation(self):
        with ThreadPoolExecutor(max_workers=4) as pool:
            rows=list(pool.map(lambda _: self.begin(),range(8)))
        self.assertTrue(all(row==rows[0] for row in rows))

    def test_competing_transitions(self):
        row=self.begin()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda _:self.store.record_verified("owner",row["id"],1),range(2)))
        self.assertEqual(sorted(results),[False,True])

    def test_secret_rotation_requires_explicit_replacement(self):
        self.begin()
        with self.assertRaises(ValueError):
            self.begin(secret_ref="another_ref")
