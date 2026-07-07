
package src;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

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
		StringBuilder content = new StringBuilder();
		try (Scanner reader = new Scanner(configFile)){
			while ( reader.hasNext()){
				content.append(reader.nextLine());
			}
		} catch (FileNotFoundException error){
			System.out.println(error);
			return null;
		}
		JsonScanner lexer = new JsonScanner(content.toString());
		JsonParser  parser = new JsonParser(lexer.scanTokens());
		lexer.scanTokens();
		
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
		
		
		/*if (!this.validate(null)){
			return null;
		}*/
		Router r = new Router();
		HttpRequest dummyRequest = new HttpRequest();

		dummyRequest.requestLine = new RequestLine();
		dummyRequest.requestLine.setMethod("GET");
		dummyRequest.requestLine.setPath("/images/image.py");
		dummyRequest.Headers = new HashMap<>();
		dummyRequest.Headers.put("host", "localhost:8080");
		System.err.println(r.handle(dummyRequest, list));
		System.err.println(list.get(0).routes.get(0).acceptedMethods);
		return list;
	}
	private boolean validate(ServerConfig config){
		return true;
	}
}
