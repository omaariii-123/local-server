
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
		ServerConfig config = new ServerConfig();
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
		JsonElement res = parser.parse();
		if (res instanceof JsonArray) {
			JsonArray arr = (JsonArray) res;
			arr.elements.forEach((x) -> System.err.println(x.toString()));
		} else if (res instanceof JsonObject){
			JsonObject obj = (JsonObject) res;
			obj.values.forEach((x, y) -> System.err.println(x +  y.toString()));

		}else {
			System.err.println(res);
		}
		if (!this.validate(config)){
			return null;
		}

		return config;
	}
	private boolean validate(ServerConfig config){
					System.out.println("testttt");
		return true;
	}
}
