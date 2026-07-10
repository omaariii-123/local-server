import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CGIHandler {

    public record CGIContext(Process process, Path tempFile) {
    }

    public CGIContext execute(Path scriptPath, String leftoverUri, String method, String interpreter,
            byte[] body, String contentType, String queryString) throws IOException {

        // to absolute path -> relative path to root + relative help to prevent the
        // wrong comparison and searching path bg
        // normalizing the path remove all redudant element (. , .. ) preventing the
        // travaresal attack
        Path absoluteScript = scriptPath.toAbsolutePath().normalize();
        Path tempOutput = Files.createTempFile("cgi_out_", ".tmp");
        File outputFile = tempOutput.toFile();

        ProcessBuilder pb = new ProcessBuilder(interpreter, absoluteScript.toString());
        Map<String, String> env = pb.environment();
        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        env.put("SERVER_PROTOCOL", "HTTP/1.1");
        env.put("REQUEST_METHOD", method);
        env.put("SCRIPT_NAME", absoluteScript.getFileName().toString());
        env.put("PATH_INFO", leftoverUri.isEmpty() ? "/" : leftoverUri);
        env.put("QUERY_STRING", queryString != null ? queryString : "");
        byte[] payload = body != null ? body : new byte[0];
        env.put("CONTENT_LENGTH", String.valueOf(payload.length));
        if (contentType != null) {
            env.put("CONTENT_TYPE", contentType);
        }
        pb.redirectOutput(outputFile);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Feed the request body to the script's stdin, then close it so a script
        // blocked on e.g. sys.stdin.read() sees EOF instead of hanging forever.
        // Done synchronously, right here on the same event-loop thread - no helper
        // thread, per the one-process/one-thread rule. The OS pipe buffer (tens of
        // KB) absorbs small/medium bodies without blocking; a body close to
        // clientMaxBodySize could in theory apply brief backpressure if the script is
        // slow to start reading, same tradeoff the rest of this codebase already
        // accepts for synchronous file writes on uploads (see Router.handle()).
        try (var stdin = process.getOutputStream()) {
            if (payload.length > 0) {
                stdin.write(payload);
            }
        } catch (IOException ignored) {
            // Process may have already exited / closed its stdin - nothing to do.
        }

        System.out.println("CGI process started for script: " + scriptPath + " with PID: " + process.pid());
        System.out.println("Temporary output file created at: " + tempOutput.toString());
        return new CGIContext(process, tempOutput);
    }
}