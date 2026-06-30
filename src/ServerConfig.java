package src;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {
	public final JsonElement root;
	public String host = "127.0.0.1";
	public int clientMaxBodySize = 0;
    public boolean isDefaultServer = false;
	public List<Integer> ports = new ArrayList<>();
    public List<Route> routes = new ArrayList<>();

	public ServerConfig(JsonElement root) {
		this.root = root;
	}

	public void hydrate() {
		
	}
}
