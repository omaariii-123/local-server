package src;

import java.util.HashMap;
import java.util.Map;
import src.HttpResponse;

public class Errorhandler {
    private Map<Integer, String> errorPagePaths = new HashMap<>();
    Errorhandler(Map<Integer, String> errorPagePaths) {
        this.errorPagePaths = errorPagePaths;
    }
    // public void loadErrorPages(JSONObject errorPagesConfig) {
    // }
    
    public HttpResponse handleError(int statusCode, String message) {
        String body = getErrorPageContent(statusCode, message);
        return new HttpResponse(statusCode, body.getBytes());
    }


    private String getErrorPageContent(int statusCode, String message) {
        if (errorPagePaths.containsKey(statusCode)) {
            return errorPagePaths.get(statusCode);

        } else {
            return DefaultErr(statusCode, message);
        }
    }
    
    private String DefaultErr(int statusCode, String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><title>Error %d</title></head>
            <body>
                <h1>Error %d</h1>
                <p>%s</p>
            </body>
            </html>
        """, statusCode, statusCode, message);
    }
}
