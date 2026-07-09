package utils;

import java.net.HttpCookie;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.List;

public class Cookie {

    HttpCookie Cookie;

    public void setCookie(HttpCookie cookie) {
        Cookie = cookie;

    }

    public void parseCookie(HttpRequest req) {
        HttpHeaders headers = req.headers();
        List<HttpCookie> cookies  = HttpCookie.parse(headers.firstValue("Cookie").get());
    }
}