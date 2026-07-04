package src;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import src.*;
import java.time.format.DateTimeFormatter;


public class UplHandler {




    private String uploadDirectory;
    private long maxFileSize;
    
    public UplHandler(String updir, long maxSize) {
        this.uploadDirectory = updir;
        this.maxFileSize = maxSize;
        
        try {
            Files.createDirectories(Paths.get(updir));
        } catch (IOException e) {
            System.err.println("Failed to create upload directory: " + e.getMessage());
        }
    }
    

    public UploadResult processUpload(RequestLine request) throws IOException {
        
        // String contentType = request.getHeader("Content-Type");
        // if (contentType == null || !contentType.startsWith("multipart/form-data")) {
        //     throw new IOException("Invalid content type. Expected multipart/form-data");
        // }
        
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            throw new IOException("Could not extract boundary from Content-Type");
        }
        
        // String body = request.getBody();
        // if (body == null || body.isEmpty()) {
        //     throw new IOException("No upload data received");
        // }
        
        List<FileUpl> files = parseMultipart(body, boundary);
        
        List<FileUpl> savedFiles = new ArrayList<>();
        for (FileUpl file : files) {
            FileUpl saved = saveFile(file);
            savedFiles.add(saved);
        }
        
        return new UploadResult(true, savedFiles, "Files uploaded successfully");
    }
    

    private String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring(9);
            }
        }
        return null;
    }
    

    private List<FileUpl> parseMultipart(String body, String boundary) {
        List<FileUpl> files = new ArrayList<>();
        
        String fullBoundary = "--" + boundary;
        String endBoundary = fullBoundary + "--";
        
        String[] parts = body.split(fullBoundary);
        
        for (String part : parts) {
            if (part.trim().isEmpty() || part.contains("--")) {
                continue;
            }
            
            FileUpl file = parseMultipartPart(part);
            if (file != null) {
                files.add(file);
            }
        }
        
        return files;
    }
    

    private FileUpl parseMultipartPart(String part) {
        int headerEnd = part.indexOf("\r\n\r\n");
        if (headerEnd == -1) {
            headerEnd = part.indexOf("\n\n");
        }
        
        if (headerEnd == -1) {
            return null;
        }
        
        String headers = part.substring(0, headerEnd);
        String data = part.substring(headerEnd + 4);
        

        if (data.endsWith("\r\n")) {
            data = data.substring(0, data.length() - 2);
        } else if (data.endsWith("\n")) {
            data = data.substring(0, data.length() - 1);
        }
        
        String filename = extractFilename(headers);
        String fieldName = extractFieldName(headers);
        
        if (filename == null) {
            //errorhandler
        }
        
        if (data.length() > maxFileSize) {
            throw new RuntimeException("max file ");
        }
        
        String uniqueFilename = generateUniqueFilename(filename);
        
        return new FileUpl(filename, uniqueFilename, data.getBytes(), 
                               headers, fieldName);
    }
    

    private String extractFilename(String headers) {
        // Look for filename="..." in Content-Disposition
        String pattern = "filename=\"";
        int start = headers.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        
        start += pattern.length();
        int end = headers.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        
        return headers.substring(start, end);
    }
    

    private String extractFieldName(String headers) {
        String pattern = "name=\"";
        int start = headers.indexOf(pattern);
        if (start == -1) {
            return "unknown";
        }
        
        start += pattern.length();
        int end = headers.indexOf("\"", start);
        if (end == -1) {
            return "unknown";
        }
        
        return headers.substring(start, end);
    }
    

    private String generateUniqueFilename(String originalFilename) {
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        );
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = "";
        
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex != -1) {
            extension = originalFilename.substring(dotIndex);
        }
        
        return timestamp + "_" + uuid + extension;
    }
    
//save the file 
    private FileUpl saveFile(FileUpl file) throws IOException {
        Path filePath = Paths.get(uploadDirectory, file.getUniqueFilename());
        
        Files.write(filePath, file.getData());
        
        file.setSavedPath(filePath.toString());
        file.setFileSize(file.getData().length);
        file.setSaved(true);
        
        return file;
    }
}