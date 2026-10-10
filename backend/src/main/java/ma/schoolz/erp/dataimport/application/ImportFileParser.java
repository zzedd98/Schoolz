package ma.schoolz.erp.dataimport.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ma.schoolz.erp.foundation.security.FileSecurityScanner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImportFileParser {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final int maxRows;
    private final FileSecurityScanner fileSecurity;

    public ImportFileParser(
            @Value("${schoolz.imports.max-rows:10000}") int maxRows,
            FileSecurityScanner fileSecurity) {
        this.maxRows = maxRows;
        this.fileSecurity = fileSecurity;
    }

    public ParsedFile parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportValidationException("Le fichier est vide");
        }
        String name = file.getOriginalFilename() == null ? "import" : file.getOriginalFilename();
        String lowerName = name.toLowerCase(Locale.ROOT);
        try {
            byte[] content = file.getBytes();
            fileSecurity.inspect(
                    name, file.getContentType(), content, ALLOWED_TYPES, MAX_FILE_SIZE);
            if (lowerName.endsWith(".csv")) {
                return parseCsv(content);
            }
            if (lowerName.endsWith(".xlsx")) {
                return parseXlsx(content);
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ImportValidationException validation) {
                throw validation;
            }
            throw new ImportValidationException("Le fichier ne peut pas être lu");
        }
        throw new ImportValidationException("Seuls les fichiers CSV et XLSX sont acceptés");
    }

    private ParsedFile parseCsv(byte[] content) throws IOException {
        String source = new String(content, StandardCharsets.UTF_8);
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        char delimiter = detectDelimiter(source);
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
            List<ParsedRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                boolean populated = false;
                for (String header : headers) {
                    String value =
                            record.isMapped(header) && record.isSet(header)
                                    ? record.get(header).strip()
                                    : "";
                    values.put(header, value);
                    populated |= !value.isBlank();
                }
                if (populated) {
                    rows.add(new ParsedRow(Math.toIntExact(record.getRecordNumber() + 1), values));
                    ensureLimit(rows.size());
                }
            }
            return complete("CSV", headers, rows);
        } catch (IllegalArgumentException exception) {
            throw new ImportValidationException("Les en-têtes CSV sont invalides ou dupliqués");
        }
    }

    private ParsedFile parseXlsx(byte[] content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ImportValidationException("Le classeur ne contient aucune feuille");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ImportValidationException("La première ligne doit contenir les en-têtes");
            }
            DataFormatter formatter = new DataFormatter(Locale.FRANCE, true);
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                headers.add(cellValue(headerRow.getCell(column), formatter).strip());
            }
            headers = cleanHeaders(headers);
            List<ParsedRow> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean populated = false;
                for (int column = 0; column < headers.size(); column++) {
                    String value = cellValue(row.getCell(column), formatter).strip();
                    values.put(headers.get(column), value);
                    populated |= !value.isBlank();
                }
                if (populated) {
                    rows.add(new ParsedRow(rowIndex + 1, values));
                    ensureLimit(rows.size());
                }
            }
            return complete("XLSX", headers, rows);
        }
    }

    private static String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            throw new ImportValidationException("Les formules Excel ne sont pas autorisées");
        }
        return formatter.formatCellValue(cell);
    }

    private static List<String> cleanHeaders(List<String> input) {
        List<String> headers = input.stream().map(String::strip).toList();
        if (headers.isEmpty() || headers.stream().anyMatch(String::isBlank)) {
            throw new ImportValidationException("Chaque colonne doit avoir un en-tête");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String header : headers) {
            if (!normalized.add(ImportDefinitionCatalog.normalizeHeader(header))) {
                throw new ImportValidationException("Les en-têtes doivent être uniques");
            }
        }
        return headers;
    }

    private ParsedFile complete(String format, List<String> headers, List<ParsedRow> rows) {
        if (rows.isEmpty()) {
            throw new ImportValidationException("Le fichier ne contient aucune ligne de données");
        }
        return new ParsedFile(format, headers, rows);
    }

    private void ensureLimit(int size) {
        if (size > maxRows) {
            throw new ImportValidationException(
                    "Le fichier dépasse la limite de " + maxRows + " lignes");
        }
    }

    private static char detectDelimiter(String content) {
        int comma = 0;
        int semicolon = 0;
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                quoted = !quoted;
            } else if (!quoted && (current == '\r' || current == '\n')) {
                break;
            } else if (!quoted && current == ',') {
                comma++;
            } else if (!quoted && current == ';') {
                semicolon++;
            }
        }
        return semicolon > comma ? ';' : ',';
    }

    public record ParsedFile(String format, List<String> headers, List<ParsedRow> rows) {}

    public record ParsedRow(int rowNumber, Map<String, String> values) {}
}
