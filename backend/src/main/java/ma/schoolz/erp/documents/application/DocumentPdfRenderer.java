package ma.schoolz.erp.documents.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
public class DocumentPdfRenderer {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMMM uuuu", Locale.FRENCH);

    public byte[] render(
            String template,
            String title,
            String recipient,
            String reference,
            Map<String, ?> fields) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(content, bold, 11, 52, 790, "SCHOOLZ · DOCUMENT OFFICIEL");
                text(content, bold, 19, 52, 742, safe(title));
                text(content, regular, 10, 52, 718, "Modèle : " + safe(template));
                line(content, 52, 703, 543, 703);
                float y = 665;
                text(content, bold, 11, 52, y, "Destinataire");
                text(content, regular, 11, 175, y, safe(recipient));
                y -= 25;
                text(content, bold, 11, 52, y, "Référence");
                text(content, regular, 11, 175, y, safe(reference));
                y -= 25;
                text(content, bold, 11, 52, y, "Date");
                text(content, regular, 11, 175, y, DATE.format(LocalDate.now()));
                y -= 45;
                for (Map.Entry<String, ?> field : fields.entrySet()) {
                    text(content, bold, 10, 52, y, safe(field.getKey()));
                    List<String> lines = wrap(safe(String.valueOf(field.getValue())), 74);
                    for (String value : lines) {
                        text(content, regular, 10, 175, y, value);
                        y -= 16;
                    }
                    y -= 10;
                    if (y < 120) break;
                }
                line(content, 52, 92, 543, 92);
                text(
                        content,
                        regular,
                        8,
                        52,
                        75,
                        "Document généré et versionné par SchoolZ. Vérifier son statut dans le"
                                + " DMS.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Le document PDF n'a pas pu être généré", exception);
        }
    }

    private static void text(
            PDPageContentStream content,
            PDType1Font font,
            float size,
            float x,
            float y,
            String value)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(ascii(value));
        content.endText();
    }

    private static void line(PDPageContentStream content, float x1, float y1, float x2, float y2)
            throws IOException {
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = value;
        while (remaining.length() > width) {
            int cut = remaining.lastIndexOf(' ', width);
            if (cut < 1) cut = width;
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        lines.add(remaining);
        return lines;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value.strip();
    }

    private static String ascii(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.getType(character) == Character.NON_SPACING_MARK) continue;
            result.append(character >= 32 && character <= 255 ? character : '?');
        }
        return result.toString();
    }
}
