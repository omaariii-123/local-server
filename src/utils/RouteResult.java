package utils;

import java.net.http.HttpRequest;
import java.nio.file.Path;

public record RouteResult(
        Action action,
        int statusCode,
        Path resolvedPath,
        String contentType,
        String redirectUrl) {
    public enum Action {
        SERVE_FILE, DIRECTORY_LISTING, EXECUTE_CGI, REDIRECT, ERROR
    }

    static RouteResult route(HttpRequest req, ConfigLoader conf) {
        return new RouteResult(null, 0, null, null, null);
    }

}