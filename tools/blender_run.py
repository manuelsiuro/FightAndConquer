#!/usr/bin/env python3
"""Drive the official Blender Lab MCP server (stdio) from the command line.

Blender must be running with the MCP add-on connected (socket on 127.0.0.1:9876),
and the server repo cloned at ~/blender_mcp (see .mcp.json).

Usage:
  blender_run.py exec <script.py>          run a bpy script inside Blender
  blender_run.py code "<python>"           run an inline snippet
  blender_run.py thumb <out.png> [size]    render a viewport thumbnail to PNG
  blender_run.py tools                     list available MCP tools
"""

import json
import subprocess
import sys
import threading
from pathlib import Path

SERVER_DIR = Path.home() / "blender_mcp" / "mcp"
TIMEOUT_S = 180


class Bridge:
    def __init__(self):
        self.proc = subprocess.Popen(
            ["uv", "run", "blender-mcp"],
            cwd=SERVER_DIR,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )
        self._watchdog = threading.Timer(TIMEOUT_S, self.proc.kill)
        self._watchdog.start()
        self._id = 0
        self._request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "fightandconquer-tools", "version": "1.0"},
        })
        self._notify("notifications/initialized")

    def close(self):
        self._watchdog.cancel()
        self.proc.kill()

    def _send(self, msg):
        self.proc.stdin.write(json.dumps(msg) + "\n")
        self.proc.stdin.flush()

    def _notify(self, method):
        self._send({"jsonrpc": "2.0", "method": method})

    def _request(self, method, params):
        self._id += 1
        self._send({"jsonrpc": "2.0", "id": self._id, "method": method, "params": params})
        while True:
            line = self.proc.stdout.readline()
            if not line:
                raise RuntimeError("MCP server closed the stream")
            try:
                reply = json.loads(line)
            except json.JSONDecodeError:
                continue
            if reply.get("id") == self._id:
                if "error" in reply:
                    raise RuntimeError(f"MCP error: {reply['error']}")
                return reply["result"]

    def call(self, tool, arguments):
        result = self._request("tools/call", {"name": tool, "arguments": arguments})
        texts = [c.get("text", "") for c in result.get("content", []) if isinstance(c, dict)]
        return "\n".join(t for t in texts if t)

    def list_tools(self):
        return [t["name"] for t in self._request("tools/list", {})["tools"]]


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    command = sys.argv[1]
    bridge = Bridge()
    try:
        if command == "tools":
            print("\n".join(bridge.list_tools()))
        elif command == "code":
            print(bridge.call("execute_blender_code", {"code": sys.argv[2]}))
        elif command == "exec":
            script = Path(sys.argv[2])
            code = script.read_text()
            common = script.parent.parent / "_common.py"
            if common.exists():
                code = common.read_text() + "\n\n" + code
            print(bridge.call("execute_blender_code", {"code": code}))
        elif command == "thumb":
            out = str(Path(sys.argv[2]).absolute())
            print(bridge.call("render_thumbnail_to_path", {"output_path": out}))
        elif command == "viewport":
            out = str(Path(sys.argv[2]).absolute())
            print(bridge.call("render_viewport_to_path", {"output_path": out}))
        else:
            print(__doc__)
            return 2
    finally:
        bridge.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
