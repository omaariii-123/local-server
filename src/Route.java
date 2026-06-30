package src;

import java.util.ArrayList;
import java.util.List;

public class Route {
    public String path;
    public String root;
    public List<String> acceptedMethods = new ArrayList<>();
    public boolean autoindex = false;
    private Route(){}
    public static Route hydrate(JsonObject node) {
        Route route = new Route();
        JsonArray arr = (JsonArray)node.values.values().iterator().next();
        JsonObject node1 = (JsonObject)arr.elements.get(0);
        JsonObject node2;
        if (node1.values.get("routes") instanceof JsonArray ar){
            node2 = (JsonObject)ar.elements.get(0);       
            route.path = extract(node2, "path", "/");
            route.root = extract(node2, "root", "/var/www/html");
            route.autoindex = extract(node2, "autoindex", false);
            extract(route, node2, "acceptedMethods");
        }
        return route;
    }
    public static String extract(JsonObject node, String key, String def){
        JsonElement str = node.values.get(key);
        if (str instanceof JsonString s){
            return s.value;
        }
        return def;
    }
     public static boolean extract(JsonObject node, String key, boolean def){
        JsonElement str = node.values.get(key);
        if (str instanceof JsonBoolean b){
            return b.value;
        }
        return def;
    }
      public static void extract(Route r, JsonObject o, String key){
        if (o.values.get(key) instanceof JsonArray arr){
            arr.elements.forEach((elem) -> {
                if (elem instanceof JsonString s){
                    r.acceptedMethods.add(s.value);
                }
            });
        }
    }
}
