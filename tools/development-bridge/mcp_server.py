"""Read-only stdio MCP adapter; no tokens, network access, mutations or event triggers."""
from mcp.server.fastmcp import FastMCP
from mcp.types import ToolAnnotations
from pydantic import BaseModel
from checkpoint_validation import validate_checkpoint
from reconcile_policy import reconcile_ref

mcp = FastMCP("Development Automation Bridge")
class Inspection(BaseModel):
    status: str
    authorizes_write: bool = False
    checkpoint: dict | None = None

READ_ONLY = ToolAnnotations(readOnlyHint=True, destructiveHint=False, openWorldHint=False)

@mcp.tool(annotations=READ_ONLY)
def inspect_checkpoint(checkpoint_json: str, expected_sha: str) -> Inspection:
    """Validate supplied checkpoint JSON only. Does not authenticate or verify freshness."""
    try:
        record = validate_checkpoint(checkpoint_json.encode("utf-8"), expected_sha)
    except (ValueError, UnicodeError, RecursionError):
        return Inspection(status="invalid_checkpoint")
    return Inspection(status="integrity_valid_only",checkpoint=record)

@mcp.tool(annotations=READ_ONLY)
def inspect_ref_observation(expected: str, proposed: str, observed: str | None = None) -> Inspection:
    """Classify caller-supplied ref identities, not a live GitHub read or write authorization."""
    try:
        decision = reconcile_ref(expected, proposed, observed)
    except ValueError:
        return Inspection(status="invalid_identity")
    return Inspection(status=decision)

if __name__ == "__main__":
    mcp.run(transport="stdio")
