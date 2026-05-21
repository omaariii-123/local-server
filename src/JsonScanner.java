package src;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return c >= 'a' && c <= 'z'; // JSON only uses lowercase for true/false/null
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
            case "true":
                tokens.add(new Token(TokenType.TRUE, text));
                break;
            case "false":
                tokens.add(new Token(TokenType.FALSE, text));
                break;
            case "null":
                tokens.add(new Token(TokenType.NULL, text));
                break;
            default:
                throw new RuntimeException("Unexpected keyword: " + text);
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
            case '{':
                addToken(TokenType.LEFT_BRACE);
                break;
            case '}':
                addToken(TokenType.RIGHT_BRACE);
                break;
            case '[':
                addToken(TokenType.LEFT_BRACKET);
                break;
            case ']':
                addToken(TokenType.RIGHT_BRACKET);
                break;
            case ':':
                addToken(TokenType.COLON);
                break;
            case ',':
                addToken(TokenType.COMMA);
                break;
            case '"':
                string();
                break;
                case ' ':
            case '\r':
            case '\t':
            case '\n':
                break;
            default:
                if (isDigit(c) || c == '-'){
                    number();   
                }else if ( isAlpha(c)){
                    keyword();
                }else {
                    throw new RuntimeException("Unexpected character: " + c);
                }
                break;
        }
    }
}
