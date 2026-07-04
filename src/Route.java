package src;

import java.util.ArrayList;
import java.util.List;

public class Route {
    public String path;
    public String root;
    public List<String> acceptedMethods;
    public boolean autoindex = false;
    private Route(){}
    public static Route hydrate(JsonObject node) {
        Route route = new Route();       
        route.path = extract(node, "path");
        route.root = extract(node, "root");
        route.autoindex = extract(node, "autoindex", false);
        route.acceptedMethods = extract(node, new JsonString("acceptedMethods"));
        return route;
    }
    public static String extract(JsonObject node, String key){
        JsonElement str = node.values.get(key);
        if (str instanceof JsonString s){
            return s.value;
        }else if (str instanceof JsonNumber n){
            return String.valueOf(n.value);
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

    public boolean allowsMethod(String method) {
        return acceptedMethods.contains(method);
    }

    public boolean hasRedirect() {
        return this.root != null && !this.root.isEmpty();
    }

    public String getRedirect() {
        return this.root;
    }
    public String getPath() {
        return this.path;
    }
    public List<String> getAcceptedMethods() {
        return this.acceptedMethods;
    }

    public boolean isAutoindex() {
        return this.autoindex;
    }

    public String getRoot() {
        return this.root;
    }

    public boolean isuploadRoute() {
        return this.path.equals("/upload");
    }

    public boolean iscgiRoute() {
        return this.path.equals("/cgi-bin");
    }

    public HttpResponse handleRedirect(RequestLine request) {
        String redirectUrl = getRedirect();
        HttpResponse response = new HttpResponse(302,redirectUrl);
        // response.addHeader("Location", redirectUrl);
        return response;
    }
}
