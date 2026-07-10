
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class Router {

    private record Target(String host, int port) {
    };

    private final SessionManager sessionManager = new SessionManager();
    private Target target;
    private ServerConfig server;
    public int urlIndex = 0;

    public RouteResult handle(HttpRequest request, List<ServerConfig> serverConfigs) {
        return handle(request, serverConfigs, 80);
    }

    public RouteResult handle(HttpRequest request, List<ServerConfig> serverConfigs, int defaultPort) {
        target = extract(request.Headers.get("host"), defaultPort);
        server = findMatchedServer(serverConfigs);
        if (server == null) {
            return new RouteResult(
                    RouteResult.Action.ERROR,
                    500,
                    null,
                    "text/plain",
                    null,
                    null,
                    null, null);
        }

        String path = request.requestLine.getPath();
        Route route = extract(server.routes, path);
        if (route == null) {
            return createError(404);
        }
        if (route.redirection != null) {
            return new RouteResult(
                    RouteResult.Action.REDIRECT,
                    route.redirection.values.get("code") instanceof JsonNumber n ? n.value : 302,
                    null,
                    "text/html",
                    route.redirection.values.get("url") instanceof JsonString s ? s.value : route.redirection.values.get("url").toString(),
                    null,
                    null, path);
        }
        String cookieHeader = null;
        String rawCookie = request.Headers.get("cookie");
        boolean isKnownUser = false;

        if (rawCookie != null && rawCookie.contains("session_id=")) {
            try {
                String sessionId = rawCookie.split("session_id=")[1].split(";")[0];
                if (sessionManager.isValidSession(sessionId)) {
                    isKnownUser = true;
                    System.out.println("DEBUG: Recognized returning user with ID: " + sessionId);
                }
            } catch (Exception e) {
            }
        }

        if (!isKnownUser) {
            String newSessionId = sessionManager.createSession();
            cookieHeader = "session_id=" + newSessionId + "; HttpOnly; Path=/";
            System.out.println("DEBUG: Issued new session cookie: " + newSessionId);
        }
        String method = request.requestLine.method;
        if (!route.acceptedMethods.contains(method)) {
            return createError(405);
        }
        String leftoverUri = path.substring(route.path.length());
        if (leftoverUri.startsWith("/")) {
            leftoverUri = leftoverUri.substring(1);
        }
        Path rootPath = Path.of(route.root).toAbsolutePath().normalize();
        Path finalUri = rootPath.resolve(leftoverUri).toAbsolutePath().normalize();
        if (!finalUri.startsWith(rootPath)) {
            System.err.println("SECURITY ALERT: Path traversal attempt blocked!");
            return createError(403);
        }
        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {

            boolean hasContentLength = request.Headers.containsKey("content-length");
            boolean isChunked = "chunked".equalsIgnoreCase(request.Headers.get("transfer-encoding"));
            boolean isMultipart = request.Headers.get("content-type") != null
                    && request.Headers.get("content-type").startsWith("multipart/form-data");

            if (!hasContentLength && !isChunked) {
                return createError(411);
            }
            if (hasContentLength) {
                try {
                    long contentLength = Long.parseLong(request.Headers.get("content-length"));
                    if (contentLength > server.clientMaxBodySize) {
                        return createError(413);
                    }
                } catch (NumberFormatException e) {
                    return createError(400);
                }
            }
            try {
                if (request.body != null && request.body.size() > 0) {
                    Path targetPath = finalUri;
                    byte[] payload = request.body.toByteArray();

                    if (Files.isDirectory(finalUri)) {
                        String uploadFileName = isMultipart ? extractMultipartFileName(payload) : null;
                        if (uploadFileName == null || uploadFileName.isBlank()) {
                            uploadFileName = "upload.bin";
                        }
                        targetPath = finalUri.resolve(uploadFileName).normalize();
                    }

                    if (!Files.exists(targetPath.getParent())) {
                        Files.createDirectories(targetPath.getParent());
                    }

                    if (isMultipart) {
                        payload = extractMultipartPayload(payload);
                    }

                    Files.write(targetPath, payload);

                    return new RouteResult(
                            RouteResult.Action.SERVE_FILE,
                            201,
                            targetPath,
                            "text/plain",
                            null,
                            null,
                            cookieHeader,
                            path);
                } else if (isChunked) {
                    if (Files.isDirectory(finalUri)) {
                        finalUri = finalUri.resolve("upload.bin").normalize();
                    }
                    if (!Files.exists(finalUri.getParent())) {
                        Files.createDirectories(finalUri.getParent());
                    }
                    Files.write(finalUri, new byte[0]);
                    return new RouteResult(
                            RouteResult.Action.SERVE_FILE,
                            202,
                            finalUri,
                            "text/plain",
                            null,
                            null,
                            cookieHeader,
                            path);
                } else {
                    return createError(400);
                }
            } catch (IOException e) {
                return createError(500);
            }
        }
        if (!Files.exists(finalUri)) {
            return createError(404);
        }
        if (method.equals("DELETE")) {
            try {
                Files.delete(finalUri);
                return new RouteResult(
                        RouteResult.Action.DELETE_FILE,
                        200,
                        null,
                        getMimeType(finalUri),
                        null,
                        null,
                        cookieHeader,
                        path);
            } catch (IOException e) {
                System.err.println("Failed to delete file: " + e.getMessage());
                return createError(500);
            }
        }
        if (Files.isDirectory(finalUri)) {
            String defaultFile = route.defaultFile != null ? route.defaultFile : "index.html";
            Path indexPath = finalUri.resolve(defaultFile);
            if (Files.exists(indexPath)) {
                finalUri = indexPath;
            } else {
                if (route.autoindex) {
                    return new RouteResult(
                            RouteResult.Action.DIRECTORY_LISTING,
                            200,
                            finalUri,
                            "text/html",
                            null,
                            null,
                            cookieHeader, path);

                } else {
                    return createError(403);
                }
            }
        }
        String fileName = finalUri.getFileName().toString();
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileName.substring(dotIndex);
        }
        if (route.cgi != null && route.cgi.containsKey(extension)) {
            String executablePath = route.cgi.get(extension);
            try {
                CGIHandler cgiHandler = new CGIHandler();
                CGIHandler.CGIContext context = cgiHandler.execute(
                        finalUri,
                        leftoverUri,
                        request.requestLine.method,
                        executablePath);

                return new RouteResult(
                        RouteResult.Action.EXECUTE_CGI,
                        200,
                        context.tempFile(),
                        "text/html",
                        null,
                        context,
                        cookieHeader, path);

            } catch (IOException e) {
                System.err.println("CGI Execution failed: " + e.getMessage());
                return createError(500);
            }
        }
        return new RouteResult(
                RouteResult.Action.SERVE_FILE,
                200,
                finalUri,
                getMimeType(finalUri),
                null,
                null,
                cookieHeader, path);
    }

    private String getMimeType(Path path) {
        try {
            String mimeType = Files.probeContentType(path);
            return (mimeType != null) ? mimeType : "application/octet-stream";
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private RouteResult createError(int statusCode) {
        String customPagePath = server.errorPages.get(String.valueOf(statusCode));
        Path errorPath = null;
        if (customPagePath != null) {
            errorPath = Path.of(customPagePath);
        }
        return new RouteResult(
                RouteResult.Action.ERROR,
                statusCode,
                errorPath,
                "text/html",
                null,
                null,
                null, null);
    }

    public Target extract(String input, int defaultPort) {
        if (input == null)
            return null;
        String host = input;
        int port = defaultPort;
        if (input.startsWith("[") && input.contains("]")) {
            int closingBracket = input.indexOf(']');
            host = input.substring(1, closingBracket);
            if (closingBracket + 1 < input.length() && input.charAt(closingBracket + 1) == ':') {
                port = Integer.parseInt(input.substring(closingBracket + 2));
            }
            return new Target(host, port);
        }
        int lastColon = input.lastIndexOf(':');
        if (lastColon > -1) {
            try {
                port = Integer.parseInt(input.substring(lastColon + 1));
                host = input.substring(0, lastColon);
            } catch (NumberFormatException ignored) {
                host = input;
            }
        }
        return new Target(host, port);
    }

    public Target extract(String input) {
        return extract(input, 80);
    }

    private String extractMultipartFileName(byte[] body) {
        String text = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);
        int index = text.indexOf("filename=");
        if (index == -1) {
            return null;
        }
        int startQuote = text.indexOf('"', index);
        if (startQuote == -1) {
            return null;
        }
        int endQuote = text.indexOf('"', startQuote + 1);
        if (endQuote == -1) {
            return null;
        }
        return text.substring(startQuote + 1, endQuote);
    }

    private byte[] extractMultipartPayload(byte[] body) {
        String text = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);
        int payloadStart = text.indexOf("\r\n\r\n");
        if (payloadStart == -1) {
            return body;
        }
        payloadStart += 4;
        int payloadEnd = text.lastIndexOf("\r\n--");
        if (payloadEnd == -1 || payloadEnd < payloadStart) {
            payloadEnd = body.length;
        }
        return text.substring(payloadStart, payloadEnd).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    public Route extract(List<Route> list, String path) {
        return list.stream().filter((r) -> {
            return path.startsWith(r.path);
        }).max(Comparator.comparingInt((r) -> r.path.length())).orElse(null);
    }

    public ServerConfig findMatchedServer(List<ServerConfig> list) {
        ServerConfig exactMatch = list.stream()
                .filter(x -> x.host.equals(target.host()) && x.ports.contains(target.port()))
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            return exactMatch;
        }
        ServerConfig defaultMatch = list.stream()
                .filter(x -> x.ports.contains(target.port()) && x.isDefaultServer)
                .findFirst()
                .orElse(null);

        if (defaultMatch != null) {
            return defaultMatch;
        }
        return list.stream()
                .filter(x -> x.ports.contains(target.port()))
                .findFirst()
                .orElse(null);
    }
}