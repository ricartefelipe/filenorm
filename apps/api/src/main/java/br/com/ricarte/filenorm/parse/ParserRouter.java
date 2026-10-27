package br.com.ricarte.filenorm.parse;

import br.com.ricarte.filenorm.config.FilenormProperties;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ParserRouter {

    private final OfxParser ofxParser;
    private final CsvParser csvParser;
    private final Cnab240Parser cnab240Parser;
    private final PdfTextParser pdfTextParser;
    private final int maxRecords;

    public ParserRouter(
            OfxParser ofxParser,
            CsvParser csvParser,
            Cnab240Parser cnab240Parser,
            PdfTextParser pdfTextParser,
            FilenormProperties properties
    ) {
        this.ofxParser = ofxParser;
        this.csvParser = csvParser;
        this.cnab240Parser = cnab240Parser;
        this.pdfTextParser = pdfTextParser;
        this.maxRecords = properties.jobs().maxRecords();
    }

    public ParseResult parse(byte[] content, String format, String preset, String filename, String contentType) {
        String resolved = "auto".equalsIgnoreCase(format)
                ? FormatDetector.detect(content, filename, contentType)
                : format.toLowerCase(Locale.ROOT);
        ParseResult result = switch (resolved) {
            case "ofx" -> ofxParser.parse(content);
            case "csv" -> csvParser.parse(content, preset);
            case "cnab240" -> cnab240Parser.parse(content);
            case "pdf" -> pdfTextParser.parse(content);
            default -> throw new ParseException("unsupported_format");
        };
        if (result.events().size() + result.issues().size() > maxRecords) {
            throw new ParseException("too_many_records");
        }
        return result;
    }
}
