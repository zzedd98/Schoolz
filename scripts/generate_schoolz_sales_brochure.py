from pathlib import Path

from reportlab.lib.colors import HexColor, white
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf" / "schoolz-brochure-commerciale-features-ecoles.pdf"
SCREENS = ROOT / "tmp" / "pdfs" / "schoolz-guide-pro" / "screens"
PW, PH = landscape(A4)

NAVY = HexColor("#112A5C")
NAVY_2 = HexColor("#0A1B3E")
BLUE = HexColor("#2563EB")
AZURE = HexColor("#38BDF8")
ICE = HexColor("#EEF6FF")
ICE_2 = HexColor("#DCEBFF")
INK = HexColor("#152033")
SLATE = HexColor("#526176")
MUTED = HexColor("#7B889B")
LINE = HexColor("#D7E3F3")
PAPER = HexColor("#F8FBFF")
GOLD = HexColor("#E8BA5B")
CORAL = HexColor("#EF8D75")
WHITE = HexColor("#FFFFFF")


def fonts():
    pdfmetrics.registerFont(TTFont("SZ", r"C:\Windows\Fonts\segoeui.ttf"))
    pdfmetrics.registerFont(TTFont("SZ-Bold", r"C:\Windows\Fonts\segoeuib.ttf"))
    pdfmetrics.registerFont(TTFont("SZ-Display", r"C:\Windows\Fonts\georgia.ttf"))
    pdfmetrics.registerFont(TTFont("SZ-DisplayBold", r"C:\Windows\Fonts\georgiab.ttf"))


def wrap(text, font, size, max_width):
    words = text.split()
    lines, line = [], ""
    for word in words:
        candidate = word if not line else f"{line} {word}"
        if pdfmetrics.stringWidth(candidate, font, size) <= max_width:
            line = candidate
        else:
            if line:
                lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def paragraph(c, text, x, y, width, size=10, color=SLATE, leading=None, font="SZ", max_lines=None):
    leading = leading or size * 1.38
    lines = wrap(text, font, size, width)
    if max_lines:
        lines = lines[:max_lines]
    c.setFont(font, size)
    c.setFillColor(color)
    for line in lines:
        c.drawString(x, y, line)
        y -= leading
    return y


def pill(c, text, x, y, fill=ICE, color=BLUE, font_size=8.2, pad=12, height=22):
    width = pdfmetrics.stringWidth(text, "SZ-Bold", font_size) + 2 * pad
    c.setFillColor(fill)
    c.roundRect(x, y, width, height, height / 2, stroke=0, fill=1)
    c.setFillColor(color)
    c.setFont("SZ-Bold", font_size)
    c.drawCentredString(x + width / 2, y + (height - font_size) / 2 + 1.2, text)
    return width


def label(c, text, x, y, color=BLUE):
    c.setFillColor(color)
    c.setFont("SZ-Bold", 8.4)
    c.drawString(x, y, text.upper())


def footer(c, page, total=11):
    c.setStrokeColor(LINE)
    c.setLineWidth(0.6)
    c.line(48, 31, PW - 48, 31)
    c.setFillColor(MUTED)
    c.setFont("SZ", 7.6)
    c.drawString(48, 17, "SchoolZ - brochure commerciale - version de demonstration M23")
    c.drawRightString(PW - 48, 17, f"{page:02d} / {total:02d}")


def page_base(c, page, kicker, title, subtitle=None, dark=False):
    c.setFillColor(NAVY_2 if dark else PAPER)
    c.rect(0, 0, PW, PH, stroke=0, fill=1)
    if dark:
        c.setFillColor(NAVY)
        c.circle(PW + 20, PH + 8, 160, stroke=0, fill=1)
        c.setFillColor(BLUE)
        c.circle(PW - 84, PH + 3, 77, stroke=0, fill=1)
    else:
        c.setFillColor(ICE)
        c.circle(PW + 15, PH + 8, 120, stroke=0, fill=1)
        c.setFillColor(ICE_2)
        c.circle(PW - 62, PH + 16, 55, stroke=0, fill=1)
    label(c, kicker, 48, PH - 58, AZURE if dark else BLUE)
    c.setFillColor(WHITE if dark else INK)
    c.setFont("SZ-DisplayBold", 28)
    c.drawString(48, PH - 97, title)
    if subtitle:
        paragraph(c, subtitle, 48, PH - 122, 660, size=10.2, color=ICE_2 if dark else SLATE, leading=14)
    footer(c, page)


def logo(c, x, y, size=34):
    c.setFillColor(BLUE)
    c.roundRect(x, y, size, size, 9, stroke=0, fill=1)
    c.setFillColor(AZURE)
    c.circle(x + size * 0.72, y + size * 0.72, size * 0.21, stroke=0, fill=1)
    c.setFillColor(WHITE)
    c.setFont("SZ-Bold", size * 0.47)
    c.drawCentredString(x + size / 2, y + size * 0.27, "S")


def shadow_card(c, x, y, w, h, fill=WHITE, radius=16, stroke=LINE):
    c.saveState()
    try:
        c.setFillAlpha(0.08)
    except Exception:
        pass
    c.setFillColor(NAVY_2)
    c.roundRect(x + 4, y - 5, w, h, radius, stroke=0, fill=1)
    c.restoreState()
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(0.8)
    c.roundRect(x, y, w, h, radius, stroke=1, fill=1)


def screenshot(c, path, x, y, w, h):
    path = Path(path)
    image = ImageReader(str(path))
    iw, ih = image.getSize()
    scale = max(w / iw, h / ih)
    dw, dh = iw * scale, ih * scale
    dx, dy = x + (w - dw) / 2, y + (h - dh) / 2
    c.saveState()
    clip = c.beginPath()
    clip.roundRect(x, y, w, h, 14)
    c.clipPath(clip, stroke=0, fill=0)
    c.drawImage(image, dx, dy, dw, dh, preserveAspectRatio=True, mask="auto")
    c.restoreState()
    c.setStrokeColor(HexColor("#C9D8ED"))
    c.setLineWidth(0.9)
    c.roundRect(x, y, w, h, 14, stroke=1, fill=0)


def feature_card(c, x, y, w, h, number, title, text, accent=BLUE):
    shadow_card(c, x, y, w, h)
    c.setFillColor(accent)
    c.roundRect(x + 16, y + h - 39, 32, 23, 11, stroke=0, fill=1)
    c.setFillColor(WHITE)
    c.setFont("SZ-Bold", 8.5)
    c.drawCentredString(x + 32, y + h - 31, number)
    c.setFillColor(INK)
    c.setFont("SZ-Bold", 11)
    c.drawString(x + 58, y + h - 32, title)
    paragraph(c, text, x + 18, y + h - 57, w - 36, size=8.7, color=SLATE, leading=12.2)


def bullet(c, x, y, text, width, color=BLUE, size=9.2):
    c.setFillColor(color)
    c.circle(x + 3, y + 3, 2.8, stroke=0, fill=1)
    return paragraph(c, text, x + 15, y + 7, width - 15, size=size, color=INK, leading=size * 1.35)


def metric(c, x, y, w, value, caption, accent=BLUE):
    c.setFillColor(accent)
    c.setFont("SZ-DisplayBold", 25)
    c.drawString(x, y, value)
    paragraph(c, caption, x, y - 18, w, size=8.5, color=SLATE, leading=11)


def cover(c):
    c.setFillColor(NAVY_2)
    c.rect(0, 0, PW, PH, stroke=0, fill=1)
    c.setFillColor(NAVY)
    c.circle(PW - 20, PH - 25, 214, stroke=0, fill=1)
    c.setFillColor(BLUE)
    c.circle(PW - 98, PH - 33, 116, stroke=0, fill=1)
    c.setFillColor(AZURE)
    c.circle(PW - 205, PH - 96, 38, stroke=0, fill=1)
    logo(c, 50, PH - 77, 38)
    c.setFillColor(WHITE)
    c.setFont("SZ-Bold", 16)
    c.drawString(99, PH - 59, "SchoolZ")
    c.setFillColor(ICE_2)
    c.setFont("SZ", 8.7)
    c.drawString(100, PH - 74, "School ERP Maroc")
    pill(c, "BROCHURE FONCTIONNELLE", 50, 420, fill=HexColor("#183D83"), color=AZURE)
    c.setFillColor(WHITE)
    c.setFont("SZ-DisplayBold", 31)
    c.drawString(50, 372, "Pilotez votre école")
    c.drawString(50, 335, "depuis une seule")
    c.drawString(50, 298, "plateforme")
    paragraph(c, "Admissions, élèves, pédagogie, finance, familles et services scolaires réunis dans une expérience moderne, bilingue et sécurisée.", 50, 263, 335, size=11.3, color=ICE_2, leading=16)
    c.setFillColor(GOLD)
    c.roundRect(50, 177, 272, 34, 17, stroke=0, fill=1)
    c.setFillColor(NAVY_2)
    c.setFont("SZ-Bold", 9.5)
    c.drawCentredString(186, 189, "VERSION DE DEMONSTRATION - JALONS 0 A 23")
    shadow_card(c, 430, 91, 362, 388, fill=WHITE, radius=22, stroke=HexColor("#35538A"))
    screenshot(c, SCREENS / "admin-dashboard.png", 445, 107, 332, 356)
    c.setFillColor(ICE_2)
    c.setFont("SZ", 8.4)
    c.drawString(50, 60, "Document de présentation destiné aux directions d'établissements scolaires privés")
    c.setFillColor(MUTED)
    c.drawRightString(PW - 50, 60, "Août 2026")
    c.showPage()


def overview(c):
    page_base(c, 2, "LA PROMESSE SCHOOLZ", "Une vue unifiée de l'établissement", "SchoolZ centralise l'administration, la scolarité et la relation familles afin de réduire les doubles saisies et d'améliorer le pilotage quotidien.")
    metrics = [
        ("23", "jalons fonctionnels réunis dans une première version cohérente", BLUE),
        ("18", "rôles de base complétés par des permissions fines", AZURE),
        ("FR / AR", "interface bilingue avec affichage RTL", GOLD),
        ("360°", "dossier élève reliant scolarité, finance et services", CORAL),
    ]
    for idx, (value, caption, accent) in enumerate(metrics):
        x = 48 + idx * 190
        shadow_card(c, x, 355, 174, 105)
        metric(c, x + 18, 417, 138, value, caption, accent)
    c.setFillColor(INK)
    c.setFont("SZ-DisplayBold", 18)
    c.drawString(48, 318, "Quatre résultats concrets pour la direction")
    pillars = [
        ("01", "Une donnée fiable", "Un dossier central par personne, des historiques conservés et une isolation stricte entre établissements."),
        ("02", "Des équipes coordonnées", "Direction, administration, enseignants et familles travaillent sur les mêmes informations publiées."),
        ("03", "Des revenus mieux suivis", "Factures, paiements, échéanciers, relances, reçus et services facturables dans un ledger cohérent."),
        ("04", "Une expérience moderne", "Portails web installables, écrans responsives, français/arabe et parcours adaptés à chaque profil."),
    ]
    for idx, item in enumerate(pillars):
        row, col = divmod(idx, 2)
        feature_card(c, 48 + col * 386, 175 - row * 116, 362, 98, *item, accent=BLUE if col == 0 else AZURE)
    c.showPage()


def admin_governance(c):
    page_base(c, 3, "ADMINISTRATION ET GOUVERNANCE", "La direction garde le contrôle", "Une base structurée pour gérer les accès, l'organisation, les campus, les années scolaires et les dossiers élèves.")
    screenshot(c, SCREENS / "admin-dashboard.png", 366, 176, 428, 241)
    pill(c, "CAPTURE REELLE DE LA DEMO", 382, 392, fill=WHITE, color=BLUE)
    c.setFillColor(INK)
    c.setFont("SZ-DisplayBold", 17)
    c.drawString(48, 393, "Fonctions clés")
    bullets = [
        "Utilisateurs, rôles, permissions fines, verrouillage et journal d'audit.",
        "Organisation multi-campus, paramètres d'école, cycles, niveaux, salles et classes.",
        "Annuaire des élèves, foyers, responsables, contacts d'urgence et dossier 360°.",
        "Archivage sans suppression de l'historique scolaire ou financier.",
        "Import CSV/XLSX avec mapping, prévisualisation, validation ligne par ligne et rapport d'erreurs.",
    ]
    y = 360
    for item in bullets:
        y = bullet(c, 48, y, item, 290, size=9.2) - 10
    shadow_card(c, 366, 78, 428, 76, fill=NAVY)
    c.setFillColor(WHITE)
    c.setFont("SZ-Bold", 10.5)
    c.drawString(389, 126, "Bénéfice direction")
    paragraph(c, "Moins de fichiers dispersés, des responsabilités explicites et une traçabilité continue des opérations sensibles.", 389, 106, 377, size=9.2, color=ICE_2, leading=13)
    c.showPage()


def admissions(c):
    page_base(c, 4, "ADMISSIONS ET INSCRIPTIONS", "Du premier contact à l'élève activé", "SchoolZ structure le recrutement, la constitution du dossier et l'inscription sans rupture entre les équipes et la famille.")
    stages = [
        ("01", "Prospect", "Source du lead, campagne, visite et suivi commercial."),
        ("02", "Candidature", "Compte parent, enfant, dossier et pièces justificatives."),
        ("03", "Décision", "Revue, entretien, test, décision et offre d'inscription."),
        ("04", "Inscription", "Contrat, services, signature, frais et affectation de classe."),
        ("05", "Activation", "Création atomique du foyer, de l'élève et de l'inscription."),
    ]
    y = 381
    for idx, (num, title, text) in enumerate(stages):
        x = 48 + idx * 151
        c.setFillColor(BLUE if idx < 4 else GOLD)
        c.circle(x + 20, y + 17, 19, stroke=0, fill=1)
        c.setFillColor(WHITE if idx < 4 else NAVY_2)
        c.setFont("SZ-Bold", 8.5)
        c.drawCentredString(x + 20, y + 14, num)
        if idx < 4:
            c.setStrokeColor(ICE_2)
            c.setLineWidth(2)
            c.line(x + 41, y + 17, x + 143, y + 17)
        c.setFillColor(INK)
        c.setFont("SZ-Bold", 10.5)
        c.drawString(x, y - 18, title)
        paragraph(c, text, x, y - 38, 130, size=8.4, color=SLATE, leading=11.2)
    c.setFillColor(INK)
    c.setFont("SZ-DisplayBold", 17)
    c.drawString(48, 260, "Une gestion conçue pour la réalité des écoles privées")
    cards = [
        ("CRM admissions", "Pipeline à 11 états, Kanban, campagnes, taux de conversion et dossiers incomplets."),
        ("Portail parent", "Création de compte, ajout d'enfant, dépôt sécurisé des pièces et suivi du statut."),
        ("Réinscription", "Parcours annuel reprenable avec données famille préremplies et services actualisés."),
        ("Contrôle administratif", "Classes compatibles, complétude du dossier, affectation et activation par permission."),
    ]
    for idx, (title, text) in enumerate(cards):
        row, col = divmod(idx, 2)
        feature_card(c, 48 + col * 386, 145 - row * 110, 362, 92, f"0{idx+1}", title, text, accent=BLUE if col == 0 else AZURE)
    c.showPage()


def academics(c):
    page_base(c, 5, "SCOLARITE ET PEDAGOGIE", "Le quotidien scolaire, sans silos", "Présences, emplois du temps, notes, devoirs et bulletins partagent la même structure académique et les mêmes élèves.")
    features = [
        ("01", "Présences et vie scolaire", "Appel rapide, absence, retard, départ anticipé, justification parent, validation administrative, incidents et statistiques."),
        ("02", "Emploi du temps", "Construction manuelle, conflits bloquants, disponibilités, contraintes, preview d'optimisation puis publication."),
        ("03", "Notes et compétences", "Politiques par cycle, coefficients, historique de correction, compétences, calcul de bulletins et classement optionnel."),
        ("04", "Cahier de texte", "Cours, devoirs, échéances, pièces jointes, publication et suivi A faire/Fait côté famille."),
        ("05", "Bulletins versionnés", "PDF français, arabe ou bilingue, publication immuable, empreinte SHA-256 et historique des versions."),
        ("06", "Dossier élève 360°", "Identité, famille, scolarité, absences, notes, documents, finance, transport et historique au même endroit."),
    ]
    for idx, item in enumerate(features):
        row, col = divmod(idx, 3)
        feature_card(c, 48 + col * 252, 291 - row * 151, 232, 130, *item, accent=[BLUE, AZURE, GOLD][col])
    c.showPage()


def finance(c):
    page_base(c, 6, "FINANCE ET RECOUVREMENT", "Une vision claire des revenus de l'école", "SchoolZ relie les frais de scolarité et les services aux comptes familles, avec une traçabilité financière conçue pour durer.")
    c.setFillColor(NAVY)
    c.roundRect(48, 314, 746, 108, 18, stroke=0, fill=1)
    metrics = [
        ("39 500 MAD", "facture de démonstration"),
        ("38 000 MAD", "paiement ventilé"),
        ("1 500 MAD", "reste à recouvrer"),
        ("66,67 %", "taux de l'échéancier démo"),
    ]
    for idx, (value, caption) in enumerate(metrics):
        x = 72 + idx * 181
        c.setFillColor(GOLD if idx in (0, 2) else AZURE)
        c.setFont("SZ-DisplayBold", 20)
        c.drawString(x, 373, value)
        paragraph(c, caption, x, 350, 150, size=8.6, color=ICE_2)
    cards = [
        ("01", "Facturation", "Catalogue de services, factures en brouillon puis immuables, numérotation et lignes détaillées."),
        ("02", "Encaissements", "Paiements multi-factures, allocations, reçus uniques et annulation par écritures inverses."),
        ("03", "Recouvrement", "Echéanciers, retards, tranches J-3/J+1/J+7/J+15 et prévisualisation des relances."),
        ("04", "Services intégrés", "Transport, cantine, garderie et activités peuvent générer automatiquement leurs charges."),
        ("05", "Portail famille", "Ledger, facture, reçu, échéancier, français/arabe et préparation d'un paiement sandbox."),
        ("06", "Traçabilité", "Aucune suppression physique d'un paiement validé ; ledger et audit append-only."),
    ]
    for idx, item in enumerate(cards):
        row, col = divmod(idx, 3)
        feature_card(c, 48 + col * 252, 176 - row * 112, 232, 95, *item, accent=[BLUE, AZURE, GOLD][col])
    c.showPage()


def portals(c):
    page_base(c, 7, "PORTAILS PROFESSEUR ET FAMILLE", "La bonne information, pour le bon profil", "Chaque utilisateur voit les données et les actions qui correspondent à son rôle, sans recréer une seconde source de vérité.")
    screenshot(c, SCREENS / "teacher-portal.png", 48, 205, 354, 199)
    screenshot(c, SCREENS / "family-portal.png", 440, 205, 354, 199)
    pill(c, "PROFESSEUR", 62, 382, fill=NAVY, color=WHITE)
    pill(c, "FAMILLE", 454, 382, fill=NAVY, color=WHITE)
    c.setFillColor(INK)
    c.setFont("SZ-Bold", 11)
    c.drawString(48, 174, "Une journée de travail simplifiée")
    teacher = [
        "Cours du jour, salle, classe et progression.",
        "Raccourcis vers appel, notes, devoir et observation.",
        "Actions précontextualisées et contrôlées par permission.",
    ]
    y = 151
    for item in teacher:
        y = bullet(c, 48, y, item, 350, size=8.8) - 5
    c.setFillColor(INK)
    c.setFont("SZ-Bold", 11)
    c.drawString(440, 174, "Un espace famille complet")
    family = [
        "Sélecteur d'enfant, synthèse, notes, devoirs et absences.",
        "Bulletins, finance, messages, documents et services.",
        "PWA responsive, français/arabe et contrôle du lien parent-enfant.",
    ]
    y = 151
    for item in family:
        y = bullet(c, 440, y, item, 354, color=AZURE, size=8.8) - 5
    c.showPage()


def operations(c):
    page_base(c, 8, "SERVICES ET OPERATIONS", "Au-delà de la salle de classe", "Les services scolaires sont gérés comme de vrais modules métier, avec capacité, sécurité, suivi et impact financier.")
    modules = [
        ("01", "Crèche et garderie", "Groupes, arrivées/départs, personnes autorisées, journal quotidien, publication et Daily Report parent."),
        ("02", "Transport", "Véhicules, chauffeurs, circuits, arrêts, abonnements, trajets, montée/descente, capacité et ETA."),
        ("03", "Cantine", "Menus, plans, abonnements, présences, allergènes, restrictions protégées et charges automatiques."),
        ("04", "Activités", "Catalogue, coachs, sessions, capacité, liste d'attente, inscription parent et facturation associée."),
    ]
    for idx, item in enumerate(modules):
        x = 48 + idx * 190
        feature_card(c, x, 295, 174, 142, *item, accent=[BLUE, AZURE, GOLD, CORAL][idx])
    c.setFillColor(INK)
    c.setFont("SZ-DisplayBold", 17)
    c.drawString(48, 254, "Pourquoi ces modules comptent commercialement")
    benefits = [
        ("Plus de revenus suivis", "Les services facturables alimentent le compte famille au lieu de rester dans des fichiers séparés."),
        ("Plus de sécurité", "Capacité, autorisations, restrictions et liens familiaux sont vérifiés dans les workflows métier."),
        ("Plus de satisfaction", "Les familles retrouvent l'information dans le même espace que la scolarité et la finance."),
    ]
    for idx, (title, text) in enumerate(benefits):
        x = 48 + idx * 252
        shadow_card(c, x, 91, 232, 126, fill=ICE if idx != 1 else WHITE)
        c.setFillColor([BLUE, AZURE, GOLD][idx])
        c.circle(x + 25, 188, 9, stroke=0, fill=1)
        c.setFillColor(INK)
        c.setFont("SZ-Bold", 10.5)
        c.drawString(x + 43, 184, title)
        paragraph(c, text, x + 18, 158, 196, size=8.8, color=SLATE, leading=12.2)
    c.showPage()


def communication_security(c):
    page_base(c, 9, "COMMUNICATION, DOCUMENTS ET SECURITE", "Des échanges traçables et des accès maîtrisés", "La plateforme encadre les informations sensibles, les documents et les communications entre l'école et les familles.")
    blocks = [
        ("Communications", ["Annonces école, campus ou classe", "Conversations administration/professeur-famille", "Préférences et suivi des livraisons", "Canaux externes disponibles en mode PREVIEW"]),
        ("Documents", ["Catégories, types, versions et expiration", "PDF générés depuis des modèles SchoolZ", "Empreintes SHA-256 et liens temporaires signés", "Partage limité à la famille concernée"]),
        ("Signatures simples", ["Workflow envoyé, vu, accepté puis signé", "Version figée du document", "Signataire, IP, horodatages et événements", "Provider local non qualifié dans la démo"]),
        ("Sécurité et conformité", ["Tenant isolé sur chaque lecture", "BCrypt, JWT court et refresh rotatif HttpOnly", "Détection de rejeu et révocation", "Audit append-only des opérations sensibles"]),
    ]
    for idx, (title, items) in enumerate(blocks):
        row, col = divmod(idx, 2)
        x, y = 48 + col * 386, 280 - row * 174
        shadow_card(c, x, y, 362, 150)
        pill(c, f"0{idx+1}", x + 18, y + 108, fill=ICE, color=BLUE if col == 0 else AZURE, pad=10)
        c.setFillColor(INK)
        c.setFont("SZ-Bold", 12)
        c.drawString(x + 68, y + 116, title)
        yy = y + 88
        for item in items:
            yy = bullet(c, x + 18, yy, item, 325, color=BLUE if col == 0 else AZURE, size=8.4) - 3
    c.showPage()


def matrix(c):
    page_base(c, 10, "CATALOGUE DES FONCTIONNALITES", "Une couverture fonctionnelle large, déjà cohérente", "Cette matrice synthétise les capacités principales disponibles dans la démonstration M23.")
    columns = [
        ("Administration", ["Utilisateurs et rôles", "Organisation et campus", "Structure académique", "Elèves et familles", "Import CSV/XLSX", "Audit et sécurité"]),
        ("Scolarité", ["Admissions CRM", "Inscription/réinscription", "Présences et discipline", "Emploi du temps", "Notes et compétences", "Devoirs et bulletins"]),
        ("Gestion", ["Facturation", "Paiements et reçus", "Echéanciers", "Recouvrement", "Documents", "Communications"]),
        ("Services", ["Portail professeur", "Portail famille", "Crèche et garderie", "Transport", "Cantine", "Activités"]),
    ]
    for idx, (title, items) in enumerate(columns):
        x = 48 + idx * 190
        shadow_card(c, x, 126, 174, 309)
        c.setFillColor([BLUE, AZURE, GOLD, CORAL][idx])
        c.roundRect(x, 385, 174, 50, 16, stroke=0, fill=1)
        c.setFillColor(WHITE if idx != 2 else NAVY_2)
        c.setFont("SZ-Bold", 11)
        c.drawString(x + 18, 405, title)
        y = 352
        for item in items:
            c.setFillColor(ICE if idx != 2 else HexColor("#FFF7E7"))
            c.roundRect(x + 14, y - 16, 146, 31, 10, stroke=0, fill=1)
            c.setFillColor([BLUE, AZURE, HexColor("#B47715"), CORAL][idx])
            c.circle(x + 27, y, 3, stroke=0, fill=1)
            c.setFillColor(INK)
            c.setFont("SZ", 8.8)
            c.drawString(x + 38, y - 3, item)
            y -= 43
    c.setFillColor(NAVY)
    c.roundRect(48, 64, 746, 42, 14, stroke=0, fill=1)
    c.setFillColor(ICE_2)
    c.setFont("SZ", 9)
    c.drawCentredString(PW / 2, 80, "Toutes les interfaces clés sont responsives et disponibles en français et en arabe avec prise en charge RTL.")
    c.showPage()


def close(c):
    page_base(c, 11, "POURQUOI UNE ECOLE CHOISIRAIT SCHOOLZ", "Une plateforme à adapter à votre organisation", "La démonstration montre un socle complet. Le passage en production consiste à configurer les règles de l'école et à brancher les fournisseurs externes retenus.", dark=True)
    outcomes = [
        ("01", "Centraliser", "Remplacer les fichiers isolés par une source de vérité partagée."),
        ("02", "Automatiser", "Faire circuler l'information entre admissions, scolarité, finance et services."),
        ("03", "Sécuriser", "Limiter chaque accès au bon établissement, au bon rôle et à la bonne famille."),
        ("04", "Fidéliser", "Offrir aux enseignants et aux parents une expérience claire et moderne."),
    ]
    for idx, (num, title, text) in enumerate(outcomes):
        x = 48 + idx * 190
        shadow_card(c, x, 286, 174, 141, fill=HexColor("#122F67"), stroke=HexColor("#274B89"))
        c.setFillColor(AZURE if idx != 2 else GOLD)
        c.setFont("SZ-Bold", 9)
        c.drawString(x + 17, 395, num)
        c.setFillColor(WHITE)
        c.setFont("SZ-DisplayBold", 15)
        c.drawString(x + 17, 367, title)
        paragraph(c, text, x + 17, 340, 140, size=8.8, color=ICE_2, leading=12.5)
    c.setFillColor(WHITE)
    c.setFont("SZ-DisplayBold", 17)
    c.drawString(48, 240, "Etapes d'un projet école")
    steps = [
        ("1", "Cadrage", "campus, rôles, cycles, finance et services"),
        ("2", "Configuration", "règles, modèles, langues et données initiales"),
        ("3", "Intégrations", "paiement, SMS/e-mail, GPS, stockage et signature"),
        ("4", "Déploiement", "formation, recette, migration et accompagnement"),
    ]
    for idx, (num, title, text) in enumerate(steps):
        x = 48 + idx * 190
        c.setFillColor(BLUE)
        c.circle(x + 15, 190, 15, stroke=0, fill=1)
        c.setFillColor(WHITE)
        c.setFont("SZ-Bold", 9)
        c.drawCentredString(x + 15, 187, num)
        c.setFillColor(WHITE)
        c.setFont("SZ-Bold", 10)
        c.drawString(x + 39, 195, title)
        paragraph(c, text, x + 39, 177, 125, size=8.2, color=ICE_2, leading=11)
    c.setFillColor(GOLD)
    c.roundRect(48, 72, 746, 55, 18, stroke=0, fill=1)
    c.setFillColor(NAVY_2)
    c.setFont("SZ-Bold", 12)
    c.drawString(70, 101, "SchoolZ - une base solide pour moderniser la gestion d'un établissement scolaire privé")
    c.setFont("SZ", 8.7)
    c.drawString(70, 84, "Limites démo explicites : paiement sandbox, canaux externes en preview, GPS local et signature non qualifiée.")
    c.showPage()


def generate():
    required = [SCREENS / "admin-dashboard.png", SCREENS / "teacher-portal.png", SCREENS / "family-portal.png"]
    missing = [str(p) for p in required if not p.exists()]
    if missing:
        raise FileNotFoundError(f"Captures manquantes: {missing}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    fonts()
    c = canvas.Canvas(str(OUT), pagesize=landscape(A4), pageCompression=1)
    c.setTitle("SchoolZ - Brochure commerciale des fonctionnalités")
    c.setAuthor("SchoolZ")
    c.setSubject("Présentation des fonctionnalités administratives et de gestion pour les écoles")
    cover(c)
    overview(c)
    admin_governance(c)
    admissions(c)
    academics(c)
    finance(c)
    portals(c)
    operations(c)
    communication_security(c)
    matrix(c)
    close(c)
    c.save()
    print(OUT)


if __name__ == "__main__":
    generate()
