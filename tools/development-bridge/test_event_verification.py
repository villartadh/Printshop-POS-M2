import base64
import hashlib
import hmac
import json
import unittest

from event_verification import Verification, signed_body


class EventVerificationTests(unittest.TestCase):
    secret = "whsec_" + base64.b64encode(b"synthetic key only").decode()

    def test_exact_byte_signature(self):
        body, headers = signed_body("sub_1", "msg_1", self.secret, 100, {"test": "é"})
        self.assertEqual(body, '{"test":"é"}'.encode())
        expected = hmac.new(b"synthetic key only", b"msg_1.100." + body, hashlib.sha256).digest()
        self.assertEqual(headers["webhook-signature"], "v1," + base64.b64encode(expected).decode())
        self.assertEqual(headers["X-MCP-Subscription-Id"], "sub_1")

    def test_challenge_single_use(self):
        challenge = Verification.begin(100)
        body, headers = challenge.request("sub_1", self.secret, 100)
        self.assertEqual(json.loads(body), {"type": "verification", "challenge": challenge.challenge})
        self.assertTrue(headers["webhook-id"].startswith("msg_"))
        with self.assertRaises(ValueError):
            challenge.request("sub_1", self.secret, 100)
        response = json.dumps({"challenge": challenge.challenge}).encode()
        self.assertTrue(challenge.consume(200, response, 101))
        self.assertFalse(challenge.consume(200, response, 101))

    def test_reject_invalid_response(self):
        for status, body, now in [(302,b'{}',101),(200,b'{}',101),
            (200,b'{"challenge":"wrong"}',101),(200,b'{"challenge":"a","challenge":"a"}',101),
            (200,b'["challenge","a"]',101),(200,b'x'*4097,101),
            (200,b'{"challenge":"a"}',160),(200,b'\xff',101)]:
            challenge = Verification("a",160,issued=True)
            self.assertFalse(challenge.consume(status,body,now))
            self.assertFalse(challenge.consume(200,b'{"challenge":"a"}',101))

    def test_reject_invalid_input(self):
        for args in [("sub\n1","msg",self.secret,100,{}),
                     ("sub","msg","whsec_",100,{}),
                     ("sub","msg",self.secret,True,{}),
                     ("sub","msg",self.secret,100,{"huge":"x"*262144})]:
            with self.assertRaises(ValueError):
                signed_body(*args)

    def test_fresh_challenge(self):
        a,b = Verification.begin(100),Verification.begin(100)
        self.assertNotEqual(a.challenge,b.challenge)
        with self.assertRaises(ValueError):
            a.request("sub",self.secret,160)
