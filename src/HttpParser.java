package src;


import java.nio.ByteBuffer;

import src.utils.RequestFsm;

public class HttpParser {
    private final StringBuilder token = new StringBuilder();
    private String currentHeaderName;
    private HttpRequest currentRequest = new HttpRequest();
    private RequestFsm state = RequestFsm.READ_METHOD;
    private Integer BodyBytes = 0;
    // private HttpResponse response;

    public void prepareForNextRequest() {
        // this.response = null;
        this.currentRequest = null;
        this.state = RequestFsm.READ_METHOD;
    }

    private boolean endsWithCRLF() {
        return token.length() >= 2
                && token.charAt(token.length() - 2) == '\r'
                && token.charAt(token.length() - 1) == '\n';
    }

    public HttpRequest ParseRequest(ByteBuffer buffer) {

        while (buffer.hasRemaining()) {

            byte b = buffer.get();

            switch (state) {
                case READ_METHOD:
                    if (b == ' ') {
                        currentRequest.requestLine.setMethod(token.toString());
                        token.setLength(0);
                        state = RequestFsm.READ_URI;
                    } else {
                        token.append((char) b);
                    }
                    break;
                case READ_URI:
                    if (b == ' ') {
                        currentRequest.requestLine.setPath(token.toString());
                        token.setLength(0);
                        state = RequestFsm.READ_VERSION;
                    } else {
                        token.append((char) b);
                    }
                    break;
                case READ_VERSION:
                    token.append((char) b);
                    if (endsWithCRLF()) {
                        currentRequest.requestLine.setProtocol(
                                token.substring(0, token.length() - 2));
                        token.setLength(0);
                        state = RequestFsm.READ_HEADER_NAME;

                    }
                    break;
                case READ_HEADER_NAME:
                    // Blank line -> end of headers
                    if (b == '\r') {
                        token.append((char) b);
                    } else if (b == '\n') {
                        token.append((char) b);
                        if (endsWithCRLF() && token.length() == 2) {
                            token.setLength(0);
                            String contentLength = currentRequest.Headers.get("Content-Length");
                            if (contentLength != null
                                    && Integer.parseInt(contentLength) > 0) {
                                state = RequestFsm.READING_BODY;
                                BodyBytes = Integer.parseInt((contentLength));
                            } else {
                                state = RequestFsm.REQUEST_COMPLETE;
                            }
                        }
                    } else if (b == ':') {
                        currentHeaderName = token.toString();
                        token.setLength(0);
                        state = RequestFsm.READ_HEADER_VALUE;
                    } else {
                        token.append((char) b);
                    }
                    break;

                case READ_HEADER_VALUE:

                    token.append((char) b);
                    if (endsWithCRLF()) {
                        currentRequest.Headers.put(
                                currentHeaderName,
                                token.substring(0, token.length() - 2).trim());
                        token.setLength(0);
                        state = RequestFsm.READ_HEADER_NAME;

                    }
                    break;

                case READING_BODY:

                    currentRequest.appendToBody(b);
                    BodyBytes--;
                    // TODO:
                    // Read exactly Content-Length bytes.
                    // When all bytes are read:
                    // String KeepAlive = currentRequest.Headers.get("Connection");
                    //
                    if (BodyBytes == 0) {
                        state = RequestFsm.REQUEST_COMPLETE;
                    }

                    break;
                case REQUEST_COMPLETE:

                    // TODO:
                    // Notify caller the request is complete.
                    // Prepare parser for next request if using keep-alive.

                    HttpRequest finished = currentRequest;
                    reset();
                    return finished;
                default:
                    break;
            }

        }
        return null;
    }

    private void reset() {
        currentRequest = new HttpRequest();
        BodyBytes = 0;
        state = RequestFsm.READ_METHOD;
        currentHeaderName = "";
    }

}
