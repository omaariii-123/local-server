package src;

import java.util.ArrayList;
import java.util.List;

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
}
 