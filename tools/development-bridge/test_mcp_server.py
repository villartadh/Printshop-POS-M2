"""Real MCP SDK client over stdio; synthetic input only."""
import json
import sys
import unittest
from pathlib import Path
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from ci_checkpoint import checkpoint

A="a"*40; B="b"*40

class McpTest(unittest.IsolatedAsyncioTestCase):
    async def test_stdio_tool_roundtrip(self):
        server = StdioServerParameters(command=sys.executable,args=[str(Path(__file__).with_name("mcp_server.py"))])
        async with stdio_client(server) as (read,write):
            async with ClientSession(read,write) as session:
                await session.initialize()
                tools=await session.list_tools()
                self.assertEqual({t.name for t in tools.tools},{"inspect_checkpoint","inspect_ref_observation"})
                for tool in tools.tools:
                    self.assertTrue(tool.annotations.readOnlyHint)
                result=await session.call_tool("inspect_checkpoint",{"checkpoint_json":json.dumps(checkpoint(A,[])),"expected_sha":A})
                self.assertFalse(result.isError)
                self.assertEqual(result.structuredContent["status"],"integrity_valid_only")
                self.assertFalse(result.structuredContent["authorizes_write"])
                invalid=await session.call_tool("inspect_checkpoint",{"checkpoint_json":"{}","expected_sha":A})
                self.assertEqual(invalid.structuredContent["status"],"invalid_checkpoint")
                ref=await session.call_tool("inspect_ref_observation",{"expected":A,"proposed":B,"observed":B})
                self.assertEqual(ref.structuredContent["status"],"exact_candidate_observed")
                self.assertFalse(ref.structuredContent["authorizes_write"])
