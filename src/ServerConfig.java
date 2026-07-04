package src;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ServerConfig {
	public String host;
	public int clientMaxBodySize;
    public boolean isDefaultServer;
	public List<Integer> ports;
    public List<Route> routes;

	public void hydrate(JsonObject obj) {
		this.host = Route.extract(obj, "host");
		this.clientMaxBodySize = Integer.parseInt(Route.extract(obj, "clientMaxBodySize"));
		this.isDefaultServer = Route.extract(obj, "isDefaultServer", false);
		this.ports = Route.extract(obj, new JsonString("ports"))
                    .stream()
                    .map(Integer::parseInt)
                    .toList();
		this.routes = new ArrayList<>();
		if (obj.values.get("routes") instanceof JsonArray arr) {
    		arr.elements.forEach(elem -> {
        		if (elem instanceof JsonObject routeObj) {
            		this.routes.add(Route.hydrate(routeObj));
        		}
    		});
		}
	}


	public Route findRoute(String path) {
		for (Route route : this.routes) {
			if (route.path.equals(path)) {
				return route;
			}
		}
		return null;
	}

	public Map<Integer, String> getErrorPages() {
		Map<Integer, String> errorPages = new HashMap<>();
		for (Route route : this.routes) {
			if (route.path.startsWith("/error")) {
				String[] parts = route.path.split("/");
				if (parts.length == 3 && parts[2].matches("\\d+")) {
					int statusCode = Integer.parseInt(parts[2]);
					errorPages.put(statusCode, route.path);
				}
			}
		}
		return errorPages;
	}
}
 