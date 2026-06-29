package src;
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

    public JsonElement parse() {
        consume(TokenType.LEFT_BRACE, "Expected '{' at the beginning of the file.");
        return parseObject();
    }

    public JsonObject parseObject() {
        JsonObject obj = new JsonObject();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            Token tokenKey = consume(TokenType.STRING, "Expected a string key!");
            consume(TokenType.COLON, "Expected a colon!");
            JsonElement tokenValue = parseValue();
            obj.values.put(tokenKey.lexeme, tokenValue);       
            if (check(TokenType.COMMA)){
                advance();
            }
            else {
                break;
            }
            
        }
        
        consume(TokenType.RIGHT_BRACE, "Expected '}' at the end of the object.");
        return obj;
    }

    private  JsonArray parseArray(){
        JsonArray arr = new JsonArray();
        while (!check(TokenType.RIGHT_BRACKET) && !isAtEnd()){
            JsonElement tokenValue =  parseValue();
            arr.elements.add(tokenValue);
             if (check(TokenType.COMMA)){
                advance();
            }
            else {
                break;
            }
        }
        consume(TokenType.RIGHT_BRACKET, "Expected ']' at the end of the Array.");
        return arr;
    }
    
    private JsonElement parseValue() {
        Token token = peek();

        switch (token.type) {
            case NUMBER:
                return  new JsonNumber(Integer.parseInt(advance().lexeme));
            case TRUE:
                advance();
                return  new JsonBoolean(true);
            case FALSE:
                advance();
                return  new JsonBoolean(false);
            case NULL:
                advance();
                return new JsonNull();
            case STRING:
                return new JsonString(advance().lexeme);
            
            case LEFT_BRACE:
                advance();
                return parseObject();
                
            case LEFT_BRACKET:
                advance();
                return parseArray(); 
            default:
                throw new RuntimeException("Unexpected value: " + token.type);
        }
    }
}
