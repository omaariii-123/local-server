
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
		//ServerConfig config = new ServerConfig();
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
		
		switch (element) {
    		case JsonString s  -> System.out.println("Parsed a string: " + s.value);
    		case JsonNumber n  -> System.out.println("Parsed a number: " + n.value);
    		case JsonBoolean b -> System.out.println("Parsed a boolean: " + b.value);
    		case JsonObject o  -> Route.hydrate(o);
    		case JsonArray a   -> System.out.println("Found an array of size: " + a.elements.size());
    		case JsonNull n    -> System.out.println("Value was explicitly null.");
    		case null          -> System.out.println("Key didn't exist in the map at all.");
    		default            -> throw new IllegalStateException("Unknown AST node!");
		}
		if (!this.validate(null)){
			return null;
		}

		return new ServerConfig(null);
	}
	private boolean validate(ServerConfig config){
					System.out.println("testttt");
		return true;
	}
}
