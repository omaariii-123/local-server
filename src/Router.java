package src;

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
        target = extract(request.Headers.get("host"));
        server = findMatchedServer(serverConfigs);
        if (server == null){
            return new RouteResult(
                RouteResult.Action.ERROR,
                500,
                null,
                "text/plain",
                null,
                null,
                null
            );
        }
        
        String path = request.requestLine.getPath();
        Route  route = extract(server.routes, path);
        if (route == null) {
            return createError(404);
        }
        if (route.redirection != null) {
            return new RouteResult(
                RouteResult.Action.REDIRECT,
                route.redirection.values.get("code") instanceof JsonNumber n ? n.value : 302,
                Path.of(route.redirection.values.get("url").toString()),
                "text/html",
                null,
                null,
                null
            );
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
                // Ignore malformed cookies
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
        Path rootPath = Path.of(route.root).toAbsolutePath().normalize();
        Path finalUri = rootPath.resolve(leftoverUri).toAbsolutePath().normalize();
        if (!finalUri.startsWith(rootPath)) {
            System.err.println("SECURITY ALERT: Path traversal attempt blocked!");
            return createError(403);
        }
        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
            
            boolean hasContentLength = request.Headers.containsKey("content-length");
            boolean isChunked = "chunked".equalsIgnoreCase(request.Headers.get("transfer-encoding"));

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
                    
                    if (!Files.exists(finalUri.getParent())) {
                        Files.createDirectories(finalUri.getParent());
                    }
                    Files.write(finalUri, request.body.toByteArray());                    
                    return new RouteResult(
                        RouteResult.Action.SERVE_FILE, 
                        201, 
                        finalUri, 
                        "text/plain", 
                        null, 
                        null,
                        cookieHeader);
                } 
                else if (isChunked) {
                    return new RouteResult(
                        RouteResult.Action.SERVE_FILE, 
                        202, 
                        finalUri, 
                        "text/plain", 
                        null, 
                        null,
                        cookieHeader);
                } 
                else {
                    return createError(400);
                }
            } catch (IOException e) {
                return createError(500);
            }
        }
        if (leftoverUri.startsWith("/")){
            leftoverUri = leftoverUri.substring(1);
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
                    cookieHeader);
            } catch (IOException e) {
                System.err.println("Failed to delete file: " + e.getMessage());
                return createError(500);
            }
        }
        if (Files.isDirectory(finalUri)){
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
                        cookieHeader);
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
                    executablePath
                );
                
                return new RouteResult(
                    RouteResult.Action.EXECUTE_CGI, 
                    200, 
                    context.tempFile(), 
                    "text/html", 
                    null, 
                    context,
                    cookieHeader
                );
                
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
            cookieHeader);
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
            null
        );
    }
        public Target extract(String input) {
        if (input == null) return null;
        String[] parts = input.split(":");
        String host = parts[0];
        int port = 80;       
        if (parts.length > 1) {
            port = Integer.parseInt(parts[1]);
        }   
        return new Target(host, port);
    }
    public Route extract(List<Route> list, String path){
        return list.stream().filter((r)-> {return path.startsWith(r.path);}).max(Comparator.comparingInt((r) -> r.path.length())).orElse(null);
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