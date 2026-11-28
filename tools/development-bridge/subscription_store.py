"""Synthetic durable subscription metadata; no secrets or live activation.

The caller must authenticate the principal and verify callback challenges.
This store alone never authorizes delivery or source mutation.
"""
import hashlib
import json
import re
import sqlite3
from contextlib import closing
from callback_destination import parse_callback_url

_TOKEN = re.compile(r"[A-Za-z0-9_-]{1,128}")


def _token(value):
    if not isinstance(value, str) or not _TOKEN.fullmatch(value):
        raise ValueError("Invalid identifier")
    return value


class SubscriptionStore:
    def __init__(self, path):
        self.path = path
        with closing(self._connect()) as db, db:
            db.execute("""CREATE TABLE IF NOT EXISTS subscriptions (
                id TEXT PRIMARY KEY, principal TEXT NOT NULL,
                callback TEXT NOT NULL, event TEXT NOT NULL,
                secret_ref TEXT NOT NULL, generation INTEGER NOT NULL,
                state TEXT NOT NULL CHECK(state IN ('pending','verified','revoked')),
                UNIQUE(principal, callback, event))""")

    def _connect(self):
        db = sqlite3.connect(self.path, timeout=5)
        db.row_factory = sqlite3.Row
        return db

    def begin(self, principal, callback, event, secret_ref):
        principal, event, secret_ref = map(_token, (principal,event,secret_ref))
        parse_callback_url(callback)
        identity = hashlib.sha256(json.dumps([principal,callback,event],
                    separators=(",",":")).encode()).hexdigest()
        with closing(self._connect()) as db, db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM subscriptions WHERE id=?", (identity,)).fetchone()
            if row is None:
                db.execute("INSERT INTO subscriptions VALUES (?,?,?,?,?,1,'pending')",
                           (identity,principal,callback,event,secret_ref))
            elif row["state"] == "revoked" or row["secret_ref"] != secret_ref:
                raise ValueError("Explicit replacement required")
            return dict(db.execute("SELECT * FROM subscriptions WHERE id=?", (identity,)).fetchone())

    def observe(self, principal, identity):
        _token(principal)
        _token(identity)
        with closing(self._connect()) as db:
            row = db.execute("SELECT * FROM subscriptions WHERE id=? AND principal=?",
                             (identity,principal)).fetchone()
            return dict(row) if row else None

    def record_verified(self, principal, identity, generation):
        # This records the trusted caller's observation; not a callback check.
        return self._transition(principal,identity,generation,"pending","verified")

    def revoke(self, principal, identity, generation):
        return self._transition(principal,identity,generation,None,"revoked")

    def _transition(self, principal, identity, generation, before, after):
        _token(principal)
        _token(identity)
        if type(generation) is not int or generation < 1:
            raise ValueError("Invalid generation")
        with closing(self._connect()) as db, db:
            sql = "UPDATE subscriptions SET state=?, generation=generation+1 WHERE principal=? AND id=? AND generation=?"
            args = [after,principal,identity,generation]
            if before:
                sql += " AND state=?"
                args.append(before)
            else:
                sql += " AND state!='revoked'"
            return db.execute(sql,args).rowcount == 1
