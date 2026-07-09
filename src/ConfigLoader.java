
package src;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.ByteArrayOutputStream;

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
			System.err.println("FATAL: No valid server configurations found. Exiting.");
			System.exit(1);
		}
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
	public static void runGauntletTests(List<ServerConfig> configs) {
        Router router = new Router();

        System.out.println("--- RUNNING ROUTER GAUNTLET TESTS ---");

        // TEST 1: The Standard GET (Checking auto-index / default file)
        HttpRequest getReq = new HttpRequest();
        getReq.requestLine = new RequestLine();
        getReq.requestLine.setMethod("GET");
        getReq.requestLine.setPath("/");
        getReq.Headers = new HashMap<>();
        getReq.Headers.put("host", "localhost:8080");
        
        System.out.println("\nTest 1 (GET /):");
        System.out.println(router.handle(getReq, configs));

        // TEST 2: The CGI Execution (Checking file extension mapping)
        HttpRequest cgiReq = new HttpRequest();
        cgiReq.requestLine = new RequestLine();
        cgiReq.requestLine.setMethod("POST");
        cgiReq.requestLine.setPath("/api/login.py");
        cgiReq.Headers = new HashMap<>();
        cgiReq.Headers.put("host", "localhost:8080");
        cgiReq.Headers.put("content-length", "10");
        cgiReq.body = new ByteArrayOutputStream();
        try { cgiReq.body.write("user=admin".getBytes()); } catch (Exception ignored) {}
        
        System.out.println("\nTest 2 (POST /api/login.py):");
        System.out.println(router.handle(cgiReq, configs));

        // TEST 3: The 413 Payload Too Large Trap
        // test.com in our config is limited to 500 bytes. We send 1000.
        HttpRequest trapReq = new HttpRequest();
        trapReq.requestLine = new RequestLine();
        trapReq.requestLine.setMethod("POST");
        trapReq.requestLine.setPath("/");
        trapReq.Headers = new HashMap<>();
        trapReq.Headers.put("host", "test.com:8080"); 
        trapReq.Headers.put("content-length", "1000"); // Double the limit!
        trapReq.body = new ByteArrayOutputStream();
        try { trapReq.body.write(new byte[1000]); } catch (Exception ignored) {} // Write 1000 empty bytes

        System.out.println("\nTest 3 (413 Trap on test.com):");
        System.out.println(router.handle(trapReq, configs));
        
        System.out.println("-------------------------------------");
    }
}
