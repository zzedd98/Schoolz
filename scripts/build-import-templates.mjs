import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const root = path.resolve(import.meta.dirname, "..");
const outputDir = path.join(
  root,
  "outputs",
  "019fde12-16a7-7ea2-bac8-58fecf25ccaf",
  "import-templates",
);
const resourceDir = path.join(root, "backend", "src", "main", "resources", "import-templates");
await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(resourceDir, { recursive: true });

const templates = [
  {
    name: "students",
    title: "Élèves",
    fields: [
      ["first_name", "Prénom", true, "Yasmine"],
      ["last_name", "Nom", true, "Alaoui"],
      ["preferred_name", "Prénom usuel", false, "Yas"],
      ["birth_date", "Date de naissance", true, new Date(Date.UTC(2020, 2, 14))],
      ["gender", "Genre", false, "FEMALE"],
      ["nationality_code", "Nationalité ISO", false, "MA"],
      ["school_identifier", "Matricule élève", true, "ELV-2026-001"],
      ["household_name", "Nom du foyer", true, "Famille Alaoui"],
      ["address_line1", "Adresse", true, "24 rue Al Massira"],
      ["address_line2", "Complément d'adresse", false, "Appartement 4"],
      ["city", "Ville", true, "Casablanca"],
      ["region", "Région", false, "Casablanca-Settat"],
      ["postal_code", "Code postal", false, "20250"],
      ["country_code", "Pays ISO", true, "MA"],
      ["guardian_first_name", "Prénom responsable", true, "Nadia"],
      ["guardian_last_name", "Nom responsable", true, "Alaoui"],
      ["guardian_relationship", "Lien familial", true, "MOTHER"],
      ["guardian_email", "E-mail responsable", true, "nadia@example.ma"],
      ["guardian_phone", "Téléphone responsable", true, "212 612 345 678"],
      ["guardian_occupation", "Profession responsable", false, "Architecte"],
      ["guardian_nationality_code", "Nationalité responsable ISO", false, "MA"],
      ["financial_responsible", "Responsable financier", false, true],
      ["campus_code", "Code campus", true, "CASA"],
      ["academic_year", "Année scolaire", true, "2026-2027"],
      ["classroom_code", "Code classe", false, "1AP-A"],
      ["enrolled_on", "Date d'inscription", true, new Date(Date.UTC(2026, 8, 1))],
    ],
  },
  {
    name: "parents",
    title: "Parents et responsables",
    fields: [
      ["first_name", "Prénom", true, "Omar"],
      ["last_name", "Nom", true, "Alaoui"],
      ["student_identifier", "Matricule élève", true, "ELV-2026-001"],
      ["relationship", "Lien familial", true, "FATHER"],
      ["email", "E-mail", false, "omar@example.ma"],
      ["phone", "Téléphone", false, "212 612 345 679"],
      ["occupation", "Profession", false, "Commerçant"],
      ["nationality_code", "Nationalité ISO", false, "MA"],
      ["financial_responsible", "Responsable financier", false, false],
    ],
  },
  {
    name: "staff",
    title: "Personnel",
    fields: [
      ["first_name", "Prénom", true, "Sara"],
      ["last_name", "Nom", true, "Bennani"],
      ["employee_number", "Matricule personnel", true, "EMP-001"],
      ["job_title", "Fonction", true, "Enseignante"],
      ["hired_on", "Date d'embauche", true, new Date(Date.UTC(2024, 8, 1))],
      ["email", "E-mail", false, "sara@example.ma"],
      ["phone", "Téléphone", false, "212 612 345 680"],
      ["nationality_code", "Nationalité ISO", false, "MA"],
    ],
  },
  {
    name: "classes",
    title: "Classes",
    fields: [
      ["code", "Code classe", true, "1AP-B"],
      ["name", "Nom de la classe", true, "1AP B"],
      ["campus_code", "Code campus", true, "CASA"],
      ["academic_year", "Année scolaire", true, "2026-2027"],
      ["grade_code", "Code niveau", true, "1AP"],
      ["capacity", "Capacité", true, 28],
      ["room_code", "Code salle", false, "S-102"],
    ],
  },
  {
    name: "payments",
    title: "Paiements historiques",
    fields: [
      ["student_identifier", "Matricule élève", true, "ELV-2026-001"],
      ["amount", "Montant", true, 1250],
      ["paid_on", "Date de paiement", true, new Date(Date.UTC(2026, 7, 15))],
      ["payment_method", "Mode de paiement", true, "BANK_TRANSFER"],
      ["external_reference", "Référence externe", false, "VIR-2026-001"],
      ["currency", "Devise ISO", false, "MAD"],
    ],
  },
  {
    name: "opening-balances",
    title: "Anciens soldes",
    fields: [
      ["student_identifier", "Matricule élève", true, "ELV-2026-001"],
      ["amount", "Solde", true, 2500],
      ["as_of_date", "Date du solde", true, new Date(Date.UTC(2026, 7, 31))],
      ["description", "Description", false, "Solde repris de l'ancien système"],
      ["currency", "Devise ISO", false, "MAD"],
    ],
  },
];

const columnName = (index) => {
  let value = index + 1;
  let result = "";
  while (value > 0) {
    const remainder = (value - 1) % 26;
    result = String.fromCharCode(65 + remainder) + result;
    value = Math.floor((value - 1) / 26);
  }
  return result;
};

for (const template of templates) {
  const workbook = Workbook.create();
  const data = workbook.worksheets.add("Import");
  const guide = workbook.worksheets.add("Guide");
  data.showGridLines = false;
  guide.showGridLines = false;

  const lastColumn = columnName(template.fields.length - 1);
  data.getRange(`A1:${lastColumn}2`).values = [
    template.fields.map(([key]) => key),
    template.fields.map(([, , , example]) => example),
  ];
  data.freezePanes.freezeRows(1);
  data.getRange(`A1:${lastColumn}1`).format = {
    fill: "#123A5A",
    font: { bold: true, color: "#FFFFFF", size: 10 },
    wrapText: true,
    verticalAlignment: "center",
    borders: { preset: "outside", style: "thin", color: "#0B263C" },
  };
  data.getRange(`A1:${lastColumn}1`).format.rowHeight = 42;
  data.getRange(`A2:${lastColumn}2`).format = {
    fill: "#FFF4D6",
    font: { color: "#5C4322" },
    borders: { preset: "inside", style: "thin", color: "#E5D9BD" },
  };
  data.getRange(`A1:${lastColumn}2`).format.autofitColumns();
  for (let index = 0; index < template.fields.length; index += 1) {
    const column = columnName(index);
    const example = template.fields[index][3];
    const exampleWidth =
      example instanceof Date ? 10 : typeof example === "boolean" ? 5 : String(example).length;
    data.getRange(`${column}1:${column}25`).format.columnWidth = Math.min(
      28,
      Math.max(14, String(template.fields[index][0]).length + 3, exampleWidth + 2),
    );
    const key = template.fields[index][0];
    if (key.includes("date") || key.endsWith("_on")) {
      data.getRange(`${column}2:${column}200`).format.numberFormat = "yyyy-mm-dd";
    }
    if (key === "amount") {
      data.getRange(`${column}2:${column}200`).format.numberFormat = "#,##0.00";
    }
  }
  const financialIndex = template.fields.findIndex(([key]) => key === "financial_responsible");
  if (financialIndex >= 0) {
    const column = columnName(financialIndex);
    data.getRange(`${column}2:${column}200`).dataValidation = {
      rule: { type: "list", values: ["true", "false"] },
    };
  }

  guide.getRange("A1:D1").merge();
  guide.getRange("A1:D1").values = [[`Modèle SchoolZ · ${template.title}`]];
  guide.getRange("A1:D1").format = {
    fill: "#123A5A",
    font: { bold: true, color: "#FFFFFF", size: 16 },
    verticalAlignment: "center",
  };
  guide.getRange("A1:D1").format.rowHeight = 34;
  guide.getRange("A3:D3").values = [["Clé technique", "Libellé", "Obligatoire", "Exemple"]];
  guide.getRange("A3:D3").format = {
    fill: "#DCEAF3",
    font: { bold: true, color: "#123A5A" },
    borders: { preset: "outside", style: "thin", color: "#9DB7C8" },
  };
  const guideRows = template.fields.map(([key, label, required, example]) => {
    const displayedExample =
      example instanceof Date
        ? example.toISOString().slice(0, 10)
        : typeof example === "boolean"
          ? String(example)
          : example;
    return [key, label, required ? "Oui" : "Non", displayedExample];
  });
  guide.getRange(`A4:D${3 + guideRows.length}`).values = guideRows;
  guide.getRange(`A4:D${3 + guideRows.length}`).format = {
    borders: {
      insideHorizontal: { style: "thin", color: "#E2E8EC" },
      bottom: { style: "thin", color: "#9DB7C8" },
    },
  };
  guide.getRange(`C4:C${3 + guideRows.length}`).conditionalFormats.addCustom('=C4="Oui"', {
    fill: "#E5F5ED",
    font: { color: "#17623A", bold: true },
  });
  guide.getRange("A3:D40").format.autofitColumns();
  guide.getRange("A1:A40").format.columnWidth = 25;
  guide.getRange("B1:B40").format.columnWidth = 30;
  guide.getRange("C1:C40").format.columnWidth = 15;
  guide.getRange("D1:D40").format.columnWidth = 32;
  guide.freezePanes.freezeRows(3);

  const inspected = await workbook.inspect({
    kind: "table",
    range: `Import!A1:${lastColumn}2`,
    include: "values,formulas",
    tableMaxRows: 3,
    tableMaxCols: 30,
  });
  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 50 },
    summary: `formula scan ${template.name}`,
  });
  if (errors.ndjson.includes('"match"')) {
    throw new Error(`Formula error in ${template.name}`);
  }
  for (const sheetName of ["Import", "Guide"]) {
    const preview = await workbook.render({
      sheetName,
      range:
        sheetName === "Import"
          ? `A1:${lastColumn}4`
          : `A1:D${Math.min(3 + template.fields.length, 32)}`,
      scale: 1.5,
      format: "png",
    });
    await fs.writeFile(
      path.join(outputDir, `${template.name}-${sheetName.toLowerCase()}.png`),
      new Uint8Array(await preview.arrayBuffer()),
    );
  }
  const fileName = `schoolz-import-${template.name}.xlsx`;
  const xlsx = await SpreadsheetFile.exportXlsx(workbook);
  await xlsx.save(path.join(outputDir, fileName));
  await xlsx.save(path.join(resourceDir, fileName));
  await fs.rm(path.join(resourceDir, `${fileName}.inspect.ndjson`), { force: true });
  console.log(`${fileName}: ${template.fields.length} colonnes vérifiées; ${inspected.ndjson.length} octets inspectés`);
}
