package br.com.ricarte.filenorm.parse;

public class ParseException extends RuntimeException {

    private final String code;

    public ParseException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
