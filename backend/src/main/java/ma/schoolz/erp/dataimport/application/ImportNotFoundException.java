package ma.schoolz.erp.dataimport.application;

public class ImportNotFoundException extends RuntimeException {
    public ImportNotFoundException() {
        super("Import introuvable");
    }
}
