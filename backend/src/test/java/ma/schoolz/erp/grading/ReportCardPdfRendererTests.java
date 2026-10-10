package ma.schoolz.erp.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.CompetencyLine;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.DocumentData;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.GradeLine;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.SubjectLine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportCardPdfRendererTests {
    private final ReportCardPdfRenderer renderer = new ReportCardPdfRenderer();

    @ParameterizedTest
    @ValueSource(strings = {"FR", "AR", "BILINGUAL"})
    void rendersValidPdfInEverySupportedLanguage(String language) throws Exception {
        UUID reportId = UUID.randomUUID();
        DocumentData data =
                new DocumentData(
                        reportId,
                        3,
                        language,
                        "École Horizon",
                        "École privée Horizon SARL",
                        null,
                        "12 rue des Écoles, Rabat",
                        "Salma El Idrissi",
                        "SZ-2026-0042",
                        "CE2 A",
                        "Cours élémentaire 2",
                        "2026-2027",
                        "Premier trimestre",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 12, 20),
                        new BigDecimal("16.25"),
                        2,
                        24,
                        List.of(
                                new SubjectLine(
                                        "MATH",
                                        "Mathématiques",
                                        "الرياضيات",
                                        new BigDecimal("17.5"),
                                        new BigDecimal("3"),
                                        List.of(
                                                new GradeLine(
                                                        "Contrôle 1",
                                                        LocalDate.of(2026, 10, 3),
                                                        new BigDecimal("17"),
                                                        new BigDecimal("20"),
                                                        "GRADED"),
                                                new GradeLine(
                                                        "Examen",
                                                        LocalDate.of(2026, 12, 10),
                                                        new BigDecimal("18"),
                                                        new BigDecimal("20"),
                                                        "GRADED")),
                                        "Très bon raisonnement. واصل العمل الجيد.")),
                        List.of(
                                new CompetencyLine(
                                        "Résoudre un problème",
                                        "حل مسألة",
                                        "MASTERED",
                                        "Démarche autonome")),
                        2,
                        1,
                        3,
                        "Excellent trimestre. ممتاز، واصل جهودك.",
                        "Nadia El Mansouri",
                        "Directrice",
                        "Modèle institutionnel",
                        "Document de contrôle",
                        Map.of("accentColor", "#1C7155", "showRank", true),
                        Instant.parse("2026-12-21T10:15:30Z"));

        byte[] pdf = renderer.render(data, false);

        assertThat(pdf).startsWith('%', 'P', 'D', 'F');
        assertThat(pdf.length).isGreaterThan(20_000);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getDocumentInformation().getTitle())
                    .contains("Salma El Idrissi")
                    .contains("v3");
            String extracted = new PDFTextStripper().getText(document);
            assertThat(extracted).contains("SchoolZ").contains(reportId.toString());
        }
    }
}
