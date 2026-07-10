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

        try (var stdin = process.getOutputStream()) {
            if (payload.length > 0) {
                stdin.write(payload);
            }
        } catch (IOException ignored) {
        }

        System.out.println("CGI process started for script: " + scriptPath + " with PID: " + process.pid());
        System.out.println("Temporary output file created at: " + tempOutput.toString());
        return new CGIContext(process, tempOutput);
    }
}