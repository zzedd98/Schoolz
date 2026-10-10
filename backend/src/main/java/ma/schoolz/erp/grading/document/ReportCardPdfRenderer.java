package ma.schoolz.erp.grading.document;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
public class ReportCardPdfRenderer {
    private static final Color INK = new Color(25, 42, 38);
    private static final Color MUTED = new Color(91, 108, 101);
    private static final Color ACCENT = new Color(28, 113, 85);
    private static final Color ACCENT_SOFT = new Color(230, 243, 237);
    private static final Color LINE = new Color(215, 224, 219);
    private static final Color PAPER_TINT = new Color(247, 249, 247);
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float MARGIN = 42;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(DocumentData data, boolean preview) {
        Objects.requireNonNull(data, "data");
        try (PDDocument document = new PDDocument();
                FontSet fonts = FontSet.load(document)) {
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Bulletin " + data.studentName() + " - v" + data.versionNumber());
            info.setAuthor(data.schoolName());
            info.setSubject("Bulletin scolaire SchoolZ");
            info.setCreator("SchoolZ ERP");

            PageWriter writer = new PageWriter(document, fonts, data, preview);
            writer.header();
            writer.identity();
            writer.subjects();
            writer.competencies();
            writer.schoolLife();
            writer.appreciationAndSignature();
            writer.finish();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Le bulletin PDF n'a pas pu être généré", exception);
        }
    }

    public record DocumentData(
            UUID reportCardId,
            int versionNumber,
            String languageMode,
            String schoolName,
            String legalName,
            String logoReference,
            String schoolAddress,
            String studentName,
            String studentIdentifier,
            String classroomName,
            String gradeLevelName,
            String academicYearName,
            String termName,
            LocalDate termStartsOn,
            LocalDate termEndsOn,
            BigDecimal overallAverage,
            Integer classRank,
            Integer classSize,
            List<SubjectLine> subjects,
            List<CompetencyLine> competencies,
            int absenceCount,
            int excusedAbsenceCount,
            int lateCount,
            String generalAppreciation,
            String signerName,
            String signerTitle,
            String templateName,
            String publicationNote,
            Map<String, Object> layout,
            Instant generatedAt) {
        public DocumentData {
            subjects = subjects == null ? List.of() : List.copyOf(subjects);
            competencies = competencies == null ? List.of() : List.copyOf(competencies);
            layout = layout == null ? Map.of() : Map.copyOf(layout);
        }
    }

    public record SubjectLine(
            String code,
            String nameFr,
            String nameAr,
            BigDecimal average,
            BigDecimal coefficient,
            List<GradeLine> grades,
            String teacherComment) {
        public SubjectLine {
            grades = grades == null ? List.of() : List.copyOf(grades);
        }
    }

    public record GradeLine(
            String title,
            LocalDate assessmentDate,
            BigDecimal score,
            BigDecimal maximumScore,
            String status) {}

    public record CompetencyLine(
            String nameFr, String nameAr, String levelCode, String observation) {}

    private static final class FontSet implements AutoCloseable {
        private final InputStream latinRegularInput;
        private final InputStream latinBoldInput;
        private final InputStream arabicRegularInput;
        private final InputStream arabicBoldInput;
        private final PDFont latinRegular;
        private final PDFont latinBold;
        private final PDFont arabicRegular;
        private final PDFont arabicBold;

        private FontSet(
                InputStream latinRegularInput,
                InputStream latinBoldInput,
                InputStream arabicRegularInput,
                InputStream arabicBoldInput,
                PDFont latinRegular,
                PDFont latinBold,
                PDFont arabicRegular,
                PDFont arabicBold) {
            this.latinRegularInput = latinRegularInput;
            this.latinBoldInput = latinBoldInput;
            this.arabicRegularInput = arabicRegularInput;
            this.arabicBoldInput = arabicBoldInput;
            this.latinRegular = latinRegular;
            this.latinBold = latinBold;
            this.arabicRegular = arabicRegular;
            this.arabicBold = arabicBold;
        }

        private static FontSet load(PDDocument document) throws IOException {
            InputStream latinRegular =
                    openFont(
                            List.of(
                                    "C:/Windows/Fonts/arial.ttf",
                                    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
                                    "/usr/share/fonts/noto/NotoSans-Regular.ttf",
                                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
            InputStream latinBold =
                    openFont(
                            List.of(
                                    "C:/Windows/Fonts/arialbd.ttf",
                                    "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
                                    "/usr/share/fonts/noto/NotoSans-Bold.ttf",
                                    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));
            InputStream arabicRegular =
                    openFont(
                            List.of(
                                    "C:/Windows/Fonts/arial.ttf",
                                    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
                                    "/usr/share/fonts/noto/NotoSansArabic-Regular.ttf",
                                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
            InputStream arabicBold =
                    openFont(
                            List.of(
                                    "C:/Windows/Fonts/arialbd.ttf",
                                    "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
                                    "/usr/share/fonts/noto/NotoSansArabic-Bold.ttf",
                                    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));
            return new FontSet(
                    latinRegular,
                    latinBold,
                    arabicRegular,
                    arabicBold,
                    PDType0Font.load(document, latinRegular, true),
                    PDType0Font.load(document, latinBold, true),
                    PDType0Font.load(document, arabicRegular, true),
                    PDType0Font.load(document, arabicBold, true));
        }

        private static InputStream openFont(List<String> candidates) throws IOException {
            for (String candidate : candidates) {
                Path path = Path.of(candidate);
                if (Files.isRegularFile(path)) return Files.newInputStream(path);
            }
            throw new IOException("Aucune police Unicode SchoolZ n'est disponible");
        }

        @Override
        public void close() throws IOException {
            latinRegularInput.close();
            latinBoldInput.close();
            arabicRegularInput.close();
            arabicBoldInput.close();
        }
    }

    private static final class PageWriter {
        private final PDDocument document;
        private final FontSet fonts;
        private final DocumentData data;
        private final boolean preview;
        private final String mode;
        private final Color accent;
        private PDPage page;
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        private PageWriter(PDDocument document, FontSet fonts, DocumentData data, boolean preview)
                throws IOException {
            this.document = document;
            this.fonts = fonts;
            this.data = data;
            this.preview = preview;
            this.mode = normalizeMode(data.languageMode());
            this.accent = parseColor(data.layout().get("accentColor"));
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) {
                footer();
                content.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            pageNumber++;
            y = PDRectangle.A4.getHeight() - MARGIN;
            if (preview) watermark();
        }

        private void watermark() throws IOException {
            content.saveGraphicsState();
            content.setNonStrokingColor(new Color(233, 238, 235));
            content.beginText();
            content.setFont(fonts.latinBold, 48);
            content.setTextMatrix(
                    org.apache.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(34), 120, 310));
            content.showText("APERÇU - PREVIEW");
            content.endText();
            content.restoreGraphicsState();
        }

        private void header() throws IOException {
            content.setNonStrokingColor(accent);
            content.addRect(MARGIN, y - 56, 56, 56);
            content.fill();
            textAt("SZ", MARGIN + 11, y - 37, fonts.latinBold, 24, Color.WHITE);
            textAt(
                    fit(data.schoolName(), 270, fonts.latinBold, 17),
                    MARGIN + 70,
                    y - 20,
                    fonts.latinBold,
                    17,
                    INK);
            String legal = blank(data.legalName()) ? data.schoolAddress() : data.legalName();
            textAt(
                    fit(orEmpty(legal), 310, fonts.latinRegular, 8.5f),
                    MARGIN + 70,
                    y - 38,
                    fonts.latinRegular,
                    8.5f,
                    MUTED);
            if ("BILINGUAL".equals(mode)) {
                String frenchTitle = "BULLETIN SCOLAIRE";
                textAt(
                        frenchTitle,
                        PAGE_WIDTH - MARGIN - width(frenchTitle, fonts.latinBold, 10),
                        y - 13,
                        fonts.latinBold,
                        10,
                        accent);
                rtlAt(
                        arabicVisual("البيان الدراسي"),
                        PAGE_WIDTH - MARGIN,
                        y - 29,
                        fonts.arabicBold,
                        11,
                        accent);
            } else if ("AR".equals(mode)) {
                String documentTitle = "البيان الدراسي";
                String visualTitle = arabicVisual(documentTitle);
                rtlAt(visualTitle, PAGE_WIDTH - MARGIN, y - 19, fonts.arabicBold, 14, accent);
            } else {
                String documentTitle = "BULLETIN SCOLAIRE";
                textAt(
                        documentTitle,
                        PAGE_WIDTH - MARGIN - width(documentTitle, fonts.latinBold, 11),
                        y - 18,
                        fonts.latinBold,
                        11,
                        accent);
            }
            textAt(
                    "v" + data.versionNumber() + " · " + data.termName(),
                    PAGE_WIDTH - MARGIN - 128,
                    y - ("BILINGUAL".equals(mode) ? 47 : 39),
                    fonts.latinRegular,
                    8.5f,
                    MUTED);
            y -= 76;
            line(y, accent, 1.4f);
            y -= 17;
        }

        private void identity() throws IOException {
            ensure(100);
            labelValue(bilingual("ÉLÈVE", "التلميذ"), data.studentName(), MARGIN, y, 244);
            labelValue(
                    bilingual("IDENTIFIANT", "المعرف"),
                    data.studentIdentifier(),
                    MARGIN + 254,
                    y,
                    118);
            labelValue(bilingual("CLASSE", "القسم"), data.classroomName(), MARGIN + 382, y, 129);
            y -= 48;
            String period = data.academicYearName() + " · " + data.termName();
            String dates = formatDate(data.termStartsOn()) + " - " + formatDate(data.termEndsOn());
            labelValue(bilingual("PÉRIODE", "الفترة"), period, MARGIN, y, 244);
            labelValue(bilingual("NIVEAU", "المستوى"), data.gradeLevelName(), MARGIN + 254, y, 118);
            labelValue(bilingual("DATES", "التواريخ"), dates, MARGIN + 382, y, 129);
            y -= 60;
        }

        private void subjects() throws IOException {
            sectionTitle(bilingual("RÉSULTATS PAR MATIÈRE", "النتائج حسب المادة"));
            tableHeader();
            if (data.subjects().isEmpty()) {
                text(
                        bilingual("Aucun résultat disponible", "لا توجد نتائج متاحة"),
                        10,
                        MUTED,
                        false);
                y -= 18;
                return;
            }
            for (SubjectLine subject : data.subjects()) {
                ensure(68);
                float top = y;
                content.setNonStrokingColor(PAPER_TINT);
                content.addRect(MARGIN, y - 54, CONTENT_WIDTH, 54);
                content.fill();
                String subjectName = localized(subject.nameFr(), subject.nameAr());
                localizedAt(subjectName, MARGIN + 8, PAGE_WIDTH - MARGIN - 345, y - 17, true, 9.5f);
                String gradeSummary = gradeSummary(subject.grades());
                textAt(
                        fit(gradeSummary, 157, fonts.latinRegular, 8),
                        MARGIN + 178,
                        y - 17,
                        fonts.latinRegular,
                        8,
                        INK);
                textAt(
                        formatNumber(subject.average()),
                        MARGIN + 355,
                        y - 17,
                        fonts.latinBold,
                        10,
                        INK);
                textAt(
                        formatNumber(subject.coefficient()),
                        MARGIN + 415,
                        y - 17,
                        fonts.latinRegular,
                        9,
                        INK);
                String comment =
                        blank(subject.teacherComment())
                                ? bilingual("Sans commentaire", "بدون ملاحظة")
                                : subject.teacherComment();
                localizedAt(
                        fitLocalized(comment, 82, 7.5f),
                        MARGIN + 453,
                        PAGE_WIDTH - MARGIN - 8,
                        y - 17,
                        false,
                        7.5f);
                content.setStrokingColor(LINE);
                content.moveTo(MARGIN, top - 54);
                content.lineTo(PAGE_WIDTH - MARGIN, top - 54);
                content.stroke();
                y -= 54;
            }
            y -= 12;
            ensure(62);
            content.setNonStrokingColor(ACCENT_SOFT);
            content.addRect(MARGIN, y - 44, CONTENT_WIDTH, 44);
            content.fill();
            localizedAt(
                    fitLocalized(bilingual("MOYENNE GÉNÉRALE", "المعدل العام"), 230, 10),
                    MARGIN + 12,
                    MARGIN + 255,
                    y - 18,
                    true,
                    10);
            String average = formatNumber(data.overallAverage()) + " / 20";
            textAt(average, MARGIN + 310, y - 21, fonts.latinBold, 17, accent);
            if (data.classRank() != null
                    && Boolean.parseBoolean(
                            String.valueOf(data.layout().getOrDefault("showRank", true)))) {
                String rank =
                        bilingual("Rang", "الترتيب")
                                + " "
                                + data.classRank()
                                + "/"
                                + data.classSize();
                localizedAt(rank, MARGIN + 400, PAGE_WIDTH - MARGIN - 12, y - 19, false, 9);
            }
            y -= 60;
        }

        private void tableHeader() throws IOException {
            ensure(28);
            content.setNonStrokingColor(accent);
            content.addRect(MARGIN, y - 24, CONTENT_WIDTH, 24);
            content.fill();
            localizedAt(
                    fitLocalized(bilingual("MATIÈRE", "المادة"), 160, 7.2f),
                    MARGIN + 8,
                    MARGIN + 168,
                    y - 16,
                    true,
                    7.2f,
                    Color.WHITE);
            localizedAt(
                    fitLocalized(bilingual("NOTES", "النقط"), 160, 7.2f),
                    MARGIN + 178,
                    MARGIN + 340,
                    y - 16,
                    true,
                    7.2f,
                    Color.WHITE);
            localizedAt(
                    fitLocalized(
                            "BILINGUAL".equals(mode) ? "MOY./معدل" : bilingual("MOY.", "المعدل"),
                            52,
                            6.5f),
                    MARGIN + 355,
                    MARGIN + 408,
                    y - 16,
                    true,
                    6.5f,
                    Color.WHITE);
            localizedAt(
                    fitLocalized(
                            "BILINGUAL".equals(mode) ? "COEF./مع." : bilingual("COEF.", "المعامل"),
                            36,
                            5.5f),
                    MARGIN + 415,
                    MARGIN + 451,
                    y - 16,
                    true,
                    5.5f,
                    Color.WHITE);
            localizedAt(
                    fitLocalized(
                            "BILINGUAL".equals(mode)
                                    ? "OBS./ملاحظة"
                                    : bilingual("COMMENTAIRE", "الملاحظة"),
                            91,
                            5.5f),
                    MARGIN + 453,
                    PAGE_WIDTH - MARGIN - 4,
                    y - 16,
                    true,
                    5.5f,
                    Color.WHITE);
            y -= 24;
        }

        private void competencies() throws IOException {
            if (data.competencies().isEmpty()) return;
            ensure(64);
            sectionTitle(bilingual("COMPÉTENCES", "الكفايات"));
            for (CompetencyLine competency : data.competencies()) {
                ensure(35);
                String name = localized(competency.nameFr(), competency.nameAr());
                localizedAt(
                        fitLocalized(name, 280, 8.5f),
                        MARGIN + 5,
                        MARGIN + 285,
                        y - 12,
                        false,
                        8.5f);
                textAt(competency.levelCode(), MARGIN + 300, y - 12, fonts.latinBold, 8.5f, accent);
                localizedAt(
                        fitLocalized(orEmpty(competency.observation()), 120, 8),
                        MARGIN + 390,
                        PAGE_WIDTH - MARGIN - 4,
                        y - 12,
                        false,
                        8);
                line(y - 24, LINE, 0.5f);
                y -= 27;
            }
            y -= 12;
        }

        private void schoolLife() throws IOException {
            ensure(82);
            sectionTitle(bilingual("VIE SCOLAIRE", "الحياة المدرسية"));
            metric(MARGIN, bilingual("ABSENCES", "الغياب"), String.valueOf(data.absenceCount()));
            metric(
                    MARGIN + 172,
                    bilingual("JUSTIFIÉES", "المبررة"),
                    String.valueOf(data.excusedAbsenceCount()));
            metric(MARGIN + 344, bilingual("RETARDS", "التأخر"), String.valueOf(data.lateCount()));
            y -= 58;
        }

        private void appreciationAndSignature() throws IOException {
            ensure(105);
            sectionTitle(bilingual("APPRÉCIATION GÉNÉRALE", "الملاحظة العامة"));
            content.setNonStrokingColor(PAPER_TINT);
            content.addRect(MARGIN, y - 48, 320, 48);
            content.fill();
            String appreciation =
                    blank(data.generalAppreciation())
                            ? bilingual("Poursuivre les efforts.", "مواصلة الجهود.")
                            : data.generalAppreciation();
            List<String> lines = wrap(appreciation, 294, 8.5f, containsArabic(appreciation));
            float lineY = y - 16;
            for (String value : lines.stream().limit(3).toList()) {
                localizedAt(value, MARGIN + 10, MARGIN + 310, lineY, false, 8.5f);
                lineY -= 11;
            }
            float signX = MARGIN + 335;
            localizedAt(
                    bilingual("SIGNATURE", "التوقيع"),
                    signX,
                    PAGE_WIDTH - MARGIN - 8,
                    y - 12,
                    true,
                    8);
            localizedAt(
                    orEmpty(data.signerTitle()), signX, PAGE_WIDTH - MARGIN - 8, y - 30, false, 8);
            localizedAt(data.signerName(), signX, PAGE_WIDTH - MARGIN - 8, y - 49, true, 9.5f);
            y -= 64;
            if (!blank(data.publicationNote())) {
                text(
                        bilingual("Note de publication : ", "ملاحظة النشر: ")
                                + data.publicationNote(),
                        7.5f,
                        MUTED,
                        false);
                y -= 18;
            }
        }

        private void metric(float x, String label, String value) throws IOException {
            content.setNonStrokingColor(ACCENT_SOFT);
            content.addRect(x, y - 44, 158, 44);
            content.fill();
            textAt(value, x + 12, y - 28, fonts.latinBold, 17, accent);
            localizedAt(label, x + 48, x + 151, y - 25, true, 8);
        }

        private void labelValue(String label, String value, float x, float top, float width)
                throws IOException {
            localizedAt(label, x, x + width, top - 9, true, 6.7f, MUTED);
            String safe = blank(value) ? "-" : value;
            localizedAt(fitLocalized(safe, width, 10), x, x + width, top - 28, true, 10);
        }

        private void sectionTitle(String title) throws IOException {
            ensure(34);
            localizedAt(title, MARGIN, PAGE_WIDTH - MARGIN, y - 12, true, 9);
            content.setNonStrokingColor(accent);
            content.addRect(MARGIN, y - 23, 36, 2);
            content.fill();
            y -= 30;
        }

        private void text(String value, float size, Color color, boolean bold) throws IOException {
            localizedAt(value, MARGIN, PAGE_WIDTH - MARGIN, y, bold, size, color);
        }

        private void localizedAt(
                String value, float left, float right, float baseline, boolean bold, float size)
                throws IOException {
            localizedAt(value, left, right, baseline, bold, size, INK);
        }

        private void localizedAt(
                String value,
                float left,
                float right,
                float baseline,
                boolean bold,
                float size,
                Color color)
                throws IOException {
            if (containsArabic(value)) {
                String visual = arabicVisual(value);
                PDFont font = bold ? fonts.arabicBold : fonts.arabicRegular;
                rtlAt(visual, right, baseline, font, size, color);
            } else {
                textAt(
                        value,
                        left,
                        baseline,
                        bold ? fonts.latinBold : fonts.latinRegular,
                        size,
                        color);
            }
        }

        private void ensure(float required) throws IOException {
            if (y - required < 58) newPage();
        }

        private void finish() throws IOException {
            footer();
            content.close();
            content = null;
        }

        private void footer() throws IOException {
            line(45, LINE, 0.6f);
            String ref = "SchoolZ · " + data.reportCardId() + " · v" + data.versionNumber();
            textAt(ref, MARGIN, 30, fonts.latinRegular, 6.5f, MUTED);
            String timestamp =
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
                            .withZone(ZoneId.of("Europe/Paris"))
                            .format(data.generatedAt());
            String pageText = timestamp + " · " + pageNumber;
            textAt(
                    pageText,
                    PAGE_WIDTH - MARGIN - width(pageText, fonts.latinRegular, 6.5f),
                    30,
                    fonts.latinRegular,
                    6.5f,
                    MUTED);
        }

        private void line(float atY, Color color, float lineWidth) throws IOException {
            content.setStrokingColor(color);
            content.setLineWidth(lineWidth);
            content.moveTo(MARGIN, atY);
            content.lineTo(PAGE_WIDTH - MARGIN, atY);
            content.stroke();
        }

        private void textAt(
                String value, float x, float baseline, PDFont font, float size, Color color)
                throws IOException {
            if (blank(value)) return;
            content.beginText();
            content.setNonStrokingColor(color);
            content.setFont(font, size);
            content.newLineAtOffset(x, baseline);
            content.showText(value);
            content.endText();
        }

        private void rtlAt(
                String visual, float right, float baseline, PDFont font, float size, Color color)
                throws IOException {
            textAt(visual, right - width(visual, font, size), baseline, font, size, color);
        }

        private String bilingual(String french, String arabic) {
            return switch (mode) {
                case "AR" -> arabic;
                case "BILINGUAL" -> french + " / " + arabic;
                default -> french;
            };
        }

        private String localized(String french, String arabic) {
            if (mode.equals("AR")) return blank(arabic) ? french : arabic;
            if (mode.equals("BILINGUAL") && !blank(arabic)) return french + " / " + arabic;
            return french;
        }

        private String gradeSummary(List<GradeLine> grades) {
            if (grades.isEmpty()) return "-";
            return grades.stream()
                    .limit(4)
                    .map(
                            grade -> {
                                if (!"GRADED".equals(grade.status()))
                                    return statusLabel(grade.status());
                                return formatNumber(grade.score())
                                        + "/"
                                        + formatNumber(grade.maximumScore());
                            })
                    .reduce((left, right) -> left + " · " + right)
                    .orElse("-");
        }

        private String statusLabel(String status) {
            return switch (status) {
                case "ABSENT" -> bilingual("Abs.", "غ");
                case "EXEMPT" -> bilingual("Disp.", "معفى");
                default -> "-";
            };
        }

        private String fitLocalized(String value, float maximumWidth, float size) {
            PDFont font = containsArabic(value) ? fonts.arabicRegular : fonts.latinRegular;
            if (blank(value)) return "";
            boolean arabic = containsArabic(value);
            if (width(arabic ? arabicVisual(value) : value, font, size) <= maximumWidth)
                return value;
            String candidate = value;
            while (candidate.length() > 1
                    && width(
                                    arabic ? arabicVisual(candidate + "...") : candidate + "...",
                                    font,
                                    size)
                            > maximumWidth) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            return candidate.stripTrailing() + "...";
        }

        private List<String> wrap(String value, float maximumWidth, float size, boolean arabic) {
            if (blank(value)) return List.of();
            PDFont font = arabic ? fonts.arabicRegular : fonts.latinRegular;
            List<String> result = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : value.strip().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                String measured = arabic ? arabicVisual(candidate) : candidate;
                if (!line.isEmpty() && width(measured, font, size) > maximumWidth) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
            return result;
        }
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "FR";
        String normalized = mode.strip().toUpperCase(Locale.ROOT);
        return List.of("FR", "AR", "BILINGUAL").contains(normalized) ? normalized : "FR";
    }

    private static Color parseColor(Object configured) {
        if (configured == null) return ACCENT;
        try {
            return Color.decode(String.valueOf(configured));
        } catch (NumberFormatException ignored) {
            return ACCENT;
        }
    }

    private static String arabicVisual(String logical) {
        if (blank(logical)) return "";
        try {
            ArabicShaping shaping =
                    new ArabicShaping(
                            ArabicShaping.LETTERS_SHAPE | ArabicShaping.LENGTH_GROW_SHRINK);
            String shaped = shaping.shape(logical);
            Bidi bidi = new Bidi(shaped, Bidi.DIRECTION_RIGHT_TO_LEFT);
            return bidi.writeReordered(Bidi.DO_MIRRORING);
        } catch (ArabicShapingException exception) {
            return logical;
        }
    }

    private static boolean containsArabic(String value) {
        if (value == null) return false;
        return value.codePoints()
                .anyMatch(
                        code ->
                                (code >= 0x0600 && code <= 0x06ff)
                                        || (code >= 0x0750 && code <= 0x077f)
                                        || (code >= 0xfb50 && code <= 0xfdff)
                                        || (code >= 0xfe70 && code <= 0xfeff));
    }

    private static String fit(String value, float maximumWidth, PDFont font, float size) {
        if (blank(value)) return "";
        if (width(value, font, size) <= maximumWidth) return value;
        String ellipsis = "...";
        String candidate = value;
        while (candidate.length() > 1 && width(candidate + ellipsis, font, size) > maximumWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.stripTrailing() + ellipsis;
    }

    private static float width(String value, PDFont font, float size) {
        try {
            return font.getStringWidth(orEmpty(value)) / 1000f * size;
        } catch (IOException | IllegalArgumentException exception) {
            return orEmpty(value).length() * size * 0.55f;
        }
    }

    private static String formatNumber(BigDecimal value) {
        if (value == null) return "-";
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String formatDate(LocalDate value) {
        return value == null ? "-" : DATE.format(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
