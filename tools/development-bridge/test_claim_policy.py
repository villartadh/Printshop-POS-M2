import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from claim_policy import new_ledger, claim, complete

A="a"*64; B="b"*64; SHA="c"*40; W="worker-1234567890"; X="worker-0987654321"

class ClaimTest(unittest.TestCase):
    def test_claim_immutable(self):
        state=new_ledger(); acquired=claim(state,A,SHA,W,"review")
        self.assertIsNone(state["active"])
        self.assertEqual(acquired["active"]["generation"],1)
    def test_single_writer(self):
        state=claim(new_ledger(),A,SHA,W,"review")
        with self.assertRaises(ValueError): claim(state,B,SHA,X,"review")
    def test_no_automatic_steal(self):
        state=claim(new_ledger(),A,SHA,W,"review")
        with self.assertRaises(ValueError): claim(state,A,SHA,W,"review")
    def test_completion(self):
        state=complete(claim(new_ledger(),A,SHA,W,"review"),A,W,1,"reviewed")
        self.assertEqual(state["completed"],{A:"reviewed"})
        self.assertIsNone(state["active"])
        with self.assertRaises(ValueError): claim(state,A,SHA,W,"review")
        self.assertEqual(claim(state,B,SHA,X,"diagnose")["generation"],3)
    def test_stale_owner(self):
        state=claim(new_ledger(),A,SHA,W,"diagnose")
        for args in [(A,X,1,"diagnosed"),(A,W,0,"diagnosed"),(B,W,1,"diagnosed"),(A,W,1,"reviewed")]:
            with self.assertRaises(ValueError): complete(state,*args)
    def test_invalid(self):
        for args in [("invalid",SHA,W,"review"),(A,"main",W,"review"),(A,SHA,"x","review"),(A,SHA,W,"wait")]:
            with self.assertRaises(ValueError): claim(new_ledger(),*args)
    def test_persisted_roundtrip(self):
        state=claim(new_ledger(),A,SHA,W,"review")
        self.assertEqual(complete(json.loads(json.dumps(state)),A,W,1,"reviewed")["completed"],{A:"reviewed"})
    def test_git_compare_and_swap_two_writers(self):
        # Actual isolated bare repository; both proposed commits have the same parent.
        # A non-force push accepts exactly one; losing worker cannot acquire ownership.
        with tempfile.TemporaryDirectory() as root:
            root=Path(root); remote=root/"remote.git"; one=root/"one"; two=root/"two"
            def git(cwd,*args,ok=True):
                p=subprocess.run(["git",*args],cwd=cwd,capture_output=True,text=True)
                if ok and p.returncode: self.fail(p.stderr)
                return p
            git(root,"init","--bare",str(remote))
            git(root,"clone",str(remote),str(one))
            for repo in [one]:
                git(repo,"config","user.name","Synthetic");git(repo,"config","user.email","synthetic@example.invalid")
            (one/"ledger.json").write_text(json.dumps(new_ledger()))
            git(one,"add","ledger.json");git(one,"commit","-m","Initial ledger")
            git(one,"push","origin","HEAD:refs/heads/ledger")
            git(root,"clone","--branch","ledger",str(remote),str(two))
            git(two,"config","user.name","Synthetic");git(two,"config","user.email","synthetic@example.invalid")
            for repo,event,worker in [(one,A,W),(two,B,X)]:
                (repo/"ledger.json").write_text(json.dumps(claim(new_ledger(),event,SHA,worker,"review")))
                git(repo,"add","ledger.json");git(repo,"commit","-m","Claim")
            self.assertEqual(git(one,"push","origin","HEAD:refs/heads/ledger").returncode,0)
            self.assertNotEqual(git(two,"push","origin","HEAD:refs/heads/ledger",ok=False).returncode,0)
            git(two,"fetch","origin","ledger")
            actual=json.loads(git(two,"show","FETCH_HEAD:ledger.json").stdout)
            self.assertEqual(actual["active"]["worker"],W)

if __name__=="__main__": unittest.main()
