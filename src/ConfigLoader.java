
package src;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
		}

		return this.validate(content.toString())? config : null;
	}
	private boolean validate(String data){
					System.out.println("testttt");
		return true;
	}
}
