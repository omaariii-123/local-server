import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
interface JsonElement { }

 class JsonObject implements JsonElement {
    public final Map<String, JsonElement> values = new HashMap<>();
    @Override
    public String toString() {
        // This will automatically call the toString() of every element inside!
        return values.toString(); 
    }
}

 class JsonArray implements JsonElement {
    public final List<JsonElement> elements = new ArrayList<>();
    @Override
    public String toString() {
        // This will automatically call the toString() of every element inside!
        return elements.toString(); 
    }
}

class JsonString implements JsonElement {
    public final String value;
    public JsonString(String value) { this.value = value; }
    @Override
    public String toString() {
       
        return "\"" + value + "\""; // Adds quotes back for visualization
    }
}
class JsonNull implements JsonElement{
     @Override
    public String toString() {
        return "null";
    }
}
class JsonBoolean implements JsonElement{
    public final boolean value;
    public JsonBoolean(boolean bool){
        this.value = bool;
    }
    @Override
    public String toString() {
        if (this.value){
            return "true";
        }
        return "false";
    }
}

class JsonNumber implements JsonElement{
    public final int value;
    public JsonNumber(int num){
        this.value = num;
    }
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

public class JsonScanner {
    private final String input;
    private final List<Token> tokens = new ArrayList<>();
    private int current = 0;
    private int start = 0;
    public JsonScanner(String input){
        this.input = input;
    }
    private boolean isAtEnd(){
        return this.current >= this.input.length();
    }
    private char advance(){
        return this.input.charAt(this.current++);
    }
    private void addToken(TokenType type){
        String text = this.input.substring(this.start, this.current);
        tokens.add(new Token(type, text));
    }
    public List<Token> scanTokens(){
        while(!isAtEnd()){
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return c >= 'a' && c <= 'z' ; // JSON only uses lowercase for true/false/null
    }
    private void string(){
        while(peek() != '"' && !isAtEnd()){
            advance();
        }
        if (isAtEnd()) {
            throw new RuntimeException("Error: Unterminated string.");
        }
        advance();
        String value = input.substring(start + 1, current - 1);
        tokens.add(new Token(TokenType.STRING, value));
    }
    private void number(){
        while (isDigit(peek())) {
            advance();
        }
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) {
                advance();
            }
        }

        String value = input.substring(start, current);
        tokens.add(new Token(TokenType.NUMBER, value));
    }
    private void keyword() {
        while (isAlpha(peek())) {
            advance();
        }

        String text = input.substring(start, current);
            switch (text) {
                case "true" -> tokens.add(new Token(TokenType.TRUE, text));
                case "false" -> tokens.add(new Token(TokenType.FALSE, text));
                case "null" -> tokens.add(new Token(TokenType.NULL, text));
                default -> throw new RuntimeException("Unexpected keyword: " + text);
            }
    }
    private char peek(){
        if (isAtEnd()) return '\0';
        return input.charAt(current);
    }
    private char peekNext() {
        if (current + 1 >= input.length()) return '\0';
        return input.charAt(current + 1);
    }
    private void scanToken(){
        char c = this.advance();
    switch (c) {
        case '{' -> addToken(TokenType.LEFT_BRACE);
        case '}' -> addToken(TokenType.RIGHT_BRACE);
        case '[' -> addToken(TokenType.LEFT_BRACKET);
        case ']' -> addToken(TokenType.RIGHT_BRACKET);
        case ':' -> addToken(TokenType.COLON);
        case ',' -> addToken(TokenType.COMMA);
        case '"' -> string();
        case ' ', '\r', '\t', '\n' -> {}        
        default -> {
                if (isDigit(c) || c == '-') {
                    number();   
                } else if (isAlpha(c)) {
                    keyword();
                } else {
                    throw new RuntimeException("Unexpected character: " + c);
                }
            }
        }
    }
}