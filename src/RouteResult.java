
import java.nio.file.Path;

public record RouteResult(
        Action action,
        int statusCode,
        Path resolvedPath,
        String contentType,
        String redirectUrl,
        CGIHandler.CGIContext cgiContext,
        String cookieHeader,
        String originalUri) {
    public enum Action {
        SERVE_FILE, DIRECTORY_LISTING, EXECUTE_CGI, REDIRECT, ERROR, DELETE_FILE
    }

}