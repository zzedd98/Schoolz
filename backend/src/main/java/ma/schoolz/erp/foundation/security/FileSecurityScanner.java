package ma.schoolz.erp.foundation.security;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FileSecurityScanner {
    private static final Set<String> TEXT_TYPES = Set.of("text/plain", "text/csv");
    private static final Set<String> ZIP_TYPES =
            Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final List<MalwareScanAdapter> scanners;

    public FileSecurityScanner(List<MalwareScanAdapter> scanners) {
        this.scanners = List.copyOf(scanners);
    }

    public void inspect(
            String originalFileName,
            String claimedContentType,
            byte[] content,
            Set<String> allowedTypes,
            long maximumSize) {
        if (content == null || content.length == 0 || content.length > maximumSize) {
            throw new UnsafeFileException("Taille de fichier non autorisée");
        }
        String fileName = safeName(originalFileName);
        String contentType = normalizeType(claimedContentType);
        if (!allowedTypes.contains(contentType)) {
            throw new UnsafeFileException("Type de fichier non autorisé");
        }
        if (!signatureMatches(contentType, content)) {
            throw new UnsafeFileException("Le contenu du fichier ne correspond pas à son type");
        }
        for (MalwareScanAdapter scanner : scanners) {
            MalwareScanAdapter.ScanResult result = scanner.scan(fileName, contentType, content);
            if (!result.safe()) {
                throw new UnsafeFileException("Fichier refusé par le contrôle anti-malware");
            }
        }
    }

    private static String safeName(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new UnsafeFileException("Nom de fichier invalide");
        }
        if (value.contains("..")
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new UnsafeFileException("Chemin de fichier non autorisé");
        }
        return value.strip();
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) return "";
        return value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private static boolean signatureMatches(String type, byte[] content) {
        if ("application/pdf".equals(type)) return startsWith(content, "%PDF-".getBytes());
        if ("image/png".equals(type))
            return startsWith(
                    content, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        if ("image/jpeg".equals(type))
            return content.length >= 3
                    && content[0] == (byte) 0xff
                    && content[1] == (byte) 0xd8
                    && content[2] == (byte) 0xff;
        if (ZIP_TYPES.contains(type))
            return content.length >= 4
                    && content[0] == 'P'
                    && content[1] == 'K'
                    && Set.of(3, 5, 7).contains((int) content[2])
                    && Set.of(4, 6, 8).contains((int) content[3]);
        if (TEXT_TYPES.contains(type)) return validUtf8Text(content);
        return false;
    }

    private static boolean validUtf8Text(byte[] content) {
        for (byte value : content) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) return false;
        }
        return true;
    }
}
