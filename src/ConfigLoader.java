
package src;

import java.io.File;
import java.io.FileNotFoundException;	
import java.util.Scanner;

class ConfigLoader {
	private final String filePath;
	public ConfigLoader(String filePath){
		this.filePath = filePath;
	}
	public ServerConfig load(){
		File configFile = new File(this.filePath);
		if (!configFile.exists()){
			return null;
		}
		return this.parse(configFile);
	}
	private ServerConfig parse(File configFile){
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
		JsonObject element = (JsonObject)parser.parse();
		ServerConfig config = new ServerConfig();
		JsonArray arr = (JsonArray)element.values.get("servers");
        JsonObject node1 = (JsonObject)arr.elements.get(0);
		config.hydrate(node1);
		
		if (!this.validate(config)){
			return null;
		}

		return new ServerConfig();
	}
	private boolean validate(ServerConfig config){
		return true;
	}
}
