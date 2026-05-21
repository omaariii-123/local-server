package src;

public class Token {
    public final TokenType type;
    public final String lexeme;
    public Token(TokenType type, String lexeme){
        this.type = type;
        this.lexeme = lexeme;
    }
    @Override
    public String toString() {
        return type + " '" + lexeme + "'";
    }
}
enum TokenType {
    LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET,
    COLON, COMMA,
    STRING, NUMBER,
    TRUE, FALSE, NULL,
    EOF
}