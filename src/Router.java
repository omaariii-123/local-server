package src;

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class Router {
    private ServerConfig config;
    private UplHandler uploadHandler;
    private CGIHandler cgiHandler;
    private Errorhandler errorHandler;
    
    public Router(ServerConfig config) {
        this.config = config;
        
        // Initialize upload handler with config values
        // this.uploadHandler = new UplHandler(
            // config.getUDIR(),
            // config.getULIMIT()
        // );
        
        this.errorHandler = new Errorhandler(config.getErrorPages());
    }
    
    public HttpResponse route(RequestLine request) {
        String path = request.getPath();
        String method = request.getMethod();
        
        
        Route route = config.findRoute(path);
        if (route == null) {
            return errorHandler.handleError(404, "Route not found: " + path);
        }
        
        if (!route.allowsMethod(method)) {
            return errorHandler.handleError(405, "Method " + method + " not allowed");
        }
        
        if (route.hasRedirect()) {
            return handleRedirect(route);
        }
        
        // Handle upload
        if (route.isuploadRoute() && "POST".equals(method)) {
            return handleUpload(request, route);
        }
        
        // Handle CGI
        if (route.iscgiRoute()) {
            return handleCGI(request, route);
        }
        
        // Handle static files
        if (route.isStaticRoute()) {
            return handleStaticFile(request, route);
        }
        
        // Handle directory listing
        if (route.isDirectoryListing()) {
            return handleDirectoryListing(request, route);
        }
        
        return errorHandler.handleError(404, "No handler for this route");
    }
    

    private HttpResponse handleRedirect(RouteConfig route) {
        System.out.println("   🔀 Redirecting to: " + route.getRedirect());
        return new HttpResponse.Builder()
                .statusCode(301)
                .statusMessage("Moved Permanently")
                .header("Location", route.getRedirect())
                .body("<h1>301 Moved Permanently</h1><p>Redirecting to: " + 
                      route.getRedirect() + "</p>")
                .build();
    }
    
    /**
     * Handle file upload
     */
    private HttpResponse handleUpload(HttpRequest request, RouteConfig route) {
        try {
            System.out.println("   📤 Processing upload...");
            
            // Check body size
            String contentLength = request.getHeader("Content-Length");
            if (contentLength != null) {
                long size = Long.parseLong(contentLength);
                if (size > config.getClientBodyLimit()) {
                    return errorHandler.handleError(413, "Request body too large");
                }
                
                if (size > route.getMaxFileSize()) {
                    return errorHandler.handleError(413, "File exceeds maximum size");
                }
            }
            
            // Process upload
            UploadResult result = uploadHandler.processUpload(request);
            
            if (!result.isSuccess()) {
                return errorHandler.handleError(500, "Upload failed: " + result.getMessage());
            }
            
            // Build success response
            StringBuilder html = new StringBuilder();
            html.append("<html><body>");
            html.append("<h1>Upload Successful</h1>");
            html.append("<p>").append(result.getMessage()).append("</p>");
            html.append("<h2>Uploaded Files:</h2>");
            html.append("<ul>");
            
            for (UploadedFile file : result.getFiles()) {
                html.append("<li>");
                html.append("<strong>").append(file.getOriginalFilename()).append("</strong>");
                html.append(" - ").append(file.getFileSize()).append(" bytes");
                html.append(" - Saved as: ").append(file.getUniqueFilename());
                html.append("</li>");
            }
            
            html.append("</ul>");
            html.append("<p><a href='/'>Go Home</a></p>");
            html.append("</body></html>");
            
            return new HttpResponse.Builder()
                    .statusCode(200)
                    .statusMessage("OK")
                    .body(html.toString())
                    .contentType("text/html")
                    .build();
            
        } catch (IOException e) {
            return errorHandler.handleError(500, "Upload error: " + e.getMessage());
        } catch (Exception e) {
            return errorHandler.handleError(500, "Error processing upload: " + e.getMessage());
        }
    }
    
    /**
     * Handle CGI script execution
     */
    private HttpResponse handleCGI(HttpRequest request, RouteConfig route) {
        try {
            String path = request.getPath();
            
            // Extract script path
            String scriptPath = path.substring(route.getPath().length());
            if (scriptPath.startsWith("/")) {
                scriptPath = scriptPath.substring(1);
            }
            
            // Create CGI handler with route-specific root
            CGIHandler handler = new CGIHandler(route.getRoot());
            
            // Execute script
            return handler.executeScript(scriptPath, request);
            
        } catch (Exception e) {
            return errorHandler.handleError(500, "CGI error: " + e.getMessage());
        }
    }
    
    /**
     * Handle static file serving
     */
    private HttpResponse handleStaticFile(HttpRequest request, RouteConfig route) {
        try {
            String path = request.getPath();
            
            // Remove route prefix
            String relativePath = path.substring(route.getPath().length());
            if (relativePath.isEmpty() || relativePath.equals("/")) {
                // Requesting the root of this route
                if (route.getDefaultFile() != null) {
                    // Try default file
                    File defaultFile = new File(route.getRoot(), route.getDefaultFile());
                    if (defaultFile.exists() && defaultFile.isFile()) {
                        return serveFile(defaultFile);
                    }
                }
                
                // If no default file or directory listing is enabled
                if (route.isDirectoryListing()) {
                    return generateDirectoryListing(new File(route.getRoot()));
                }
                
                return errorHandler.handleError(404, "No default file found");
            }
            
            // Build file path
            File file = new File(route.getRoot(), relativePath);
            
            // Security: Prevent directory traversal
            if (!file.getCanonicalPath().startsWith(new File(route.getRoot()).getCanonicalPath())) {
                return errorHandler.handleError(403, "Access denied");
            }
            
            // Check if file exists
            if (!file.exists()) {
                return errorHandler.handleError(404, "File not found: " + relativePath);
            }
            
            // If it's a directory
            if (file.isDirectory()) {
                if (route.getDefaultFile() != null) {
                    File defaultFile = new File(file, route.getDefaultFile());
                    if (defaultFile.exists() && defaultFile.isFile()) {
                        return serveFile(defaultFile);
                    }
                }
                
                if (route.isDirectoryListing()) {
                    return generateDirectoryListing(file);
                }
                
                return errorHandler.handleError(403, "Directory listing not allowed");
            }
            
            // Serve file
            return serveFile(file);
            
        } catch (IOException e) {
            return errorHandler.handleError(500, "Error serving file: " + e.getMessage());
        }
    }
    
    /**
     * Serve a static file
     */
    private HttpResponse serveFile(File file) throws IOException {
        byte[] content = Files.readAllBytes(file.toPath());
        String contentType = getContentType(file.getName());
        
        return new HttpResponse.Builder()
                .statusCode(200)
                .statusMessage("OK")
                .body(new String(content))
                .contentType(contentType)
                .header("Content-Length", String.valueOf(content.length))
                .build();
    }
    
    /**
     * Generate directory listing HTML
     */
    private HttpResponse generateDirectoryListing(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return errorHandler.handleError(500, "Could not list directory");
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<html><head>");
        html.append("<title>Directory: ").append(directory.getName()).append("</title>");
        html.append("<style>");
        html.append("body { font-family: Arial; margin: 40px; }");
        html.append(".file { padding: 5px; }");
        html.append(".dir { color: blue; font-weight: bold; }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<h1>Directory: ").append(directory.getName()).append("</h1>");
        html.append("<hr>");
        html.append("<ul>");
        
        // Add parent directory link
        html.append("<li><a href='..' class='dir'>📁 ..</a></li>");
        
        // Sort files
        Arrays.sort(files);
        
        for (File file : files) {
            String name = file.getName();
            String path = file.getName();
            
            if (file.isDirectory()) {
                html.append("<li><a href='").append(path).append("/' class='dir'>");
                html.append("📁 ").append(name);
                html.append("</a></li>");
            } else {
                html.append("<li><a href='").append(path).append("' class='file'>");
                html.append("📄 ").append(name);
                html.append("</a> (").append(file.length()).append(" bytes)");
                html.append("</li>");
            }
        }
        
        html.append("</ul>");
        html.append("</body></html>");
        
        return new HttpResponse.Builder()
                .statusCode(200)
                .statusMessage("OK")
                .body(html.toString())
                .contentType("text/html")
                .build();
    }
    
    /**
     * Get content type based on file extension
     */
    private String getContentType(String filename) {
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        
        switch (ext) {
            case "html": return "text/html";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "json": return "application/json";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "txt": return "text/plain";
            case "pdf": return "application/pdf";
            default: return "application/octet-stream";
        }
    }
}