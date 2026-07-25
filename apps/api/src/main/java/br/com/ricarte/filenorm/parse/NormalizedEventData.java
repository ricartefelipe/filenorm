package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NormalizedEventData(
        String externalId,
        LocalDate postedAt,
        BigDecimal amount,
        String currency,
        String direction,
        String counterparty,
        String description,
        String rawRef,
        BigDecimal confidence
) {
}
