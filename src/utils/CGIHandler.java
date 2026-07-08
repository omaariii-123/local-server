package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CGIHandler {

    public record CGIContext(Process process, Path tempFile) {
    }

    public CGIContext execute(Path scriptPath, String leftoverUri, String method, String interpreter)
            throws IOException {

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
        pb.redirectOutput(outputFile);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        System.out.println("CGI process started for script: " + scriptPath + " with PID: " + process.pid());
        System.out.println("Temporary output file created at: " + tempOutput.toString());
        return new CGIContext(process, tempOutput);
    }
}