#!/usr/bin/env python3
import os
import sys
import json
import urllib.parse

# Parse query string from environment
query_string = os.environ.get("QUERY_STRING", "")
params = dict(urllib.parse.parse_qsl(query_string))

# Parse POST body if present
method = os.environ.get("REQUEST_METHOD", "GET")
body_data = {}
if method == "POST":
    content_length = int(os.environ.get("CONTENT_LENGTH", "0") or "0")
    if content_length > 0:
        raw_body = sys.stdin.buffer.read(content_length)
        content_type = os.environ.get("CONTENT_TYPE", "")
        if "application/x-www-form-urlencoded" in content_type:
            body_data = dict(urllib.parse.parse_qsl(raw_body.decode("utf-8", errors="replace")))
        else:
            body_data = {"raw": raw_body.decode("utf-8", errors="replace")}

response = {
    "status": "ok",
    "method": method,
    "query": params,
    "body": body_data,
    "path_info": os.environ.get("PATH_INFO", "/"),
    "script": os.environ.get("SCRIPT_NAME", "index.py"),
}

body = json.dumps(response)

print("Content-Type: application/json")
print("Content-Length: " + str(len(body)))
print()
print(body)
