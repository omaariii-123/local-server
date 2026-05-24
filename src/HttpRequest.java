import java.nio.ByteBuffer;
import java.util.HashMap;

public class HttpRequest {

    public HashMap<String, String> Headers;
    RequestLine requestLine;
    private byte[] body;

    // public HttpRequest(String method, String path, HashMap<String, String>
    // headers, byte[] body) {
    // this.method = method;
    // this.path = path;
    // this.Headers = headers;
    // this.body = body;
    // }

    public boolean HasBody() {
        return this.body != null;
    }

}
