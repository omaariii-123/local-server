import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class HttpRequest {

    public HashMap<String, String> Headers = new HashMap<>();
    public ByteArrayOutputStream body = new ByteArrayOutputStream();
    RequestLine requestLine = new RequestLine();

    public boolean HasBody() {
        return this.body.size() != 0;
    }

    public void appendToBody(byte b) {
        body.write(b);
    }

    public boolean isKeepAlive() {
        String connection = Headers.get("connection");

        if (connection != null && connection.equalsIgnoreCase("keep-alive")) {
            return true;
        }
        return false;
    }

}
