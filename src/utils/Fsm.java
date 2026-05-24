package utils;
public enum Fsm {
    REQUEST_LINE,
    READING_HEADERS,
    READING_BODY,
    PROCESSING,
    WRITING_RESPONSE,
    // KEEP_ALIVE,
    CLOSED
}