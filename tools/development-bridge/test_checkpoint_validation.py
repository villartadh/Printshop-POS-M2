import copy
import hashlib
import json
import unittest
from ci_checkpoint import checkpoint, REQUIRED
from checkpoint_validation import validate_checkpoint, MAX_BYTES

A = "a"*40
B = "b"*40

def encode(record):
    return json.dumps(record).encode()

def signed(record):
    record = copy.deepcopy(record)
    record.pop("event_key", None)
    record["event_key"] = hashlib.sha256(json.dumps(record,sort_keys=True,separators=(",",":")).encode()).hexdigest()
    return encode(record)

class ValidationTest(unittest.TestCase):
    def setUp(self):
        self.record = checkpoint(A,[{"head_sha":A,"workflow_id":wid,"event":"pull_request","id":wid,"run_attempt":1,"status":"completed","conclusion":"success"} for wid in REQUIRED.values()])
    def test_roundtrip(self):
        self.assertEqual(validate_checkpoint(encode(self.record), A), self.record)
        self.assertEqual(validate_checkpoint(encode(checkpoint(A,[])), A)["decision"],"wait")
    def test_wrong_sha(self):
        with self.assertRaises(ValueError): validate_checkpoint(encode(self.record),B)
    def test_duplicate(self):
        with self.assertRaises(ValueError): validate_checkpoint(b'{"schema":1,"schema":1}',A)
    def test_invalid_bytes(self):
        for raw in [b"x"*(MAX_BYTES+1),b"\xff",b"null",b"[]"]:
            with self.assertRaises(ValueError): validate_checkpoint(raw,A)
    def test_tamper(self):
        self.record["gates"]["Software acceptance"]["run_id"] += 1
        with self.assertRaises(ValueError): validate_checkpoint(encode(self.record),A)
    def test_rehashed_invalid_decision(self):
        self.record["decision"]="wait"
        with self.assertRaises(ValueError): validate_checkpoint(signed(self.record),A)
    def test_rehashed_invalid_run_identity(self):
        self.record["gates"]["Software acceptance"]["attempt"]=True
        with self.assertRaises(ValueError): validate_checkpoint(signed(self.record),A)
    def test_rehashed_wrong_gate(self):
        self.record["gates"]["untrusted"]=self.record["gates"].pop("Software acceptance")
        with self.assertRaises(ValueError): validate_checkpoint(signed(self.record),A)
    def test_rehashed_wrong_repo(self):
        self.record["repository"]="other/repo"
        with self.assertRaises(ValueError): validate_checkpoint(signed(self.record),A)
