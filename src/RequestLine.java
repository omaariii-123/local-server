import java.util.Set;
import java.util.regex.Pattern;

public class RequestLine {

    private static final Set<String> METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH",
            "DELETE", "OPTIONS", "HEAD"
    );

    private static final Pattern PATH_PATTERN = Pattern.compile(
            "^/[A-Za-z0-9._~!$&'()*+,;=:@/%-]*(\\?[A-Za-z0-9._~!$&'()*+,;=:@/%?&=-]*)?$"
    );

    String path;
    String method;
    String protocol;

    public RequestLine(String method ,String path ,String protocol) {
            this.path = path;
            this.method = method;
            this.protocol = protocol;
    }

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

        return PATH_PATTERN.matcher(path).matches();
    }
}