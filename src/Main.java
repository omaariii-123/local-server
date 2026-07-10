
import java.util.List;

public class Main {
    public static void main(String[] args) {
        ConfigLoader loader = new ConfigLoader("config.json");
        List<ServerConfig> configs = loader.load();

        if (configs == null || configs.isEmpty()) {
            System.err.println("Fatal: Could not load configuration.");
            return;
        }

        try {
            Server server = new Server(configs);
            server.start(); 
        } catch (Exception e) {
            System.err.println("Server crashed: " + e.getMessage());
        }
    }
}