"""Synthetic real-Git atomic multi-ref fencing; no external repository or credentials."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

class AtomicFenceTest(unittest.TestCase):
    def test_stale_owner_cannot_publish_after_new_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); remote=root/"remote.git"; worker=root/"worker"
            def git(cwd,*args,ok=True):
                p=subprocess.run(["git",*args],cwd=cwd,capture_output=True,text=True)
                if ok and p.returncode: self.fail(p.stderr)
                return p
            git(root,"init","--bare",str(remote))
            git(root,"clone",str(remote),str(worker))
            git(worker,"config","user.name","Synthetic")
            git(worker,"config","user.email","synthetic@example.invalid")
            def commit(path,value,message):
                (worker/path).write_text(value)
                git(worker,"add",path);git(worker,"commit","-m",message)
                return git(worker,"rev-parse","HEAD").stdout.strip()
            source=commit("source.txt","initial","source")
            git(worker,"push","origin","HEAD:refs/heads/source")
            git(worker,"checkout","--orphan","ledger")
            git(worker,"rm","-rf",".")
            claim=commit("ledger.json",json.dumps({"owner":"old","generation":1}),"claim")
            git(worker,"push","origin","HEAD:refs/heads/ledger")
            newer=commit("ledger.json",json.dumps({"owner":"new","generation":2}),"new owner")
            git(worker,"push","origin","HEAD:refs/heads/ledger")
            git(worker,"checkout","--detach",source)
            stale_source=commit("source.txt","stale change","stale write")
            git(worker,"checkout","--detach",claim)
            stale_ledger=commit("ledger.json",json.dumps({"owner":"old","generation":1,"done":True}),"stale completion")
            result=git(worker,"push","--atomic","origin",
                f"--force-with-lease=refs/heads/source:{source}",
                f"--force-with-lease=refs/heads/ledger:{claim}",
                f"{stale_source}:refs/heads/source",f"{stale_ledger}:refs/heads/ledger",ok=False)
            self.assertNotEqual(result.returncode,0)
            self.assertEqual(git(remote,"rev-parse","refs/heads/source").stdout.strip(),source)
            self.assertEqual(git(remote,"rev-parse","refs/heads/ledger").stdout.strip(),newer)
            git(worker,"checkout","--detach",source)
            good_source=commit("source.txt","new change","new write")
            git(worker,"checkout","--detach",newer)
            good_ledger=commit("ledger.json",json.dumps({"owner":"new","generation":2,"done":True}),"new completion")
            git(worker,"push","--atomic","origin",
                f"--force-with-lease=refs/heads/source:{source}",
                f"--force-with-lease=refs/heads/ledger:{newer}",
                f"{good_source}:refs/heads/source",f"{good_ledger}:refs/heads/ledger")
            self.assertEqual(git(remote,"rev-parse","refs/heads/source").stdout.strip(),good_source)
            self.assertEqual(git(remote,"rev-parse","refs/heads/ledger").stdout.strip(),good_ledger)
