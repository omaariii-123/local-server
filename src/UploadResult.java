package src;

import java.util.*;

public class UploadResult {
    private boolean success;
    private List<FileUpl> files;
    private String message;
    
    public UploadResult(boolean success, List<FileUpl> files, String message) {
        this.success = success;
        this.files = files;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public List<FileUpl> getFiles() { return files; }
    public String getMessage() { return message; }
}