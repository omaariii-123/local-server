package src;

import java.util.HashMap;

public class HttpResponse {
    Integer StatusCode;
    HashMap<String,String> Headers;
    byte[] Body;


    public HttpResponse(Integer statusCode, byte[] body) {
        this.StatusCode = statusCode;
        this.Body = body;
        this.Headers = new HashMap<>();
        this.Headers.put("Content-Length", String.valueOf(body.length));
        this.Headers.put("Content-Type", "text/html");
    }
    
}
