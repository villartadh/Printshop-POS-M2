"""Synthetic localhost-only real HTTP MCP transport test."""
import asyncio
import json
import socket
import unittest
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
import httpx
import uvicorn
from ci_checkpoint import checkpoint
from mcp_server import mcp

class HttpMcpTest(unittest.IsolatedAsyncioTestCase):
    async def test_streamable_http_loopback(self):
        sock=socket.socket()
        sock.bind(("127.0.0.1",0)); sock.listen()
        port=sock.getsockname()[1]
        server=uvicorn.Server(uvicorn.Config(mcp.streamable_http_app(), host="127.0.0.1",log_level="error"))
        task=asyncio.create_task(server.serve(sockets=[sock]))
        try:
            async with asyncio.timeout(10):
                while not server.started:
                    if task.done(): await task
                    await asyncio.sleep(0.01)
                async with httpx.AsyncClient(trust_env=False, timeout=5) as client, streamable_http_client(f"http://127.0.0.1:{port}/mcp",http_client=client) as (read,write,_):
                    async with ClientSession(read,write) as session:
                        await session.initialize()
                        tools=await session.list_tools()
                        self.assertEqual({t.name for t in tools.tools},{"inspect_checkpoint","inspect_ref_observation"})
                        sha="a"*40
                        result=await session.call_tool("inspect_checkpoint",{"checkpoint_json":json.dumps(checkpoint(sha,[])),"expected_sha":sha})
                        self.assertFalse(result.isError)
                        self.assertEqual(result.structuredContent["status"],"integrity_valid_only")
                        self.assertFalse(result.structuredContent["authorizes_write"])
        finally:
            server.should_exit=True
            try:
                await asyncio.wait_for(task,5)
            finally:
                sock.close()
