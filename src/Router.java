package src;

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
        if (server == null || request == null)return null;
        target = extract(request.Headers.get("host"));
        server = findMatchedServer(serverConfigs);
        String path = request.requestLine.getPath();
        Route  route = extract(server.routes, path);
        if (route == null) {
            return new RouteResult(RouteResult.Action.ERROR,
            404,
            Path.of("default_errors/404.html"),
            "text/html",
            null); 
        }
        if (!route.acceptedMethods.contains(request.requestLine.method)){
            return new RouteResult(RouteResult.Action.ERROR,
            405,
            Path.of("default_errors/405.html"),
            "text/html",
            null);
        }
        return new RouteResult(null, 0, null, null, null);
    }

    public Target extract(String input) {
        if (input == null)return null;
        String host;
        int port = 80;
        host = input.split(":")[0];
        try {
            port = Integer.parseInt(input.split(":")[1]);
        } catch (Exception e) {
        }
        return new Target(host, port);
    }
    public Route extract(List<Route> list, String path){
        return list.stream().filter((r)-> {return path.startsWith(r.path);}).max(Comparator.comparingInt((r) -> r.path.length())).orElse(null);
    }

    public ServerConfig findMatchedServer(List<ServerConfig> list) {
        return list.stream().filter((x) -> {return x.host.equals(target.host) && x.ports.contains(target.port());}).findFirst().orElse(null);
    }
}
