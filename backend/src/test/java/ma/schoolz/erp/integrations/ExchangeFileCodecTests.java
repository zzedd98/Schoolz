package ma.schoolz.erp.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import ma.schoolz.erp.integrations.application.ExchangeFileCodec;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExchangeFileCodecTests {
    private final ExchangeFileCodec codec = new ExchangeFileCodec();

    @Test
    void csvRoundTripKeepsUtf8HeadersAndNeutralizesFormulaInjection() {
        var encoded =
                codec.encode(
                        "CSV",
                        List.of("nom", "valeur"),
                        List.of(Map.of("nom", "Élève", "valeur", "=2+2")));

        assertThat(encoded.content()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        var parsed = codec.parse("eleves.csv", encoded.content(), List.of("nom", "valeur"));
        assertThat(parsed.rows()).containsExactly(Map.of("nom", "Élève", "valeur", "'=2+2"));
    }

    @Test
    void xlsxRoundTripPreservesTheDeclaredSchema() {
        var encoded =
                codec.encode(
                        "XLSX",
                        List.of("student_identifier", "last_name"),
                        List.of(Map.of("student_identifier", "SZ-01", "last_name", "Alaoui")));

        var parsed =
                codec.parse(
                        "eleves.xlsx",
                        encoded.content(),
                        List.of("student_identifier", "last_name"));
        assertThat(parsed.format()).isEqualTo("XLSX");
        assertThat(parsed.rows().getFirst().get("student_identifier")).isEqualTo("SZ-01");
    }

    @Test
    void missingRequiredColumnsAreRejected() {
        byte[] content =
                "nom;prenom\nAlaoui;Yasmine\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> codec.parse("eleves.csv", content, List.of("identifiant")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifiant");
    }

    @Test
    void spreadsheetFormulasAreNeverEvaluated() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("SchoolZ");
            sheet.createRow(0).createCell(0).setCellValue("student_identifier");
            sheet.createRow(1).createCell(0).setCellFormula("2+2");
            workbook.write(output);
            content = output.toByteArray();
        }

        assertThatThrownBy(() -> codec.parse("eleves.xlsx", content, List.of("student_identifier")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formules");
    }
}
