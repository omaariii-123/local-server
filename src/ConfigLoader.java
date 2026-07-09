
package src;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ConfigLoader {
	private final String filePath;
	public ConfigLoader(String filePath){
		this.filePath = filePath;
	}
	public List<ServerConfig> load(){
		File configFile = new File(this.filePath);
		if (!configFile.exists()){
			return null;
		}
		return this.parse(configFile);
	}
	private List<ServerConfig> parse(File configFile){
		if (!configFile.canRead()){
			return null;
		}
		String content;
		try {
			content = Files.readString(configFile.toPath());
		} catch (Exception e){
			System.out.println(e);
			return null;
		}
		JsonScanner lexer = new JsonScanner(content.toString());
		JsonParser  parser = new JsonParser(lexer.scanTokens());
		JsonElement element = parser.parse();
		List<ServerConfig> list = new ArrayList<>();
		if (element instanceof JsonObject e) {
			JsonArray arr = (JsonArray)e.values.get("servers");
			arr.elements.forEach((x) -> {
				if (x instanceof JsonObject o){
					ServerConfig config = new ServerConfig();
					config.hydrate(o);
					list.add(config);
				}
			});
		}else if (element instanceof JsonArray e){
			e.elements.forEach((x) -> {
				if (x instanceof JsonObject o){
					ServerConfig config = new ServerConfig();
					config.hydrate(o);
					list.add(config);
				}
			});
		}
		if(!this.validate(list)){
			System.exit(1);
		}
		Router r = new Router();
		HttpRequest dummyRequest = new HttpRequest();

		dummyRequest.requestLine = new RequestLine();
		dummyRequest.requestLine.setMethod("GET");
		dummyRequest.requestLine.setPath("/old-blog");
		dummyRequest.Headers = new HashMap<>();
		dummyRequest.Headers.put("host", "localhost:8080");
		System.err.println(r.handle(dummyRequest, list));
		return list;
	}
	private boolean validate(List<ServerConfig> configs) {
        java.util.Set<String> validBindings = new java.util.HashSet<>();
        java.util.List<ServerConfig> invalidConfigs = new java.util.ArrayList<>();

        for (ServerConfig config : configs) {
            boolean isConfigValid = true;
            for (Route route : config.routes) {
                if (!Files.isDirectory(Path.of(route.root))) {
                    System.err.println("Warning: Dropping server block. Invalid root path: " + route.root);
                    isConfigValid = false;
                    break; 
                }
            }
            if (isConfigValid) {
                for (Integer port : config.ports) {
                    String signature = config.host + ":" + port;
                    if (validBindings.contains(signature)) {
                        System.err.println("FATAL: Duplicate Host/Port binding detected -> " + signature);
                        return false;
                    }
                    validBindings.add(signature);
                }
            } else {
                invalidConfigs.add(config);
            }
        }
        configs.removeAll(invalidConfigs);
        return !configs.isEmpty();
    }
}
