

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


