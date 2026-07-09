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
		this.clientMaxBodySize = extractNumbers(obj, "clientMaxBodySize").stream().findFirst().orElse(0);
		this.isDefaultServer = Route.extract(obj, "isDefaultServer", false);
		this.ports = extractNumbers(obj, "ports");
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
	public Map<String, String> extract(JsonObject node, String key){
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
	private List<Integer> extractNumbers(JsonObject obj, String key){
		List<Integer> port = new ArrayList<>();
		var k = obj.values.get(key);
		 switch(k) {
			case JsonArray arr -> arr.elements.forEach((p)-> {
				if (p instanceof JsonNumber n){
					port.add(n.value);
				}
			});
			case JsonNumber n -> port.add(n.value);
			default -> {}
		}
		return port;
	}
}
 