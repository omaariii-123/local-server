import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class HttpParser {
    public int findEndHeader(ByteBuffer bytes) {

        int limit = bytes.limit();
        int state = 0;
        for (int i = 0; i < limit; i++) {
            if (bytes.get(i) == '\r' && state == 0)
                state = 1;
            else if (bytes.get(i) == '\n' && state == 1)
                state = 2;
            else if (bytes.get(i) == '\r' && state == 2)
                state = 3;
            else if (bytes.get(i) == '\n' && state == 3) {
                return i + 1;
            } else {
                state = bytes.get(i) == '\r' ? 1 : 0;
            }
        }
        return -1;
    }

    public int findEndRequestLine(ByteBuffer bytes) {
        int limit = bytes.limit();
        int state = 0;
        for (int i = 0; i < limit; i++) {
            if (bytes.get(i) == '\r' && state == 0)
                state = 1;
            else if (bytes.get(i) == '\n' && state == 1)
                state = 2;
            else if (bytes.get(i) != '\r' && state == 2) {
                return i + 1;
            } else {
                state = 0;
            }
        }
        return -1;
    }

    public HashMap<String, String> HeadersParser(ByteBuffer RAWheaders) {
        return new HashMap<>();
    }

    public RequestLine RequestLineParser(String RequestLine) {
        String[] args = RequestLine.split(" ");
        if (args.length != 3) {
            // TODO: should invoke BAD REQUSET LATER

        }
        RequestLine requestline = new RequestLine(args[0], args[1], args[2]);
        if (!requestline.validate()) {
            // TODO: should invoke BAD REQUSET LATER
        }
        return requestline;

    }

}
