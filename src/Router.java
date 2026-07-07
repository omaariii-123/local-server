package src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class Router {

    private record Target(String host, int port) {
    };

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
                null);
        }
        String path = request.requestLine.getPath();
        Route  route = extract(server.routes, path);
        if (route == null) {
            return createError(404);
        }
        if (!route.acceptedMethods.contains(request.requestLine.method)){
            return createError(405);
        }
        String leftoverUri = path.substring(route.path.length());
        Path rootPath = Path.of(route.root).toAbsolutePath().normalize();
        Path finalUri = rootPath.resolve(leftoverUri).toAbsolutePath().normalize();
        if (!finalUri.startsWith(rootPath)) {
            System.err.println("SECURITY ALERT: Path traversal attempt blocked!");
            return createError(403);
        }
        if (leftoverUri.startsWith("/")){
            leftoverUri = leftoverUri.substring(1);
        }
        //Path finalUri = Path.of(route.root).resolve(leftoverUri);
        if (!Files.exists(finalUri)) {
            return createError(404);
        }
        if (Files.isDirectory(finalUri)){
            Path indexPath = finalUri.resolve("index.html");          
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
                        null);
                } else {
                    return createError(403);
                }
            }
        }
        String fileName = finalUri.getFileName().toString();        
        if (fileName.endsWith(".py")) {
            try {
                CGIHandler cgiHandler = new CGIHandler();
                
                CGIHandler.CGIContext context = cgiHandler.execute(
                    finalUri, 
                    leftoverUri, 
                    request.requestLine.method, 
                    "/usr/bin/python3" 
                );
                
                return new RouteResult(
                    RouteResult.Action.EXECUTE_CGI, 
                    200, 
                    context.tempFile(), 
                    "text/html", 
                    null, 
                    context
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
            null);
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
        return new RouteResult(
            RouteResult.Action.ERROR, 
            statusCode, 
            Path.of(server.errorPages.get(String.valueOf(statusCode))), 
            "text/html", 
            null,
            null
    );
}    public Target extract(String input) {
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
        return list.stream().filter((x) -> {return x.host.equals(target.host()) && x.ports.contains(target.port());}).findFirst().orElse(null);
    }
}
