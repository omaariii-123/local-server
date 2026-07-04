package src;

public class FileUpl {
    private String originalFilename;
    private String uniqueFilename;
    private byte[] data;
    private String headers;
    private String fieldName;
    private String savedPath;
    private long fileSize;
    private boolean saved;
    
    public FileUpl(String originalFilename, String uniqueFilename, 
                       byte[] data, String headers, String fieldName) {
        this.originalFilename = originalFilename;
        this.uniqueFilename = uniqueFilename;
        this.data = data;
        this.headers = headers;
        this.fieldName = fieldName;
        this.fileSize = data.length;
    }
    
    public String getOriginalFilename() { return originalFilename; }
    public String getUniqueFilename() { return uniqueFilename; }
    public byte[] getData() { return data; }
    public String getHeaders() { return headers; }
    public String getFieldName() { return fieldName; }
    public String getSavedPath() { return savedPath; }
    public void setSavedPath(String savedPath) { this.savedPath = savedPath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public boolean isSaved() { return saved; }
    public void setSaved(boolean saved) { this.saved = saved; }
}
