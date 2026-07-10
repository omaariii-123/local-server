package src;

import java.util.Set;

public class RequestLine {

    private static final Set<String> METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH",
            "DELETE", "OPTIONS", "HEAD"
    );

    String path;
    String method;
    String protocol;


    public boolean validate() {

        if (!"HTTP/1.1".equals(protocol)) {
            return false;
        }

        if (!METHODS.contains(method)) {
            return false;
        }

        if (path == null || path.length() > 2048) {
            return false;
        }

        if (path.contains("..")) {
            return false;
        }

        return true;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public String getPath(){
        return path;
    }
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}