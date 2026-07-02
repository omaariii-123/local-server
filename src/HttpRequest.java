package src;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;


public class HttpRequest {

    public HashMap<String, String> Headers = new HashMap<>();
    private RequestLine requestLine;
    private ByteArrayOutputStream body = new ByteArrayOutputStream();

public void appendToBody(byte b) {
    body.write(b);
}

}