package src;

import java.util.List;

public class Router {
    public HttpResponse handle(HttpRequest request, List<ServerConfig> serverConfigs){
        HttpResponse response = new HttpResponse();
        request.Headers.get("host");
        return response;
    }
}
