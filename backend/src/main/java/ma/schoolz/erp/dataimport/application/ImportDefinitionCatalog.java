package ma.schoolz.erp.dataimport.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ImportDefinitionCatalog {

    private final Map<ImportType, Definition> definitions = new LinkedHashMap<>();

    public ImportDefinitionCatalog() {
        definitions.put(
                ImportType.STUDENTS,
                definition(
                        "Élèves",
                        required("first_name", "Prénom", "Yasmine", "prenom", "prénom"),
                        required("last_name", "Nom", "Alaoui", "nom", "nom_famille"),
                        optional("preferred_name", "Prénom usuel", "Yas", "prenom_usuel"),
                        required("birth_date", "Date de naissance", "2020-03-14", "date_naissance"),
                        optional("gender", "Genre", "FEMALE", "sexe", "genre"),
                        optional("nationality_code", "Nationalité (ISO)", "MA", "nationalite"),
                        required(
                                "school_identifier",
                                "Matricule élève",
                                "ELV-2026-001",
                                "matricule",
                                "identifiant_eleve"),
                        required(
                                "household_name",
                                "Nom du foyer",
                                "Famille Alaoui",
                                "foyer",
                                "famille"),
                        required("address_line1", "Adresse", "24 rue Al Massira", "adresse"),
                        optional(
                                "address_line2",
                                "Complément d'adresse",
                                "Appartement 4",
                                "adresse_2"),
                        required("city", "Ville", "Casablanca", "ville"),
                        optional("region", "Région", "Casablanca-Settat", "region"),
                        optional("postal_code", "Code postal", "20250", "code_postal"),
                        required("country_code", "Pays (ISO)", "MA", "pays"),
                        required(
                                "guardian_first_name",
                                "Prénom du responsable",
                                "Nadia",
                                "prenom_parent",
                                "prenom_responsable"),
                        required(
                                "guardian_last_name",
                                "Nom du responsable",
                                "Alaoui",
                                "nom_parent",
                                "nom_responsable"),
                        required(
                                "guardian_relationship",
                                "Lien familial",
                                "MOTHER",
                                "lien_parent",
                                "relation"),
                        required(
                                "guardian_email",
                                "E-mail du responsable",
                                "nadia@example.ma",
                                "email_parent",
                                "email_responsable"),
                        required(
                                "guardian_phone",
                                "Téléphone du responsable",
                                "+212612345678",
                                "telephone_parent",
                                "tel_parent"),
                        optional(
                                "guardian_occupation",
                                "Profession du responsable",
                                "Architecte",
                                "profession_parent"),
                        optional(
                                "guardian_nationality_code",
                                "Nationalité responsable (ISO)",
                                "MA",
                                "nationalite_parent"),
                        optional(
                                "financial_responsible",
                                "Responsable financier",
                                "true",
                                "payeur",
                                "responsable_financier"),
                        required("campus_code", "Code campus", "CASA", "campus"),
                        required(
                                "academic_year",
                                "Année scolaire",
                                "2026-2027",
                                "annee_scolaire",
                                "annee"),
                        optional("classroom_code", "Code classe", "1AP-A", "classe"),
                        required(
                                "enrolled_on",
                                "Date d'inscription",
                                "2026-09-01",
                                "date_inscription")));
        definitions.put(
                ImportType.PARENTS,
                definition(
                        "Parents et responsables",
                        required("first_name", "Prénom", "Omar", "prenom", "prénom"),
                        required("last_name", "Nom", "Alaoui", "nom"),
                        required(
                                "student_identifier",
                                "Matricule élève",
                                "ELV-2026-001",
                                "matricule",
                                "identifiant_eleve"),
                        required(
                                "relationship",
                                "Lien familial",
                                "FATHER",
                                "relation",
                                "lien_parent"),
                        optional("email", "E-mail", "omar@example.ma", "courriel"),
                        optional("phone", "Téléphone", "+212612345679", "telephone", "tel"),
                        optional("occupation", "Profession", "Commerçant", "profession"),
                        optional("nationality_code", "Nationalité (ISO)", "MA", "nationalite"),
                        optional(
                                "financial_responsible",
                                "Responsable financier",
                                "false",
                                "payeur")));
        definitions.put(
                ImportType.STAFF,
                definition(
                        "Personnel",
                        required("first_name", "Prénom", "Sara", "prenom", "prénom"),
                        required("last_name", "Nom", "Bennani", "nom"),
                        required(
                                "employee_number",
                                "Matricule personnel",
                                "EMP-001",
                                "matricule",
                                "matricule_personnel"),
                        required("job_title", "Fonction", "Enseignante", "poste", "fonction"),
                        required("hired_on", "Date d'embauche", "2024-09-01", "date_embauche"),
                        optional("email", "E-mail", "sara@example.ma", "courriel"),
                        optional("phone", "Téléphone", "+212612345680", "telephone", "tel"),
                        optional("nationality_code", "Nationalité (ISO)", "MA", "nationalite")));
        definitions.put(
                ImportType.CLASSES,
                definition(
                        "Classes",
                        required("code", "Code classe", "1AP-B", "code_classe", "classe"),
                        required("name", "Nom de la classe", "1AP B", "nom_classe", "nom"),
                        required("campus_code", "Code campus", "CASA", "campus"),
                        required(
                                "academic_year",
                                "Année scolaire",
                                "2026-2027",
                                "annee_scolaire",
                                "annee"),
                        required("grade_code", "Code niveau", "1AP", "niveau", "code_niveau"),
                        required("capacity", "Capacité", "28", "capacite", "effectif_max"),
                        optional("room_code", "Code salle", "S-102", "salle", "code_salle")));
        definitions.put(
                ImportType.PAYMENTS,
                definition(
                        "Paiements historiques",
                        required(
                                "student_identifier",
                                "Matricule élève",
                                "ELV-2026-001",
                                "matricule",
                                "identifiant_eleve"),
                        required("amount", "Montant", "1250.00", "montant"),
                        required("paid_on", "Date de paiement", "2026-08-15", "date_paiement"),
                        required(
                                "payment_method",
                                "Mode de paiement",
                                "BANK_TRANSFER",
                                "mode_paiement"),
                        optional(
                                "external_reference",
                                "Référence externe",
                                "VIR-2026-001",
                                "reference",
                                "reference_externe"),
                        optional("currency", "Devise", "MAD", "devise")));
        definitions.put(
                ImportType.OPENING_BALANCES,
                definition(
                        "Anciens soldes",
                        required(
                                "student_identifier",
                                "Matricule élève",
                                "ELV-2026-001",
                                "matricule",
                                "identifiant_eleve"),
                        required("amount", "Solde", "2500.00", "montant", "solde"),
                        required(
                                "as_of_date",
                                "Date du solde",
                                "2026-08-31",
                                "date_solde",
                                "date_arrete"),
                        optional(
                                "description",
                                "Description",
                                "Solde repris de l'ancien système",
                                "libelle"),
                        optional("currency", "Devise", "MAD", "devise")));
    }

    public Definition definition(ImportType type) {
        return definitions.get(type);
    }

    public Map<String, String> suggestedMapping(ImportType type, List<String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        Definition definition = definition(type);
        for (Field field : definition.fields()) {
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(normalizeHeader(field.key()));
            candidates.add(normalizeHeader(field.label()));
            field.aliases().stream()
                    .map(ImportDefinitionCatalog::normalizeHeader)
                    .forEach(candidates::add);
            headers.stream()
                    .filter(header -> candidates.contains(normalizeHeader(header)))
                    .findFirst()
                    .ifPresent(header -> result.put(field.key(), header));
        }
        return result;
    }

    public static String normalizeHeader(String value) {
        String decomposed = Normalizer.normalize(value.strip(), Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static Definition definition(String label, Field... fields) {
        return new Definition(label, List.of(fields));
    }

    private static Field required(String key, String label, String example, String... aliases) {
        return new Field(key, label, true, example, List.of(aliases));
    }

    private static Field optional(String key, String label, String example, String... aliases) {
        return new Field(key, label, false, example, List.of(aliases));
    }

    public record Definition(String label, List<Field> fields) {
        public List<String> requiredKeys() {
            return fields.stream().filter(Field::required).map(Field::key).toList();
        }

        public List<String> keys() {
            return fields.stream().map(Field::key).toList();
        }

        public List<List<Object>> templateRows() {
            List<List<Object>> rows = new ArrayList<>();
            rows.add(fields.stream().map(Field::key).map(value -> (Object) value).toList());
            rows.add(fields.stream().map(Field::example).map(value -> (Object) value).toList());
            return rows;
        }
    }

    public record Field(
            String key, String label, boolean required, String example, List<String> aliases) {}
}
