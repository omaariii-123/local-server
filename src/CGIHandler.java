package src;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import src.Errorhandler;

import src.*;



public class CGIHandler {
    private String cgiRoot; 
    private Errorhandler ErrorHandler = new Errorhandler();
    
    public CGIHandler(String root) {
        this.cgiRoot = root;
    }
    
    public HttpResponse execScp(String scriptPath, RequestLine request) {
        Process process = null;
        
        try {
            String fuullpath = cgiRoot + File.separator + scriptPath;
            
            ProcessBuilder pb = new ProcessBuilder("python3", fuullpath);
            pb.directory(new File(cgiRoot));
            pb.redirectErrorStream(true);// merg stdderrand stdout
            
            Map<String, String> env = pb.environment();
            env.put("PATH_INFO", fuullpath);
            
            env.put("REQUEST_METHOD", request.getMethod());
            
            process = pb.start();
            
            // if ("POST".equals(request.getMethod()) && request.getBody() != null) {
            //     try (OutputStream os = process.getOutputStream()) {
            //         os.write(request.getBody().getBytes(StandardCharsets.UTF_8));
            //         os.flush();
            //     }
            // }
            
            String output = readProcessOutput(process.getInputStream());
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ErrorHandler.handleError(500, "CGI script failed");
            }
            
            return parseCgiOutput(output);
            
        } catch (Exception err) {
            return ErrorHandler.handleError(500, "CGI error: " + err.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
    
    private String readProcessOutput(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            result.write(buffer, 0, bytesRead);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
    
    private HttpResponse parseCgiOutput(String output) {
        int headerEnd = output.indexOf("\r\n\r\n");
        if (headerEnd == -1) {
            headerEnd = output.indexOf("\n\n");
        }
        
        if (headerEnd == -1) {
            // response with output and 200
            return new HttpResponse(200, output.getBytes(StandardCharsets.UTF_8));
        }
        
        String headers = output.substring(0, headerEnd);
        String body = output.substring(headerEnd + 4);
        
        // Extract content type from headers
        String contentType = "text/html";
        for (String line : headers.split("\r\n|\n")) {
            if (line.startsWith("Content-Type:")) {
                contentType = line.substring(13).trim();
                break;
            }
        }
        
        return new HttpResponse(200, body.getBytes(StandardCharsets.UTF_8));
        // matenssach already builded hafidd httpresponse
    }
}