package br.com.ricarte.filenorm.parse;

import java.util.List;

public record ParseResult(List<NormalizedEventData> events, Integer pages) {

    public ParseResult(List<NormalizedEventData> events) {
        this(events, null);
    }
}
