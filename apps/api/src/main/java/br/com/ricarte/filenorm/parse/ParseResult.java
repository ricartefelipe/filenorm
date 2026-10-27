package br.com.ricarte.filenorm.parse;

import java.util.List;

public record ParseResult(List<NormalizedEventData> events, Integer pages, List<ParseIssue> issues, StatementBalances balances) {

    public ParseResult(List<NormalizedEventData> events) {
        this(events, null, List.of(), null);
    }

    public ParseResult(List<NormalizedEventData> events, List<ParseIssue> issues) {
        this(events, null, issues, null);
    }

    public ParseResult(List<NormalizedEventData> events, List<ParseIssue> issues, StatementBalances balances) {
        this(events, null, issues, balances);
    }

    public long count(String severity) {
        return issues.stream().filter(issue -> issue.severity().equals(severity)).count();
    }
}
