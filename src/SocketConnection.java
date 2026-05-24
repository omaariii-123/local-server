
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import utils.Fsm;

public class SocketConnection {
    HttpParser parser = new HttpParser();
    ByteBuffer readBuffer = ByteBuffer.allocate(8000);
    Boolean keepAlive = true;
    SocketChannel socket;
    ByteBuffer writeBuffer;
    HttpRequest request;
    HttpResponse response;
    Fsm state;
    int bodyBytesRead = 0;

    public void prepareForNextRequest() {
        this.response = null;
        this.request = null;
        this.state = Fsm.REQUEST_LINE;
    }

    boolean BodyComplete() {
        String contentLengthStr = request.Headers.get("Content-Length");
        if (contentLengthStr == null)
            return true;

        int expected = Integer.parseInt(contentLengthStr);
        return bodyBytesRead >= expected;
    }

    public void handleCurrentState(int newBytesRead) {
        switch (this.state) {

            case Fsm.REQUEST_LINE:
                if (this.parser.findEndRequestLine(this.readBuffer) != -1) {
                    this.readBuffer.flip();
                    RequestLine requestLine = this.parser.RequestLineParser(readBuffer.toString());
                    this.request = new HttpRequest();
                    this.request.requestLine = requestLine;
                    this.readBuffer.compact();
                    this.state = Fsm.READING_HEADERS;
                    

                }
                break;

            case Fsm.READING_HEADERS:
                // reading the header until we found the first crlf
                if (this.parser.findEndHeader(this.readBuffer) != -1) {
                    this.readBuffer.flip();
                    this.request.Headers = this.parser
                            .HeadersParser(this.readBuffer);
                    this.bodyBytesRead = this.readBuffer.remaining();
                }
                if (this.request.HasBody()) {
                    this.state = Fsm.READING_BODY;
                } else {
                    this.state = Fsm.PROCESSING;

                }
                this.readBuffer.compact();
                break;
            case Fsm.READING_BODY:
                this.bodyBytesRead += newBytesRead;
                if (this.BodyComplete()) {
                    this.state = Fsm.PROCESSING;
                }
                break;
            case Fsm.PROCESSING:
                // routing
                // response
                break;

            default:
                break;
        }
    }
}
