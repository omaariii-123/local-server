package src;

import java.util.HashMap;

public class HttpResponse {
    Integer StatusCode;
    HashMap<String,String> Headers;
    byte[] Body;
    RequestLine redirectRequestLine;


    public HttpResponse(Integer statusCode, byte[] body) {
        this.StatusCode = statusCode;
        this.Body = body;
        this.Headers = new HashMap<>();
        this.Headers.put("Content-Length", String.valueOf(body.length));
        this.Headers.put("Content-Type", "text/html");
    }

    public HttpResponse(Integer stC, String redirectUrl) {
        this.redirectRequestLine = new RequestLine("GET", redirectUrl, "HTTP/1.1");
        this.StatusCode = stC;
        this.Body = new byte[0];
        this.Headers = new HashMap<>();
        this.Headers.put("Location", redirectUrl);



    
}
