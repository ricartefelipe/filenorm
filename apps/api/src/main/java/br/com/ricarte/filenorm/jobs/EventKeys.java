package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.parse.NormalizedEventData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Chave de idempotência por evento, estável entre reenvios do mesmo arquivo: o consumidor
 * pode deduplicar lançamentos sem depender da posição da linha. OFX/CNAB usam o identificador
 * do banco (FITID / nosso número + ocorrência); CSV/PDF usam o conteúdo da linha. Eventos
 * idênticos no mesmo arquivo recebem um sufixo de ocorrência.
 */
final class EventKeys {

    private EventKeys() {
    }

    static List<String> assign(String format, List<NormalizedEventData> events) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<String> keys = new ArrayList<>(events.size());
        for (NormalizedEventData event : events) {
            String identity = "csv".equals(format) || "pdf".equals(format)
                    ? event.rawRef().strip()
                    : event.externalId();
            String base = String.join("|",
                    format,
                    identity,
                    event.postedAt().toString(),
                    event.amount().toPlainString(),
                    event.direction(),
                    event.currency());
            int occurrence = occurrences.merge(base, 1, Integer::sum);
            keys.add(sha256(base + "#" + occurrence));
        }
        return keys;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
