
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import utils.Fsm;

public class SocketConnection {
    ByteBuffer readBuffer = ByteBuffer.allocate(8000);
    SocketChannel socket;
    ByteBuffer writeBuffer;
    HttpParser parser = new HttpParser();


    public void handleCurrentState(){
        
    }
    // public void handleCurrentState(int newBytesRead) {
    // switch (this.state) {

    // case Fsm.REQUEST_LINE:
    // if (this.parser.findEndRequestLine(readBuffer) != -1) {
    // readBuffer.flip();
    // RequestLine requestLine = this.parser.RequestLineParser(readBuffer);
    // request = new HttpRequest();
    // request.requestLine = requestLine;
    // readBuffer.compact();
    // state = Fsm.READING_HEADERS;
    // }

    // case Fsm.READING_HEADERS:
    // // reading the header until we found the first crlf
    // if (this.parser.findEndHeader(this.readBuffer) != -1) {
    // this.readBuffer.flip();
    // this.request.Headers = this.parser
    // .HeadersParser(this.readBuffer);
    // this.bodyBytesRead = this.readBuffer.remaining();
    // }
    // if (this.request.HasBody()) {
    // this.state = Fsm.READING_BODY;
    // } else {
    // this.state = Fsm.PROCESSING;

    // }
    // this.readBuffer.compact();
    // break;
    // case Fsm.READING_BODY:
    // this.bodyBytesRead += newBytesRead;
    // if (this.BodyComplete()) {
    // this.state = Fsm.PROCESSING;

    // }
    // break;
    // case Fsm.PROCESSING:
    // // routing
    // // response
    // break;

    // default:
    // break;
    // }
    // }
}
