package br.com.ricarte.filenorm.parse;

/**
 * Registro do arquivo que não virou evento. {@code rejected} torna o job parcial;
 * {@code skipped} é um registro sem efeito financeiro (ex.: saldo, ocorrência sem pagamento);
 * {@code warning} acompanha um evento emitido com ressalva.
 */
public record ParseIssue(String severity, String code, String recordRef, String detail) {

    public static final String REJECTED = "rejected";
    public static final String SKIPPED = "skipped";
    public static final String WARNING = "warning";

    public static ParseIssue rejected(String code, String recordRef, String detail) {
        return new ParseIssue(REJECTED, code, recordRef, detail);
    }

    public static ParseIssue skipped(String code, String recordRef, String detail) {
        return new ParseIssue(SKIPPED, code, recordRef, detail);
    }

    public static ParseIssue warning(String code, String recordRef, String detail) {
        return new ParseIssue(WARNING, code, recordRef, detail);
    }
}
