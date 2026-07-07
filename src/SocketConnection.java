
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import utils.RouteResult;

public class SocketConnection {
    public enum ConnectionFsm {
        READING,
        WRITING,
        STREAMING_CGI,
        CLOSE,

    }

    public ByteBuffer readBuffer = ByteBuffer.allocateDirect(8000);
    public ByteBuffer writeBuffer = ByteBuffer.allocateDirect(8000);
    public SocketChannel socket;

    public HttpParser parser = new HttpParser();
    public Queue<RouteResult> responses = new LinkedList<>();
    public ConcurrentLinkedQueue<ByteBuffer> CgiBuffers = new ConcurrentLinkedQueue<>();
    public ConnectionFsm state = ConnectionFsm.READING;
    public FileChannel activeFileChannel = null;
    public ReadableByteChannel activeCgiChannel = null;

    public boolean isChunked = false;
    public boolean isKeepAlive = true;
    public boolean headersSent = false;
    public boolean cgiStreamFinished = false;

    public SocketConnection(SocketChannel socket) {
        this.socket = socket;
    }

    public void HandlePhase(SelectionKey key) {

        try {
            switch (state) {
                case READING:
                    ReadingRequest(key);
                    break;

                case WRITING:
                    if (!responses.isEmpty()) {
                        RouteResult resp = responses.poll();
                        prepareResponse(resp);
                        WritingResponse(key);
                        break;
                    }

                case STREAMING_CGI:
                    if (!responses.isEmpty()) {
                        RouteResult resp = responses.poll();
                        prepareResponse(resp);
                        WriteChunkedResponse(key);
                        break;
                    }

                    break;
                case CLOSE:
                    closeConnection(key);
                    break;
            }
        } catch (Exception e) {

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

        }
        readBuffer.flip();
        HttpRequest request;
        while ((request = parser.ParseRequest(readBuffer)) != null) {
            // routing happen here

            RouteResult respo = new RouteResult();
            responses.add(respo);
            isKeepAlive = request.isKeepAlive();
            System.out.print(request.toString());
            state = ConnectionFsm.WRITING;
            key.interestOps(SelectionKey.OP_WRITE);

        }
        readBuffer.compact();

    }

    public void FormatChunk(ByteBuffer buffer) {

        int Payloadsize = buffer.remaining();
        byte[] HexByte = (String.format("%s\r\n", Integer.toHexString(Payloadsize))).getBytes();
        ByteBuffer Formated = ByteBuffer.allocate(Payloadsize + 2 + HexByte.length);

        Formated.put(HexByte);
        Formated.put(buffer);
        Formated.put("\r\n".getBytes());
        Formated.flip();
        CgiBuffers.offer(Formated);
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

                if (!writeBuffer.hasRemaining()) {
                    ByteBuffer nextChunk = CgiBuffers.poll();
                    if (nextChunk != null) {
                        socket.write(nextChunk);
                    }
                    if (nextChunk.hasRemaining()) {
                        writeBuffer.clear();
                        writeBuffer.put(nextChunk);
                        writeBuffer.flip();
                    }

                    // if (BytesRead == -1) {
                    // this.cgiStreamFinished = true;
                    // writeBuffer.put("\r\n".getBytes());
                    // activeCgiChannel.close();
                    // this.activeCgiChannel = null;
                    // this.state = ConnectionFsm.READING;
                    // }
                    // if (BytesRead > 0) {
                    // writeBuffer.put(FormatChunk(rawBytesBuffer));

                    // }
                } else if (cgiStreamFinished) {
                    activeCgiChannel.close();
                    this.activeCgiChannel = null;
                    state = ConnectionFsm.READING;
                    key.interestOps(SelectionKey.OP_READ);
                    writeBuffer.put("\r\n".getBytes());
                }

            }

        } catch (Exception e) {
            // TODO: handle exception
        }

    }

    public void WritingResponse(SelectionKey key) throws IOException {
        if (!writeBuffer.hasRemaining() && !headersSent) {
            if (!responses.isEmpty()) {
                RouteResult resp = responses.poll();
                prepareResponse(resp);
                headersSent = true;

            } else {
                cleanUpAfterResponse(key);
                return;
            }
        }

        // drainign the buffer first to os thread making room
        if (writeBuffer.hasRemaining()) {
            socket.write(writeBuffer);
        }

        if (!writeBuffer.hasRemaining()) {

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
            } else if (isChunked && activeCgiChannel != null) {
                state = ConnectionFsm.STREAMING_CGI;

            } else {
                cleanUpAfterResponse(key);
            }

        }

    }

    // public void WriteChunkedResponse(SelectionKey key) throws IOException {
    // if (!writeBuffer.hasRemaining()) {
    // int Bytesize = writeBuffer.limit();
    // String chunkedWrite = Integer.toHexString(Bytesize) + "\r\n";

    // }

    // }

    private void prepareResponse(RouteResult result) throws IOException {
        StringBuilder headers = new StringBuilder();

        switch (result.action()) {
            case REDIRECT:
                // 302 Redirect requires a "Location" header pointing to the new URL
                headers.append("HTTP/1.1 ").append(result.statusCode()).append(" Found\r\n")
                        .append("Location: ").append(result.redirectUrl()).append("\r\n")
                        .append("Content-Length: 0\r\n") // No body needed for redirect
                        .append("Connection: ").append(isKeepAlive ? "keep-alive" : "close").append("\r\n\r\n");

                this.isChunked = false;
                this.activeFileChannel = null;

                // Load all of it into the write buffer right now
                writeBuffer.clear();
                writeBuffer.put(headers.toString().getBytes());
                writeBuffer.flip();
                break;

            case ERROR:
                // If it's a simple error without a custom file page, send a quick string
                String errorBody = "<h1>Error " + result.statusCode() + "</h1>";
                headers.append("HTTP/1.1 ").append(result.statusCode()).append(" Error\r\n")
                        .append("Content-Type: text/html\r\n")
                        .append("Content-Length: ").append(errorBody.length()).append("\r\n\r\n");

                this.isChunked = false;
                this.activeFileChannel = null;

                writeBuffer.clear();
                writeBuffer.put(headers.toString().getBytes());
                writeBuffer.put(errorBody.getBytes()); // Put both headers and body together
                writeBuffer.flip();
                break;

            case SERVE_FILE:
                long fileSize = Files.size(result.resolvedPath());
                headers.append("HTTP/1.1 ").append(result.statusCode()).append(" OK\r\n")
                        .append("Content-Type: ").append(result.contentType()).append("\r\n")
                        .append("Content-Length: ").append(fileSize).append("\r\n\r\n");

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

            default:
                // cgi execution to feed chunked data
                headers.append("HTTP/1.1 200 OK\r\n")
                        .append("Transfer-Encoding: chunked\r\n\r\n");
                this.isChunked = true;
                // this.activeCgiChannel = Channels.newChannel(process.getInputStream());

                writeBuffer.clear();
                writeBuffer.put(headers.toString().getBytes());
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

        if (!isKeepAlive) {
            closeConnection(key);
            state = ConnectionFsm.CLOSE;
        } else {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void closeConnection(SelectionKey key) throws IOException {
        if (activeFileChannel != null)
            activeFileChannel.close();
        socket.close();
        key.cancel();
    }
}
