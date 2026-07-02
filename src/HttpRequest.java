import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class HttpRequest {

    public HashMap<String, String> Headers = new HashMap<>();
    private ByteArrayOutputStream body = new ByteArrayOutputStream();
    RequestLine requestLine;
    //
    public boolean HasBody() {
        return this.body.size() != 0;
    }

    public void appendToBody(byte b) {
        body.write(b);
    }

}
