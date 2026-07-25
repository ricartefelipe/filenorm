package br.com.ricarte.filenorm.parse;

import org.springframework.stereotype.Component;

@Component
public class ParserRouter {

    private final OfxParser ofxParser;
    private final CsvParser csvParser;
    private final Cnab240Parser cnab240Parser;
    private final PdfTextParser pdfTextParser;

    public ParserRouter(
            OfxParser ofxParser,
            CsvParser csvParser,
            Cnab240Parser cnab240Parser,
            PdfTextParser pdfTextParser
    ) {
        this.ofxParser = ofxParser;
        this.csvParser = csvParser;
        this.cnab240Parser = cnab240Parser;
        this.pdfTextParser = pdfTextParser;
    }

    public ParseResult parse(byte[] content, String format, String preset, String filename, String contentType) {
        String resolved = "auto".equalsIgnoreCase(format)
                ? FormatDetector.detect(content, filename, contentType)
                : format.toLowerCase();
        return switch (resolved) {
            case "ofx" -> ofxParser.parse(content);
            case "csv" -> csvParser.parse(content, preset);
            case "cnab240" -> cnab240Parser.parse(content);
            case "pdf" -> pdfTextParser.parse(content);
            default -> throw new ParseException("unsupported_format");
        };
    }
}
