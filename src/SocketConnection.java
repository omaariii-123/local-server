import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.nio.charset.StandardCharsets;

public class SocketConnection {
    public enum ConnectionFsm {
        READING,
        WRITING,
        STREAMING_CGI,
        CLOSE,

    }

    private long lastActiveTime = System.currentTimeMillis();
    private final Integer TIMEOUT_MS = 30000;
    public ByteBuffer readBuffer = ByteBuffer.allocateDirect(8000);
    public ByteBuffer writeBuffer = ByteBuffer.allocateDirect(8000);
    public SocketChannel socket;
    public HttpParser parser = new HttpParser();
    public Queue<RouteResult> responses = new LinkedList<>();
    public ConnectionFsm state = ConnectionFsm.READING;
    public FileChannel activeFileChannel = null;
    public FileChannel activeCgiChannel = null;
    public Process Cgiprocess = null;
    public CGIHandler.CGIContext CGIContext = null;
    public boolean isChunked = false;
    public boolean isKeepAlive = true;
    public boolean headersSent = false;
    public boolean cgiStreamFinished = false;
    private final Router router;
    public List<ServerConfig> serverConfig;
    private String pendingCookieHeader = null;

    public SocketConnection(SocketChannel socket, Router router, List<ServerConfig> config) {
        this.socket = socket;
        this.router = router;
        this.serverConfig = config;
        this.writeBuffer.limit(0);
    }

    public void HandlePhase(SelectionKey key) {

        try {
            switch (state) {
                case READING:
                    ReadingRequest(key);
                    break;

                case WRITING:
                    WritingResponse(key);
                    break;
                case STREAMING_CGI:

                    if (this.Cgiprocess != null) {

                        if (this.activeCgiChannel == null) {
                            if (!Cgiprocess.isAlive()) {
                                this.activeCgiChannel = FileChannel.open(CGIContext.tempFile(),
                                        StandardOpenOption.READ);
                                setupCgiResponseHeaders();
                            } else {
                                return;
                            }
                        }
                        WriteChunkedResponse(key);
                    } else {
                        cleanUpCgi(key);
                    }
                    break;

                case CLOSE:
                    closeConnection(key);
                    break;
            }
        } catch (

        Exception e) {
            try {
                closeConnection(key);
            } catch (IOException ignore) {
            }
        }

    }

    public void ReadingRequest(SelectionKey key) throws IOException {
        int BytesRead;
        while (true) {
            try {
                BytesRead = socket.read(readBuffer);
            } catch (Exception e) {
                closeConnection(key);
                return;
            }
            if (BytesRead < 0) {
                closeConnection(key);
                return;
            }
            if (BytesRead == 0) {
                break;
            }

            UpdateTimeout();
        }
        readBuffer.flip();
        HttpRequest request;
        while ((request = parser.ParseRequest(readBuffer)) != null) {
            // routing happen here

            RouteResult respo = router.handle(request, serverConfig, socket.socket().getLocalPort());
            responses.add(respo);
            isKeepAlive = request.isKeepAlive();
            System.out.print(request.toString());
            state = ConnectionFsm.WRITING;
            key.interestOps(SelectionKey.OP_WRITE);
            UpdateTimeout();
        }
        readBuffer.compact();

    }

    public void UpdateTimeout() {
        lastActiveTime = System.currentTimeMillis();
    }

    public ByteBuffer FormatChunk(ByteBuffer buffer) {

        int Payloadsize = buffer.remaining();
        byte[] HexByte = (String.format("%s\r\n", Integer.toHexString(Payloadsize))).getBytes();
        ByteBuffer Formated = ByteBuffer.allocate(Payloadsize + 2 + HexByte.length);

        Formated.put(HexByte);
        Formated.put(buffer);
        Formated.put("\r\n".getBytes());
        Formated.flip();
        return Formated;

    }

    public void WriteChunkedResponse(SelectionKey key) {

        try {

            if (writeBuffer.hasRemaining()) {
                socket.write(writeBuffer);
                if (writeBuffer.hasRemaining()) {
                    // that we couldnt write the the full write buffer to the socket meaning the the
                    // is buffer is full , try on the next OP_write
                    return;
                }
            }

            if (!writeBuffer.hasRemaining()) {
                if (cgiStreamFinished) {
                    cleanUpCgi(key);
                    return;

                }
                if (activeCgiChannel != null) {
                    if (activeCgiChannel.position() < activeCgiChannel.size()) {
                        writeBuffer.clear();
                        activeCgiChannel.read(writeBuffer);
                        writeBuffer.flip();
                        ByteBuffer Formated = FormatChunk(writeBuffer);
                        socket.write(Formated);
                        if (Formated.hasRemaining()) {
                            writeBuffer.clear();
                            writeBuffer.put(Formated);
                            writeBuffer.flip();
                        } else {
                            writeBuffer.clear();
                            writeBuffer.flip();
                        }
                    } else {
                        // cleanUpAfterResponse(key);
                        writeBuffer.clear();
                        writeBuffer.put("0\r\n\r\n".getBytes());
                        writeBuffer.flip();
                        socket.write(writeBuffer);
                        this.cgiStreamFinished = true;

                    }
                }
                UpdateTimeout();

            }

        } catch (Exception e) {
            try {
                closeConnection(key);
            } catch (IOException ignore) {
            }
        }

    }

    public void WritingResponse(SelectionKey key) throws IOException {
        if (!writeBuffer.hasRemaining() && !headersSent) {
            if (!responses.isEmpty()) {
                RouteResult resp = responses.poll();
                prepareResponse(resp, key);
                headersSent = true;

            } else {
                cleanUpAfterResponse(key);
                return;
            }
        }

        // draining the buffer first to os thread making room for new bytes
        if (writeBuffer.hasRemaining()) {
            socket.write(writeBuffer);
        }

        if (!writeBuffer.hasRemaining()) {

            if (state == ConnectionFsm.STREAMING_CGI) {
                return;
            }
            if (activeFileChannel != null) {
                // if active channel is open and we didnt reach the end of file
                // we read from the activechanel the writedown to the bytebuffer , also we right
                // from the byte buffer to the network
                if (activeFileChannel.position() < activeFileChannel.size()) {
                    writeBuffer.clear();
                    activeFileChannel.read(writeBuffer);
                    writeBuffer.flip();
                    socket.write(writeBuffer);

                } else {
                    cleanUpAfterResponse(key);
                }
            } else {
                cleanUpAfterResponse(key);
            }

        }
        UpdateTimeout();

    }

    private void prepareResponse(RouteResult result, SelectionKey key) throws IOException {
        StringBuilder headers = new StringBuilder();
        pendingCookieHeader = result.cookieHeader();
        switch (result.action()) {
            case REDIRECT:

                headers.append("HTTP/1.1 ").append(result.statusCode()).append(" Found\r\n")
                        .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                        .append("Location: ").append(result.redirectUrl()).append("\r\n")
                        .append("Content-Length: 0\r\n") // No body needed for redirect
                        .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                this.isChunked = false;
                this.activeFileChannel = null;

                writeBuffer.clear();
                writeBuffer.put(headers.toString().getBytes());
                writeBuffer.flip();
                break;

            case ERROR:
                this.isChunked = false;
                if (result.resolvedPath() != null && Files.exists(result.resolvedPath())) {
                    // Serve the custom error page configured in errorPages, streaming it the
                    // same way SERVE_FILE does so arbitrarily large pages don't overflow
                    // the fixed-size writeBuffer.
                    long errSize = Files.size(result.resolvedPath());
                    headers.append("HTTP/1.1 ").append(result.statusCode()).append(" Error\r\n")
                            .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                            .append("Content-Type: text/html\r\n")
                            .append("Content-Length: ").append(errSize).append("\r\n")
                            .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                    this.activeFileChannel = FileChannel.open(result.resolvedPath(), StandardOpenOption.READ);

                    writeBuffer.clear();
                    writeBuffer.put(headers.toString().getBytes());
                    writeBuffer.flip();
                } else {
                    byte[] errorBody = ("<h1>Error " + result.statusCode() + "</h1>")
                            .getBytes(StandardCharsets.UTF_8);
                    headers.append("HTTP/1.1 ").append(result.statusCode()).append(" Error\r\n")
                            .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                            .append("Content-Type: text/html\r\n")
                            .append("Content-Length: ").append(errorBody.length).append("\r\n")
                            .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                    this.activeFileChannel = null;

                    writeBuffer.clear();
                    writeBuffer.put(headers.toString().getBytes());
                    writeBuffer.put(errorBody); // Put both headers and body together
                    writeBuffer.flip();
                }
                break;

            case SERVE_FILE:
                long fileSize = Files.size(result.resolvedPath());
                headers.append("HTTP/1.1 ").append(result.statusCode()).append(" OK\r\n")
                        .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                        .append("Content-Type: ").append(result.contentType()).append("\r\n")
                        .append("Content-Length: ").append(fileSize).append("\r\n")
                        .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                this.isChunked = false;
                // This handles normal files AND custom error pages (e.g., error_pages/404.html)
                // Pointing to the file in disk after we r going to consume it in the writing
                // respons whatever we have
                // it open also it has an internal buffer the keep position and limit increement
                // auto when you read from it
                this.activeFileChannel = FileChannel.open(result.resolvedPath(), StandardOpenOption.READ);

                writeBuffer.clear();
                // we append the headers to be sent first then the body comes from filechannel
                writeBuffer.put(headers.toString().getBytes());
                writeBuffer.flip();

                break;

            case DIRECTORY_LISTING:

                byte[] dirHtml = generateDirectoryHtml(result.resolvedPath(), result.originalUri());
                headers.append("HTTP/1.1 200 OK\r\n")
                        .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                        .append("Content-Type: text/html\r\n")
                        .append("Content-Length: ").append(dirHtml.length).append("\r\n")
                        .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                this.isChunked = false;
                this.activeFileChannel = null;

                writeBuffer.clear();
                writeBuffer.put(headers.toString().getBytes());
                if (writeBuffer.remaining() >= dirHtml.length) {
                    writeBuffer.put(dirHtml);
                } else {
                    writeBuffer.put(dirHtml, 0, writeBuffer.remaining());
                }

                writeBuffer.flip();
                break;
            case DELETE_FILE:
                headers.append("HTTP/1.1 ").append(result.statusCode()).append(" OK\r\n")
                        .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                        .append("Content-Length: 0\r\n")
                        .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                this.isChunked = false;
                this.activeFileChannel = null;

                writeBuffer.clear();
                writeBuffer.put(headers.toString().getBytes());
                writeBuffer.flip();
                break;
            default:
                // cgi execution to feed chunked data
                headers.append("HTTP/1.1 200 OK\r\n")
                        .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                        .append("Transfer-Encoding: chunked\r\n\r\n");
                this.isChunked = true;
                // this.activeCgiChannel = FileChannel.open(CGIContext.tempFile().getRoot(),
                // this.CGIContext = cgiHandler.execute(result.resolvedPath(), "/", "GET",
                // "python");
                this.CGIContext = result.cgiContext();
                this.Cgiprocess = result.cgiContext().process();
                this.state = ConnectionFsm.STREAMING_CGI;
                key.interestOps(0);
                final Selector currentSector = key.selector();
                // when the process it about to do we added a callback to wakeup the selector
                // and switch for OP_write so we dont w8 for the scipt bieng processed instead
                // we do another operation until the process it dead meaning we have the full
                // output ready to stream it
                CGIContext.process().onExit().thenAccept(p -> {
                    key.interestOps(SelectionKey.OP_WRITE);
                    currentSector.wakeup();
                });

                writeBuffer.clear();
                // writeBuffer.put(headers.toString().getBytes());
                writeBuffer.flip();
                break;

        }
        this.headersSent = false;
    }

    private void cleanUpAfterResponse(SelectionKey key) throws IOException {
        if (activeFileChannel != null) {
            activeFileChannel.close();
            activeFileChannel = null;
        }
        pendingCookieHeader = null;
        if (!responses.isEmpty()) {
            state = ConnectionFsm.WRITING;
            key.interestOps(SelectionKey.OP_WRITE);
        } else if (!isKeepAlive) {
            closeConnection(key);
            state = ConnectionFsm.CLOSE;
        } else {
            state = ConnectionFsm.READING;
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void cleanUpCgi(SelectionKey key) throws IOException {
        if (activeCgiChannel != null) {
            activeCgiChannel.close();
            activeCgiChannel = null;
        }
        this.headersSent = false;
        this.cgiStreamFinished = false;
        this.Cgiprocess = null;
        Files.deleteIfExists(this.CGIContext.tempFile());
        this.CGIContext = null;
        cleanUpAfterResponse(key);

    }

    private void closeConnection(SelectionKey key) throws IOException {
        if (activeFileChannel != null) {
            activeFileChannel.close();
        }
        if (Cgiprocess != null && Cgiprocess.isAlive()) {
            Cgiprocess.destroy();
        }
        if (activeCgiChannel != null) {
            activeCgiChannel.close();
        }
        if (CGIContext != null) {
            Files.deleteIfExists(this.CGIContext.tempFile());
        }

        socket.close();
        key.cancel();
    }

    private void setupCgiResponseHeaders() throws IOException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(2048);
        activeCgiChannel.read(headerBuffer);
        headerBuffer.flip();

        byte[] bytes = new byte[headerBuffer.remaining()];
        headerBuffer.get(bytes);
        String fileContent = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        int delimiterIdx = fileContent.indexOf("\r\n\r\n");
        int delimiterLen = 4;
        if (delimiterIdx == -1) {
            delimiterIdx = fileContent.indexOf("\n\n");
            delimiterLen = 2;
        }

        StringBuilder responseEnvelope = new StringBuilder();
        responseEnvelope.append("HTTP/1.1 200 OK\r\n")
                .append(pendingCookieHeader != null ? "Set-Cookie: " + pendingCookieHeader + "\r\n" : "")
                .append("Transfer-Encoding: chunked\r\n")
                .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n");

        if (delimiterIdx != -1) {
            String cgiHeaders = fileContent.substring(0, delimiterIdx);
            responseEnvelope.append(cgiHeaders).append("\r\n\r\n");
            activeCgiChannel.position(delimiterIdx + delimiterLen);
        } else {
            responseEnvelope.append("\r\n");
            activeCgiChannel.position(0);
        }

        writeBuffer.clear();
        writeBuffer.put(responseEnvelope.toString().getBytes());
        writeBuffer.flip();
        this.headersSent = true;
    }

    public void CheckTimeout(SelectionKey key) throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastActiveTime > TIMEOUT_MS) {
            closeConnection(key);
        }
    }

    public static byte[] generateDirectoryHtml(Path dirPath, String requestUri) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Index of ").append(requestUri).append("</title></head><body>");
        html.append("<h1>Index of ").append(requestUri).append("</h1><hr><pre>");

        // Add a "Go Back" link if we aren't at the root
        if (!requestUri.equals("/")) {
            html.append("<a href=\"../\">../</a>\n");
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                boolean isDir = Files.isDirectory(entry);

                if (isDir) {
                    fileName += "/";
                }

                String href = requestUri.endsWith("/") ? requestUri + fileName : requestUri + "/" + fileName;

                html.append("<a href=\"").append(href).append("\">").append(fileName).append("</a>\n");
            }
        }

        html.append("</pre><hr></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }
}