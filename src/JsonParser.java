package src;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class JsonParser {
    private final List<Token> tokens;
    private int current = 0;
    public JsonParser(List<Token> tokens){
        this.tokens = tokens;
    }

    private boolean isAtEnd(){
        return peek().type == TokenType.EOF;
    }

    private Token advance(){
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean check(TokenType type){
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token previous(){
        return tokens.get(current -1);
    }

    private Token peek(){
        return tokens.get(current);
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw new RuntimeException(message + " Found: " + peek().type);
    }

    public Map<String, Object> parse() {
        consume(TokenType.LEFT_BRACE, "Expected '{' at the beginning of the file.");
        return parseObject();
    }

    public Map<String, Object> parseObject() {
        Map<String, Object> map = new HashMap<>();

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            Token tokenKey = consume(TokenType.STRING, "Expected a string key!");
            consume(TokenType.COLON, "Expected a colon!");
            Object tokenValue = parseValue();
            map.put(tokenKey.lexeme, tokenValue);
            if (check(TokenType.COMMA)){
                advance();
            }
            else {
                break;
            }
        }
        consume(TokenType.RIGHT_BRACE, "Expected '}' at the end of the object.");
        return map;
    }
    
    private Object parseValue() {
        Token token = peek();

        switch (token.type) {
            case STRING:
            case NUMBER:
            case TRUE:
            case FALSE:
                return advance().lexeme;
            
            case LEFT_BRACE:
                return parseObject();
                
            case LEFT_BRACKET:
                return new Object();//parseArray(); 
                
            default:
                throw new RuntimeException("Unexpected value: " + token.type);
        }
    }
}
