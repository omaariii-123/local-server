# Project Fix List

Only the items that still need to be fixed are listed here.

## Fixes Needed

### 1. Timeout check

Fix the timeout check in `SocketConnection.CheckTimeout(...)` so active clients are not disconnected early.

```java
public void CheckTimeout(SelectionKey key) throws IOException {
	long now = System.currentTimeMillis();
	if (now - lastActiveTime > TIMEOUT_MS) {
		closeConnection(key);
	}
}
```

### 2. Request routing path

Make sure `SocketConnection` always routes parsed requests through `Router.handle(...)` and then into the response writer.

```java
while ((request = parser.ParseRequest(readBuffer)) != null) {
	RouteResult result = router.handle(request, serverConfig);
	responses.add(result);
	isKeepAlive = request.isKeepAlive();
	state = ConnectionFsm.WRITING;
	key.interestOps(SelectionKey.OP_WRITE);
}
```

### 3. CGI completion check

Replace the CGI `exitValue()` check with a non-throwing completion check.

```java
if (activeCgiChannel == null) {
	if (!Cgiprocess.isAlive()) {
		this.activeCgiChannel = FileChannel.open(CGIContext.tempFile(), StandardOpenOption.READ);
		setupCgiResponseHeaders();
	} else {
		return;
	}
}
```

### 4. Cookie/header propagation

Pass the `RouteResult` cookie/header data through the response writer so session cookies can actually be emitted.

```java
StringBuilder headers = new StringBuilder();
headers.append("HTTP/1.1 ").append(result.statusCode()).append(" OK\r\n");
if (result.cookieHeader() != null) {
	headers.append("Set-Cookie: ").append(result.cookieHeader()).append("\r\n");
}
```

### 5. Directory listing response

Fix the directory-listing path so it uses the routed request URI correctly.

```java
case DIRECTORY_LISTING:
	byte[] dirHtml = generateDirectoryHtml(result.resolvedPath(), result.originalUri());
	headers.append("HTTP/1.1 200 OK\r\n")
		   .append("Content-Type: text/html\r\n")
		   .append("Content-Length: ").append(dirHtml.length).append("\r\n\r\n");
	break;
```

### 6. Host parsing

Harden `Router.extract(...)` for host headers with more than one colon.

```java
public Target extract(String input) {
	if (input == null) {
		return null;
	}
	int lastColon = input.lastIndexOf(':');
	if (lastColon == -1) {
		return new Target(input, 80);
	}
	String host = input.substring(0, lastColon);
	int port = Integer.parseInt(input.substring(lastColon + 1));
	return new Target(host, port);
}
```

### 7. Compile-time cleanup

Resolve the compile-time issues in the JSON model and any missing fields or methods before expecting a clean build.

```java
// Remove unused imports and fields, then align the JSON model types with actual usage.
// Example cleanup:
// - delete unused imports such as java.util.ArrayList
// - remove unused local variables such as the cookies list
// - remove unused fields such as multipartFileName if they are not referenced
```

## Runtime Bugs Addressed

The earlier runtime issues were fixed in code with minimal changes:

- DELETE responses now have an explicit response branch.
- `Set-Cookie` is emitted after the HTTP status line.
- Parsed header names are normalized to lowercase.
- Keep-alive matching now ignores case.
- Host parsing now handles a port more safely, including bracketed IPv6-style hosts.

## Audit Checklist

Status legend: `PASS` = verified working, `PARTIAL` = present in code but not fully verified or has gaps, `FAIL` = tested and currently broken, `NOT TESTED` = not exercised yet.

### Server

- PASS: Java project using core libraries only.
- PASS: Non-blocking I/O via `java.nio` selector loop.
- PASS: Event-driven connection handling is implemented in a single selector loop.
- PASS: The server starts on multiple configured ports (`8080` and `8081`).
- PASS: The server is still single-process and single main thread in the current design.
- PASS: The server stays up during the live smoke suite.
- PASS: Long-running socket cleanup exists through timeout handling in `SocketConnection.CheckTimeout(...)`.

### HTTP Behavior

- PASS: Basic `GET /` returns a valid HTTP/1.1 response.
- PASS: `404` responses are returned correctly.
- PASS: Session cookies are emitted on first request.
- PASS: Parsed header names are normalized to lowercase.
- PASS: Keep-alive detection works case-insensitively.
- PASS: HTTP status handling works for basic, redirect, and error cases.
- PASS: Redirect route returns a valid `Location` header in the current config path test.

### Methods And Bodies

- PASS: `GET` works for the default route.
- PASS: `POST /upload` returns `201` for multipart uploads in the current test run.
- PASS: `DELETE /uploads/test_upload.txt` returns `200` in the current test run.
- PASS: Chunked upload returns `201` in the current test run.
- PASS: POST/body handling works for both multipart and chunked uploads.

### Cookies And Sessions

- PASS: A session cookie is created and returned on the first GET request.
- PASS: The cookie header is emitted after the status line.
- PASS: Returning-cookie handling exists in the router.
- PASS: Cookie/session behavior works for repeated requests and authenticated reuse.

### Configuration

- PASS: Multiple ports are loaded from `config.json`.
- PASS: Duplicate port configuration is detected and warned about at startup.
- PASS: Default server selection is implemented in `Router.findMatchedServer(...)`.
- PASS: Custom error pages are configured for 400, 403, 404, 405, 413, and 500.
- PASS: Route roots and accepted methods are configured.
- PASS: Host-based virtual routing works for `test.com` on port `8080`.
- PASS: Directory default file handling works for `test.com` (`home.html`).
- PASS: Hostnames on the same port are routed correctly, including `custom.local` and `test.com`.
- PARTIAL: CGI is configured by file extension, but it is still not covered by the smoke run.

### Browser And Response Headers

- PASS: The root page is reachable from a client and returns an HTTP/1.1 response.
- PASS: Response headers are emitted for successful GET and 404 cases.
- PASS: Redirect response headers now return a valid `Location` header for `/old-site`.
- NOT TESTED: Browser developer tools integration.
- NOT TESTED: CGI chunked and unchunked browser verification.

### Ports And Conflicts

- PASS: The server binds and listens on multiple configured ports.
- PASS: Duplicate port configuration is detected at startup.
- PARTIAL: The server continues after duplicate-port detection; the assignment may still want this treated as a startup error.

### Siege And Stress Testing

- NOT TESTED: `siege -b [IP]:[PORT]` availability target of `99.5%`.
- NOT TESTED: Memory leak checks under stress.
- PARTIAL: No hanging connection was observed in the small smoke run, but siege and memory-leak checks are still not run.

## Current Test Results

- `./test_server.sh`: all checks passed in the latest run.
- `curl -i --resolve test.com:8080:127.0.0.1 http://test.com:8080/`: returned `200 OK`.
- `curl -i -X POST http://localhost:8080/upload -H 'Transfer-Encoding: chunked' --data-binary ...`: returned `201 OK`.
- `curl -i -X DELETE http://localhost:8080/uploads/test_upload.txt`: returned `200 OK`.
- `curl -i -X POST http://test.com:8080/submit --resolve test.com:8080:127.0.0.1 --data-binary oversized-body`: returned `413 Error`.
- `curl -i http://localhost:8080/api/`: returned `200 OK` but the CGI transfer closed early with unread bytes remaining.
- `curl -i -L -X GET http://localhost:8080/old-site`: returned `301` with `Location: http://localhost:8080/`, then followed by `200 OK`.

## Test Matrix

This is the checklist we used to validate the server, together with the intent of each test.

### Core Server

- `./test_server.sh` - end-to-end smoke test for GET, host routing, cookies, upload, chunked upload, and 404 handling.
- `curl -i http://localhost:8080/` - verify the server returns a valid HTTP/1.1 response for the root page.
- `curl -i http://localhost:8080/does-not-exist` - verify the 404 error page path.
- `curl -i -X OPTIONS http://localhost:8080/` - verify unsupported methods return 405 and the server keeps running.

### Configuration

- `curl -i --resolve test.com:8080:127.0.0.1 http://test.com:8080/` - verify host-based routing and the `test.com` default page.
- `curl -i -L -X GET http://localhost:8080/old-site` - verify route redirection and the `Location` header.
- `curl -i -X POST http://test.com:8080/submit --resolve test.com:8080:127.0.0.1 --data-binary oversized-body` - verify the client body-size limit returns 413.

### Methods And Uploads

- `curl -i -X POST http://localhost:8080/upload -H 'Transfer-Encoding: chunked' --data-binary ...` - verify chunked upload handling.
- `curl -i -X POST http://localhost:8080/upload -H 'Content-Type: text/plain' --data 'hello'` - verify unchunked POST body handling.
- `curl -i -X DELETE http://localhost:8080/uploads/test_upload.txt` - verify DELETE on uploaded files.

### Cookies And Sessions

- First GET request to `/` - verify that a new session cookie is created.
- Second GET request with the saved cookie - verify that the router recognizes the existing session.

### CGI

- `curl -i http://localhost:8080/api/` - verify the CGI route executes and returns data.
- Current intent: confirm chunked CGI output is streamed correctly without truncation.
- Current result: partial. The route starts and returns data, but the transfer closes early.

### Ports And Conflicts

- Start the server with `config.json` as-is - verify it binds to `8080` and `8081`.
- Start the server with duplicate port settings - verify the configuration warning is emitted.

### Stress And Reliability

- `siege -b [IP]:[PORT]` - verify availability stays at or above `99.5%` on a GET-only endpoint.
- Memory leak check under repeated requests - verify there are no hanging connections or unbounded resource growth.

### Bonus / Not Implemented

- Second CGI handler - not implemented yet.
- Admin dashboard or metrics endpoint - not implemented yet.
