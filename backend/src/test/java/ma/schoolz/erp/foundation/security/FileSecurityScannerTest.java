package ma.schoolz.erp.foundation.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FileSecurityScannerTest {
    private static final Set<String> DOCUMENT_TYPES =
            Set.of("application/pdf", "image/png", "text/plain");
    private final FileSecurityScanner scanner =
            new FileSecurityScanner(List.of(new LocalMalwareScanAdapter()));

    @Test
    void acceptsAFileWhoseSignatureMatchesItsDeclaredType() {
        assertThatCode(
                        () ->
                                scanner.inspect(
                                        "bulletin.pdf",
                                        "application/pdf",
                                        "%PDF-1.7 SchoolZ".getBytes(StandardCharsets.UTF_8),
                                        DOCUMENT_TYPES,
                                        1024))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMimeSpoofingPathTraversalAndMalwareSignatures() {
        assertThatThrownBy(
                        () ->
                                scanner.inspect(
                                        "photo.png",
                                        "image/png",
                                        "<script>alert(1)</script>"
                                                .getBytes(StandardCharsets.UTF_8),
                                        DOCUMENT_TYPES,
                                        1024))
                .isInstanceOf(UnsafeFileException.class);
        assertThatThrownBy(
                        () ->
                                scanner.inspect(
                                        "../preuve.pdf",
                                        "application/pdf",
                                        "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                                        DOCUMENT_TYPES,
                                        1024))
                .isInstanceOf(UnsafeFileException.class);
        assertThatThrownBy(
                        () ->
                                scanner.inspect(
                                        "test.txt",
                                        "text/plain",
                                        "EICAR-STANDARD-ANTIVIRUS-TEST-FILE"
                                                .getBytes(StandardCharsets.UTF_8),
                                        DOCUMENT_TYPES,
                                        1024))
                .isInstanceOf(UnsafeFileException.class);
    }
}
