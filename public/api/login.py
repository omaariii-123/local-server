import sys

# 1. Print the mandatory HTTP headers first (your Java setupCgiResponseHeaders expects this delimiter)
print("Content-Type: text/plain\r\n\r\n", end="")

# 2. Read the POST body from Standard Input (This is how CGI reads POST bodies)
post_data = sys.stdin.read()

# 3. Print the result back to the server
print("CGI Execution Successful!")
print(f"The server sent this POST body: {post_data}")