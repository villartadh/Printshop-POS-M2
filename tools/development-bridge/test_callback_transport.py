"""Real TLS over a synthetic socket pair; no external network or DNS."""
import shutil
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from callback_transport import post_verified
from event_verification import signed_body


@unittest.skipUnless(shutil.which("openssl"), "openssl needed for synthetic TLS fixture")
class CallbackTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.tmp.cleanup)
        p = Path(cls.tmp.name)
        cert, key = p / "cert.pem", p / "key.pem"
        subprocess.run(["openssl","req","-x509","-newkey","rsa:2048","-nodes",
                        "-keyout",str(key),"-out",str(cert),"-days","1",
                        "-subj","/CN=callback.example",
                        "-addext","subjectAltName=DNS:callback.example"],
                       check=True, capture_output=True)
        cls.server = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        cls.server.load_cert_chain(cert, key)
        cls.client = ssl.create_default_context(cafile=str(cert))

    def exchange(self, response, host="callback.example", deadline=10):
        a,b = socket.socketpair()
        a.settimeout(3)
        b.settimeout(3)
        captured = []
        def serve():
            try:
                with self.server.wrap_socket(b, server_side=True) as tls:
                    data = b""
                    while b"\r\n\r\n" not in data:
                        chunk = tls.recv(4096)
                        if not chunk:
                            return
                        data += chunk
                    head, payload = data.split(b"\r\n\r\n",1)
                    length = int(next(x.split(b":",1)[1] for x in head.split(b"\r\n")
                                      if x.lower().startswith(b"content-length:")))
                    while len(payload)<length:
                        chunk = tls.recv(4096)
                        if not chunk:
                            return
                        payload += chunk
                    captured.append((head,payload))
                    if isinstance(response, bytes):
                        tls.sendall(response)
                    else:
                        response(tls)
            except (OSError, ssl.SSLError):
                pass
            finally:
                b.close()
        t=threading.Thread(target=serve,daemon=True)
        t.start()
        body,headers=signed_body("sub","msg","whsec_c3ludGhldGlj",100,{"type":"verification"})
        try:
            with patch("callback_transport._dial", return_value=a) as dial:
                try:
                    result=post_verified("https://"+host+"/cb",["8.8.8.8"],body,headers,context=self.client,deadline_seconds=deadline)
                finally:
                    dial.assert_called_once()
                    self.assertEqual(dial.call_args.args[:2], ("8.8.8.8",443))
                    self.assertGreater(dial.call_args.args[2],0)
                    self.assertLessEqual(dial.call_args.args[2],min(5,deadline))
            return result,captured,body
        finally:
            a.close()
            t.join(4)

    def test_real_tls_signed_post(self):
        result,captured,body=self.exchange(b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")
        self.assertEqual(result,(200,b"{}"))
        self.assertIn(b"POST /cb HTTP/1.1",captured[0][0])
        self.assertIn(b"Host: callback.example",captured[0][0])
        self.assertIn(b"webhook-signature: v1,",captured[0][0])
        self.assertEqual(captured[0][1],body)

    def test_hostname_mismatch(self):
        with self.assertRaises(ssl.SSLCertVerificationError):
            self.exchange(b"",host="wrong.example")

    def test_redirect_refused(self):
        with self.assertRaisesRegex(ValueError,"Redirect"):
            self.exchange(b"HTTP/1.1 302 Found\r\nLocation: https://other.example/\r\nContent-Length: 0\r\n\r\n")

    def test_oversize_refused(self):
        with self.assertRaisesRegex(ValueError,"too large"):
            self.exchange(b"HTTP/1.1 200 OK\r\nContent-Length: 4097\r\n\r\n")

    def test_private_address_never_dials(self):
        with patch("callback_transport._dial") as dial, self.assertRaises(ValueError):
            post_verified("https://callback.example",["127.0.0.1"],b"",{})
        dial.assert_not_called()

    def test_drip_feed_absolute_deadline(self):
        def drip(tls):
            tls.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n")
            for _ in range(100):
                tls.sendall(b"x")
                time.sleep(0.04)
        start = time.monotonic()
        with self.assertRaises(TimeoutError):
            self.exchange(drip,deadline=0.35)
        self.assertLess(time.monotonic()-start,2)
