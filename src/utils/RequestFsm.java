package src.utils;

public enum RequestFsm {
    READ_METHOD,
    READ_URI,
    READ_VERSION,
    READ_HEADER_NAME,
    READ_HEADER_VALUE,
    CHECK_HEADER_END,
    READING_BODY,
    REQUEST_COMPLETE,
    KEEP_ALIVE,
    // CLOSED   
}
