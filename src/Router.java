package src;

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
            return createError(500);
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
        if (leftoverUri.startsWith("/")){
            leftoverUri = leftoverUri.substring(1);
        }
        Path finalUri = Path.of(route.root).resolve(leftoverUri);
        if (!Files.exists(finalUri)) {
            String fileName = finalUri.getFileName().toString();
        if (fileName.endsWith(".py")) {
            // We don't serve this! We execute it.
            return new RouteResult(RouteResult.Action.EXECUTE_CGI, 200, finalUri, "text/html", null);
        }
            System.err.println(finalUri);
            return createError(404);
        }
        if (Files.isDirectory(finalUri)){
            Path indexPath = finalUri.resolve("index.html");          
            if (Files.exists(indexPath)) {
                finalUri = indexPath;
            } else {
                if (route.autoindex) {
                    return new RouteResult(RouteResult.Action.DIRECTORY_LISTING, 200, finalUri, "text/html", null);
                } else {
                    return createError(403);
                }
            }
        }
        return new RouteResult(RouteResult.Action.SERVE_FILE, 200, finalUri, getMimeType(finalUri), null);
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
