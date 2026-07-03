package src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerConfig {
	public String host;
	public int clientMaxBodySize;
    public boolean isDefaultServer;
	public List<Integer> ports;
	public Map<String, String> errorPages;
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
		this.errorPages = extract(obj, "errorPages");
	}
	private Map<String, String> extract(JsonObject node, String key){
		Map<String,String> map = new HashMap<>();
		if(node.values.get(key) instanceof JsonObject o){
			o.values.forEach((k, value)->{
				if (value instanceof JsonString s){
					map.put(k ,s.value);
				}
			});
		}
		return map;
	}
}
 