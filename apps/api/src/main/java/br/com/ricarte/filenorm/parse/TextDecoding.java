package br.com.ricarte.filenorm.parse;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodifica arquivos bancários: remove BOM, usa UTF-8 quando o conteúdo é UTF-8 válido
 * e cai para windows-1252 (superconjunto prático do ISO-8859-1 usado pelos bancos) caso contrário.
 */
public final class TextDecoding {

    static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private TextDecoding() {
    }

    public static String decode(byte[] content) {
        int offset = hasUtf8Bom(content) ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(content, offset, content.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer)
                    .toString();
        } catch (CharacterCodingException ex) {
            return new String(content, offset, content.length - offset, WINDOWS_1252);
        }
    }

    public static String decode(byte[] content, Charset declared) {
        if (declared == null || declared.equals(StandardCharsets.UTF_8)) {
            return decode(content);
        }
        int offset = hasUtf8Bom(content) ? 3 : 0;
        return new String(content, offset, content.length - offset, declared);
    }

    private static boolean hasUtf8Bom(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF;
    }
}
