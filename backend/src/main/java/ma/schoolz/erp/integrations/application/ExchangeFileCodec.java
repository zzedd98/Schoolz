package ma.schoolz.erp.integrations.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExchangeFileCodec {
    private static final int MAX_ROWS = 10_000;

    public EncodedFile encode(String format, List<String> headers, List<Map<String, String>> rows) {
        return switch (format) {
            case "CSV" -> new EncodedFile("text/csv; charset=UTF-8", encodeCsv(headers, rows));
            case "XLSX" ->
                    new EncodedFile(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            encodeXlsx(headers, rows));
            default -> throw new IllegalArgumentException("Format de fichier non pris en charge");
        };
    }

    public ParsedFile parse(String fileName, byte[] content, List<String> requiredHeaders) {
        if (content == null || content.length == 0)
            throw new IllegalArgumentException("Le fichier est vide");
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        ParsedFile parsed;
        try {
            parsed =
                    lower.endsWith(".csv")
                            ? parseCsv(content)
                            : lower.endsWith(".xlsx") ? parseXlsx(content) : null;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Le fichier ne peut pas être lu", exception);
        }
        if (parsed == null)
            throw new IllegalArgumentException("Seuls les fichiers CSV et XLSX sont acceptés");
        Set<String> actual = new LinkedHashSet<>(parsed.headers());
        List<String> missing =
                requiredHeaders.stream().filter(header -> !actual.contains(header)).toList();
        if (!missing.isEmpty())
            throw new IllegalArgumentException(
                    "Colonnes obligatoires manquantes : " + String.join(", ", missing));
        return parsed;
    }

    private byte[] encodeCsv(List<String> headers, List<Map<String, String>> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                OutputStreamWriter writer =
                        new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            try (CSVPrinter printer =
                    new CSVPrinter(
                            writer,
                            CSVFormat.DEFAULT
                                    .builder()
                                    .setDelimiter(';')
                                    .setHeader(headers.toArray(String[]::new))
                                    .get())) {
                for (Map<String, String> row : rows) {
                    printer.printRecord(
                            headers.stream().map(header -> safe(row.get(header))).toList());
                }
                printer.flush();
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de générer le CSV", exception);
        }
    }

    private byte[] encodeXlsx(List<String> headers, List<Map<String, String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SchoolZ");
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++)
                header.createCell(column).setCellValue(headers.get(column));
            for (int index = 0; index < rows.size(); index++) {
                Row target = sheet.createRow(index + 1);
                Map<String, String> source = rows.get(index);
                for (int column = 0; column < headers.size(); column++)
                    target.createCell(column)
                            .setCellValue(source.getOrDefault(headers.get(column), ""));
            }
            for (int column = 0; column < headers.size(); column++)
                sheet.setColumnWidth(column, 20 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de générer le classeur", exception);
        }
    }

    private ParsedFile parseCsv(byte[] content) throws IOException {
        String source = new String(content, StandardCharsets.UTF_8);
        if (source.startsWith("\uFEFF")) source = source.substring(1);
        char delimiter =
                firstLine(source).chars().filter(value -> value == ';').count()
                                > firstLine(source).chars().filter(value -> value == ',').count()
                        ? ';'
                        : ',';
        CSVFormat format =
                CSVFormat.DEFAULT
                        .builder()
                        .setDelimiter(delimiter)
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setIgnoreSurroundingSpaces(true)
                        .setAllowDuplicateHeaderNames(false)
                        .get();
        try (CSVParser parser =
                format.parse(
                        new InputStreamReader(
                                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8))) {
            List<String> headers = cleanHeaders(parser.getHeaderNames());
            List<Map<String, String>> rows = new ArrayList<>();
            parser.forEach(
                    record -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        headers.forEach(header -> row.put(header, record.get(header).strip()));
                        if (row.values().stream().anyMatch(value -> !value.isBlank()))
                            rows.add(row);
                        ensureLimit(rows.size());
                    });
            return new ParsedFile("CSV", headers, rows);
        }
    }

    private ParsedFile parseXlsx(byte[] content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0)
                throw new IllegalArgumentException("Le classeur ne contient aucune feuille");
            Sheet sheet = workbook.getSheetAt(0);
            Row first = sheet.getRow(sheet.getFirstRowNum());
            if (first == null) throw new IllegalArgumentException("La ligne d’en-tête est absente");
            DataFormatter formatter = new DataFormatter(Locale.FRANCE, true);
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < first.getLastCellNum(); column++)
                headers.add(value(first.getCell(column), formatter));
            headers = cleanHeaders(headers);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = first.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row source = sheet.getRow(index);
                if (source == null) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++)
                    row.put(headers.get(column), value(source.getCell(column), formatter));
                if (row.values().stream().anyMatch(item -> !item.isBlank())) rows.add(row);
                ensureLimit(rows.size());
            }
            return new ParsedFile("XLSX", headers, rows);
        }
    }

    private static String value(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA)
            throw new IllegalArgumentException("Les formules Excel ne sont pas autorisées");
        return formatter.formatCellValue(cell).strip();
    }

    private static List<String> cleanHeaders(List<String> headers) {
        List<String> clean = headers.stream().map(String::strip).toList();
        if (clean.isEmpty() || clean.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Chaque colonne doit avoir un en-tête");
        if (new LinkedHashSet<>(clean).size() != clean.size())
            throw new IllegalArgumentException("Les en-têtes doivent être uniques");
        return clean;
    }

    private static String firstLine(String value) {
        return value.lines().findFirst().orElse("");
    }

    private static void ensureLimit(int size) {
        if (size > MAX_ROWS) throw new IllegalArgumentException("Le fichier dépasse 10 000 lignes");
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.matches("^[=+@].*") ? "'" + value : value;
    }

    public record EncodedFile(String contentType, byte[] content) {}

    public record ParsedFile(String format, List<String> headers, List<Map<String, String>> rows) {}
}
