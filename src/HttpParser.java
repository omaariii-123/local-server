
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class HttpParser {
    public enum RequestFsm {
        READ_METHOD,
        READ_URI,
        READ_VERSION,
        READ_HEADER_NAME,
        READ_HEADER_VALUE,
        READING_BODY,

        READ_CHUNK_SIZE,
        READ_CHUNK_DATA,
        READ_CHUNK_CRLF,
        READ_MULTIPART_HEADERS,
        READ_MULTIPART_BODY,

        REQUEST_COMPLETE
    }

    private final StringBuilder token = new StringBuilder();
    private String currentHeaderName;
    private HttpRequest currentRequest = new HttpRequest();
    private RequestFsm state = RequestFsm.READ_METHOD;
    private Integer BodyBytes = 0;

    private String boundary = null;
    private FileChannel fileUploadChannel = null;
    private final byte[] boundaryBuffer = new byte[1024];
    private int boundaryBufIdx = 0;

    private boolean endsWithCRLF() {
        return token.length() >= 2
                && token.charAt(token.length() - 2) == '\r'
                && token.charAt(token.length() - 1) == '\n';
    }

    public HttpRequest ParseRequest(ByteBuffer buffer) throws IOException {

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
                            String transferEncoding = currentRequest.Headers.get("transfer-encoding");
                            String contentType = currentRequest.Headers.get("content-type");
                            String contentLength = currentRequest.Headers.get("content-length");
                            if ("chunked".equalsIgnoreCase(transferEncoding)) {
                                state = RequestFsm.READ_CHUNK_SIZE;
                            } else if (contentType != null && contentType.startsWith("multipart/form-data")) {
                                if (contentLength != null) {
                                    state = RequestFsm.READING_BODY;
                                    BodyBytes = Integer.parseInt(contentLength);
                                } else {
                                    HttpRequest finished = currentRequest;
                                    reset();
                                    return finished;
                                }
                            } else if (contentLength != null
                                    && Integer.parseInt(contentLength) > 0) {
                                state = RequestFsm.READING_BODY;
                                BodyBytes = Integer.parseInt((contentLength));
                            } else {
                                HttpRequest finished = currentRequest;
                                reset();
                                return finished;
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
                                currentHeaderName.toLowerCase(),
                                token.substring(0, token.length() - 2).trim());
                        token.setLength(0);
                        state = RequestFsm.READ_HEADER_NAME;

                    }
                    break;

                case READ_CHUNK_SIZE:
                    token.append((char) b);
                    if (endsWithCRLF()) {
                        String hexSize = token.substring(0, token.length() - 2).trim();
                        this.BodyBytes = Integer.parseInt(hexSize, 16);
                        token.setLength(0);

                        if (this.BodyBytes == 0) {
                            HttpRequest finished = currentRequest;
                            reset();
                            return finished;
                        } else {
                            state = RequestFsm.READ_CHUNK_DATA;
                        }
                    }
                    break;

                case READ_CHUNK_DATA:
                    currentRequest.appendToBody(b);
                    this.BodyBytes--;
                    if (this.BodyBytes == 0) {
                        state = RequestFsm.READ_CHUNK_CRLF;
                    }
                    break;

                case READ_CHUNK_CRLF:
                    token.append((char) b);
                    if (endsWithCRLF()) {
                        token.setLength(0);
                        state = RequestFsm.READ_CHUNK_SIZE;
                    }
                    break;

                case READ_MULTIPART_BODY:
                    boundaryBuffer[boundaryBufIdx++] = b;

                    if (boundaryBufIdx >= this.boundary.length()) {
                        String currentWindow = new String(boundaryBuffer, 0, boundaryBufIdx, StandardCharsets.US_ASCII);

                        if (currentWindow.contains(this.boundary)) {
                            if (fileUploadChannel != null) {
                                try {
                                    fileUploadChannel.close();
                                } catch (IOException ignored) {
                                }
                                fileUploadChannel = null;
                            }

                            HttpRequest finished = currentRequest;
                            reset();
                            return finished;
                        }
                    }

                    if (boundaryBufIdx > this.boundary.length()) {
                        if (fileUploadChannel != null) {
                            ByteBuffer singleByteBuf = ByteBuffer.wrap(boundaryBuffer, 0, 1);
                            fileUploadChannel.write(singleByteBuf);
                        }
                        System.arraycopy(boundaryBuffer, 1, boundaryBuffer, 0, boundaryBufIdx - 1);
                        boundaryBufIdx--;
                    }
                    break;

                case READING_BODY:

                    currentRequest.appendToBody(b);
                    BodyBytes--;
                    if (BodyBytes == 0) {
                        HttpRequest finished = currentRequest;
                        reset();
                        return finished;
                    }

                    break;

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
        token.setLength(0);
        currentHeaderName = null;

        this.boundary = null;
        if (this.fileUploadChannel != null) {
            try {
                this.fileUploadChannel.close();
            } catch (Exception ignored) {
            }
            this.fileUploadChannel = null;
        }
        this.boundaryBufIdx = 0;
    }

}