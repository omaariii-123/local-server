
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Route {
    public String path;
    public String root;
    public List<String> acceptedMethods;
    public boolean autoindex = false;
    public JsonObject redirection;
	public Map<String, String> cgi;
    String defaultFile;
    private Route(){}
    public static Route hydrate(JsonObject node) {
        Route route = new Route();       
        route.path = extract(node, "path");
        route.root = extract(node, "root");
        route.autoindex = extract(node, "autoindex", false);
        route.acceptedMethods = extract(node, new JsonString("acceptedMethods"));
        route.redirection = extractObj(node, "redirection");
        route.cgi = new ServerConfig().extract(node, "cgi");
        route.defaultFile = extract(node, "defaultFile");
        return route;
    }
    public static String extract(JsonObject node, String key){
        JsonElement str = node.values.get(key);
        if (str instanceof JsonString s){
            return s.value;
        }
        return "";
    }
     public static boolean extract(JsonObject node, String key, boolean def){
        JsonElement str = node.values.get(key);
        if (str instanceof JsonBoolean b){
            return b.value;
        }
        return def;
    }
      public static List<String> extract(JsonObject o, JsonString key){
        List<String> methods = new ArrayList<>();
        if (o.values.get(key.value) instanceof JsonArray arr){
            arr.elements.forEach((elem) -> {
                if (elem instanceof JsonString s){
                    methods.add(s.value);
                }
            });
        }
        return methods;
    }
    public static JsonObject extractObj(JsonObject node, String key){
        JsonElement str = node.values.get(key);
        if (str instanceof JsonObject obj){
            return obj;
        }
        return null;
    }
}