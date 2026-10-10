from pathlib import Path

from reportlab.lib.colors import HexColor, white
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf" / "schoolz-guide-demonstration-profils.pdf"
PAGE_W, PAGE_H = landscape(A4)

NAVY = HexColor("#172554")
DEEP = HexColor("#0F2F6D")
BLUE = HexColor("#2563EB")
SKY = HexColor("#38BDF8")
PALE = HexColor("#EFF6FF")
PALE_2 = HexColor("#DBEAFE")
INK = HexColor("#172033")
MUTED = HexColor("#56657A")
LINE = HexColor("#D9E5F6")
WHITE = HexColor("#FFFFFF")
GOLD = HexColor("#F3C66A")
SUCCESS = HexColor("#1D4ED8")


def install_fonts():
    pdfmetrics.registerFont(TTFont("SchoolZ", r"C:\Windows\Fonts\arial.ttf"))
    pdfmetrics.registerFont(TTFont("SchoolZ-Bold", r"C:\Windows\Fonts\arialbd.ttf"))


def wrap(text, font, size, width):
    words = text.split()
    lines, current = [], ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if pdfmetrics.stringWidth(candidate, font, size) <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def text_block(c, x, y, width, text, size=10, color=INK, leading=None, font="SchoolZ"):
    leading = leading or size * 1.38
    c.setFillColor(color)
    c.setFont(font, size)
    for line in wrap(text, font, size, width):
        c.drawString(x, y, line)
        y -= leading
    return y


def bullet_list(c, x, y, width, items, size=9.3, color=INK, gap=7):
    for item in items:
        c.setFillColor(SKY)
        c.circle(x + 3, y + 2, 2.4, stroke=0, fill=1)
        y = text_block(c, x + 13, y + 5, width - 13, item, size=size, color=color)
        y -= gap
    return y


def round_rect(c, x, y, w, h, fill, radius=14, stroke=None):
    c.setFillColor(fill)
    if stroke:
        c.setStrokeColor(stroke)
        c.setLineWidth(0.8)
        c.roundRect(x, y, w, h, radius, stroke=1, fill=1)
    else:
        c.roundRect(x, y, w, h, radius, stroke=0, fill=1)


def logo(c, x, y, size=28, light=False):
    c.setFillColor(SKY if not light else WHITE)
    c.roundRect(x, y, size, size, 8, stroke=0, fill=1)
    c.setFillColor(BLUE if not light else SKY)
    c.setFont("SchoolZ-Bold", size * 0.48)
    c.drawCentredString(x + size / 2, y + size * 0.30, "S")
    c.setFillColor(WHITE if not light else NAVY)
    c.setFont("SchoolZ-Bold", size * 0.23)
    c.drawCentredString(x + size / 2, y + size * 0.11, "Z")


def chrome(c, page, title, subtitle):
    c.setFillColor(HexColor("#F8FBFF"))
    c.rect(0, 0, PAGE_W, PAGE_H, stroke=0, fill=1)
    c.setFillColor(NAVY)
    c.rect(0, PAGE_H - 74, PAGE_W, 74, stroke=0, fill=1)
    c.setFillColor(DEEP)
    c.circle(PAGE_W - 42, PAGE_H - 4, 90, stroke=0, fill=1)
    c.setFillColor(BLUE)
    c.circle(PAGE_W - 115, PAGE_H + 3, 52, stroke=0, fill=1)
    logo(c, 42, PAGE_H - 56, 28, light=True)
    c.setFillColor(WHITE)
    c.setFont("SchoolZ-Bold", 12)
    c.drawString(79, PAGE_H - 39, "SchoolZ")
    c.setFillColor(PALE_2)
    c.setFont("SchoolZ", 8.5)
    c.drawString(79, PAGE_H - 52, "Guide de demonstration - M23")
    c.setFillColor(INK)
    c.setFont("SchoolZ-Bold", 24)
    c.drawString(42, PAGE_H - 112, title)
    c.setFillColor(MUTED)
    c.setFont("SchoolZ", 10.5)
    c.drawString(42, PAGE_H - 132, subtitle)
    c.setStrokeColor(LINE)
    c.setLineWidth(0.8)
    c.line(42, 42, PAGE_W - 42, 42)
    c.setFillColor(MUTED)
    c.setFont("SchoolZ", 8)
    c.drawString(42, 25, "SchoolZ - guide local de prise en main")
    c.drawRightString(PAGE_W - 42, 25, f"{page}/10")


def card(c, x, y, w, h, title, body=None, accent=BLUE, bullets=None, label=None):
    round_rect(c, x, y, w, h, WHITE, 14, LINE)
    c.setFillColor(accent)
    c.roundRect(x, y + h - 7, w, 7, 8, stroke=0, fill=1)
    if label:
        c.setFillColor(PALE)
        label_w = min(w - 36, max(74, pdfmetrics.stringWidth(label.upper(), "SchoolZ-Bold", 7.5) + 24))
        c.roundRect(x + 18, y + h - 38, label_w, 18, 9, stroke=0, fill=1)
        c.setFillColor(BLUE)
        c.setFont("SchoolZ-Bold", 7.5)
        c.drawCentredString(x + 18 + label_w / 2, y + h - 32, label.upper())
        title_y = y + h - 58
    else:
        title_y = y + h - 31
    c.setFillColor(INK)
    c.setFont("SchoolZ-Bold", 12)
    c.drawString(x + 18, title_y, title)
    cursor = title_y - 20
    if body:
        cursor = text_block(c, x + 18, cursor, w - 36, body, size=9.1, color=MUTED)
    if bullets:
        bullet_list(c, x + 18, cursor - 1, w - 36, bullets, size=8.7, color=INK, gap=4)


def route(c, x, y, label, path):
    c.setFillColor(PALE)
    c.roundRect(x, y - 13, 150, 20, 8, stroke=0, fill=1)
    c.setFillColor(BLUE)
    c.setFont("SchoolZ-Bold", 8)
    c.drawString(x + 8, y - 6, label)
    c.setFillColor(MUTED)
    c.setFont("SchoolZ", 7.8)
    c.drawRightString(x + 142, y - 6, path)


def cover(c):
    c.setFillColor(NAVY)
    c.rect(0, 0, PAGE_W, PAGE_H, stroke=0, fill=1)
    c.setFillColor(DEEP)
    c.circle(PAGE_W - 38, PAGE_H - 15, 210, stroke=0, fill=1)
    c.setFillColor(BLUE)
    c.circle(PAGE_W - 134, PAGE_H - 76, 118, stroke=0, fill=1)
    c.setFillColor(SKY)
    c.circle(PAGE_W - 227, PAGE_H - 142, 44, stroke=0, fill=1)
    logo(c, 55, PAGE_H - 98, 48, light=True)
    c.setFillColor(WHITE)
    c.setFont("SchoolZ-Bold", 20)
    c.drawString(117, PAGE_H - 76, "SchoolZ")
    c.setFillColor(PALE_2)
    c.setFont("SchoolZ", 10)
    c.drawString(118, PAGE_H - 94, "School ERP Maroc - demonstration locale")
    c.setFillColor(WHITE)
    c.setFont("SchoolZ-Bold", 39)
    c.drawString(55, 330, "Votre parcours de prise en main")
    c.setFillColor(PALE_2)
    c.setFont("SchoolZ", 17)
    c.drawString(55, 297, "Une presentation par profil pour explorer la version M23.")
    c.setFillColor(HexColor("#6EE7F9"))
    c.roundRect(55, 241, 218, 31, 15, stroke=0, fill=1)
    c.setFillColor(NAVY)
    c.setFont("SchoolZ-Bold", 10)
    c.drawCentredString(164, 252, "Administrateur  |  Professeur  |  Famille")
    round_rect(c, 55, 90, 560, 102, HexColor("#102D65"), 18)
    c.setFillColor(WHITE)
    c.setFont("SchoolZ-Bold", 12)
    c.drawString(79, 162, "Ce guide contient")
    bullet_list(c, 79, 140, 490, [
        "les identifiants de demonstration et les bonnes pratiques de navigation ;",
        "les actions a realiser pour chaque profil et les resultats attendus ;",
        "cinq scenarios de bout en bout entre back-office, professeur et famille.",
    ], size=10, color=PALE_2, gap=4)
    c.setFillColor(PALE_2)
    c.setFont("SchoolZ", 9)
    c.drawString(55, 45, "Edition locale - a conserver comme aide-memoire de demonstration")
    c.showPage()


def page_start(c, page, title, subtitle):
    chrome(c, page, title, subtitle)


def page_profiles(c):
    page_start(c, 2, "Demarrer la demonstration", "Acces local, comptes et ordre conseille pour une visite fluide.")
    card(c, 42, 350, 238, 105, "1. Ouvrir la demo", bullets=[
        "Adresse : http://localhost:8088",
        "Tenant commun : ecole-demo",
        "Si la palette ne se met pas a jour : Ctrl + F5.",
    ], label="point de depart")
    card(c, 302, 350, 238, 105, "2. Choisir un profil", bullets=[
        "Administrateur : vue complete du back-office.",
        "Professeur : journee, appel, notes et devoirs.",
        "Famille : suivi des enfants et services parents.",
    ], accent=SKY, label="3 experiences")
    card(c, 562, 350, 238, 105, "3. Tester sans risque", bullets=[
        "La demo utilise des donnees locales previsibles.",
        "Le paiement sandbox ne debite jamais une carte.",
        "Les comptes et secrets sont reserves au developpement.",
    ], accent=DEEP, label="important")
    creds = [
        ("Administrateur", "admin@schoolz.ma", "SchoolZDemo2026!", "Connexion principale"),
        ("Professeur", "prof.math@schoolz.ma", "SchoolZTeacher2026!", "Bouton Tester le portail professeur"),
        ("Famille", "parent.demo@schoolz.ma", "SchoolZParent2026!", "Depuis /family, /admissions ou /finance"),
    ]
    x, y = 42, 164
    for idx, (profile, email, password, entry) in enumerate(creds):
        w = 246
        card(c, x + idx * 256, y, w, 147, profile, accent=[BLUE, SKY, DEEP][idx], label="compte demo")
        c.setFillColor(MUTED); c.setFont("SchoolZ", 8.5)
        c.drawString(x + idx * 256 + 18, y + 80, "E-mail")
        c.setFillColor(INK); c.setFont("SchoolZ-Bold", 9.2)
        c.drawString(x + idx * 256 + 18, y + 65, email)
        c.setFillColor(MUTED); c.setFont("SchoolZ", 8.5)
        c.drawString(x + idx * 256 + 18, y + 45, "Mot de passe")
        c.setFillColor(INK); c.setFont("SchoolZ-Bold", 9.2)
        c.drawString(x + idx * 256 + 18, y + 30, password)
        c.setFillColor(BLUE); c.setFont("SchoolZ", 7.8)
        c.drawString(x + idx * 256 + 18, y + 13, entry)
    c.showPage()


def page_admin_overview(c):
    page_start(c, 3, "Profil administrateur", "Le compte administrateur reunit la direction et les equipes back-office dans la demo.")
    card(c, 42, 327, 238, 134, "Objectif de la visite", bullets=[
        "Piloter les donnees de l'etablissement.",
        "Suivre admissions, scolarite, finance et operations.",
        "Publier des informations visibles par les familles.",
    ], label="mission")
    card(c, 302, 327, 238, 134, "Point d'entree", bullets=[
        "Se connecter avec admin@schoolz.ma.",
        "Ouvrir le tableau de bord /app/dashboard.",
        "Utiliser le menu bleu lateral pour parcourir les modules.",
    ], accent=SKY, label="demarrage")
    card(c, 562, 327, 238, 134, "Bon repere", bullets=[
        "Les donnees de demonstration sont deja liees entre elles.",
        "Gardez une seconde session famille pour voir les effets publies.",
        "Les actions sensibles affichent une confirmation ou une trace.",
    ], accent=DEEP, label="astuce")
    c.setFillColor(INK); c.setFont("SchoolZ-Bold", 14)
    c.drawString(42, 286, "Carte rapide des modules back-office")
    groups = [
        ("Pilotage", [("Tableau de bord", "/app/dashboard"), ("Utilisateurs", "/app/users"), ("Organisation", "/app/organization")]),
        ("Scolarite", [("Eleves", "/app/students"), ("Presences", "/app/attendance"), ("Notes", "/app/grading"), ("Emploi du temps", "/app/timetable")]),
        ("Operations", [("Finance", "/app/finance"), ("Communications", "/app/communications"), ("Documents", "/app/documents"), ("Transport", "/app/transport")]),
        ("Services", [("Creche", "/app/childcare"), ("Cantine", "/app/cafeteria"), ("Activites", "/app/activities"), ("Admissions", "/app/admissions")]),
    ]
    for col, (name, routes) in enumerate(groups):
        x = 42 + col * 192
        c.setFillColor(BLUE); c.setFont("SchoolZ-Bold", 10)
        c.drawString(x, 259, name)
        yy = 235
        for label, path in routes:
            route(c, x, yy, label, path); yy -= 31
    c.showPage()


def page_admin_journey(c):
    page_start(c, 4, "Parcours administrateur", "Un itineraire de 35 a 45 minutes pour parcourir les donnees les plus parlantes.")
    steps = [
        ("01", "Admissions et inscriptions", "Ouvrir Admissions CRM puis Inscriptions. Explorer le dossier accepte, l'offre et l'affectation de classe. Resultat : l'eleve devient activable sans ressaisie."),
        ("02", "Vie scolaire", "Dans Presences, observer present, absent et retard. Dans Emploi du temps, comparer les cours manuels et les propositions de l'optimiseur sans appliquer une proposition par erreur."),
        ("03", "Pedagogie", "Dans Notes & competences, modifier une note avec motif, consulter l'historique puis recalculer un brouillon de bulletin. Dans Cahier de texte, voir cours, devoirs et pieces jointes."),
        ("04", "Finance", "Consulter la facture de 39 500 MAD, le paiement de 38 000 MAD, le recu et le reste de 1 500 MAD. Puis ouvrir le cockpit Recouvrement et ECH-DEMO-2026-0001."),
        ("05", "Operations et relation famille", "Tester communications, documents, signatures, transport, cantine et activites. Chaque module dispose ensuite d'une vue ou d'un effet cote famille."),
    ]
    y = 438
    for n, title, body in steps:
        c.setFillColor(BLUE); c.circle(66, y - 2, 17, stroke=0, fill=1)
        c.setFillColor(WHITE); c.setFont("SchoolZ-Bold", 9)
        c.drawCentredString(66, y - 6, n)
        round_rect(c, 96, y - 59, 704, 74, WHITE, 14, LINE)
        c.setFillColor(INK); c.setFont("SchoolZ-Bold", 11)
        c.drawString(117, y - 8, title)
        text_block(c, 117, y - 26, 650, body, size=9.2, color=MUTED)
        y -= 84
    c.showPage()


def page_teacher(c):
    page_start(c, 5, "Profil professeur", "Une journee enseignant contextualisee : cours, appel, notes et devoirs.")
    card(c, 42, 344, 237, 118, "Connexion", bullets=[
        "Depuis /login, choisir Tester le portail professeur.",
        "Ou utiliser prof.math@schoolz.ma.",
        "Mot de passe : SchoolZTeacher2026!", 
    ], label="acces")
    card(c, 302, 344, 237, 118, "Ecran a ouvrir", bullets=[
        "http://localhost:8088/app/teacher?date=2026-09-01",
        "La page regroupe le planning et les raccourcis autorises.",
        "La date peut etre changee pour explorer une autre journee.",
    ], accent=SKY, label="point de depart")
    card(c, 562, 344, 237, 118, "Fil conducteur", bullets=[
        "Verifier le cours de mathematiques et sa classe.",
        "Saisir ou verifier l'appel du groupe.",
        "Ajouter une note puis un devoir dans le bon contexte.",
    ], accent=DEEP, label="a tester")
    c.setFillColor(INK); c.setFont("SchoolZ-Bold", 14)
    c.drawString(42, 300, "Parcours conseille - 15 minutes")
    teacher_steps = [
        ("1", "Lire la journee", "Verifier l'identite professeur, les cours du jour, les compteurs et la prochaine seance."),
        ("2", "Faire l'appel", "Ouvrir l'action associee au cours. Marquer la presence ou l'absence en restant dans le contexte classe/matiere/date."),
        ("3", "Evaluer", "Ajouter ou consulter une note. Les controles de permission et de tenant sont rejoues cote serveur."),
        ("4", "Donner un devoir", "Creer un devoir lie a la seance. Il devient ensuite visible dans le calendrier parent de l'eleve concerne."),
    ]
    y = 263
    for n, title, body in teacher_steps:
        c.setFillColor(PALE_2); c.circle(61, y + 3, 15, stroke=0, fill=1)
        c.setFillColor(BLUE); c.setFont("SchoolZ-Bold", 10); c.drawCentredString(61, y, n)
        c.setFillColor(INK); c.setFont("SchoolZ-Bold", 11); c.drawString(88, y + 3, title)
        text_block(c, 88, y - 14, 665, body, size=9.2, color=MUTED)
        y -= 57
    c.showPage()


def page_family(c):
    page_start(c, 6, "Profil famille", "Le portail parent rassemble les informations publiees pour les enfants rattaches au compte.")
    card(c, 42, 347, 238, 115, "Connexion parent", bullets=[
        "Ouvrir /family.",
        "Tenant : ecole-demo.",
        "parent.demo@schoolz.ma / SchoolZParent2026!", 
    ], label="acces")
    card(c, 302, 347, 238, 115, "A observer", bullets=[
        "Basculer entre Yasmine et Amine.",
        "Choisir le 1er septembre 2026.",
        "Verifier que chaque enfant ne voit que ses propres donnees.",
    ], accent=SKY, label="controle")
    card(c, 562, 347, 238, 115, "Fonctions centrales", bullets=[
        "Emploi du temps, notes, devoirs et absences.",
        "Bulletin telechargeable lorsqu'il est publie.",
        "Vue consolidee, bilingue francais/arabe et RTL.",
    ], accent=DEEP, label="experience")
    c.setFillColor(INK); c.setFont("SchoolZ-Bold", 14)
    c.drawString(42, 302, "Premiere visite - 20 minutes")
    family_steps = [
        ("Portail famille", "/family", "Choisir Yasmine puis Amine, consulter la synthese et le bulletin."),
        ("Absences", "/attendance", "Voir l'absence de Yasmine, la notification et le formulaire de justification."),
        ("Devoirs", "/homework", "Parcourir le calendrier et marquer un devoir comme fait."),
        ("Finance", "/finance", "Consulter facture, recu, echeancier et changer la langue si souhaite."),
        ("Services", "/transport, /cafeteria, /activities", "Verifier l'ETA, les menus et l'inscription a une activite."),
    ]
    y = 266
    for label, path, description in family_steps:
        route(c, 42, y, label, path)
        text_block(c, 214, y - 4, 560, description, size=9.1, color=MUTED)
        y -= 41
    c.showPage()


def page_family_extended(c):
    page_start(c, 7, "Parcours famille et services", "Completer la visite avec les workflows parent, toujours relies aux donnees du back-office.")
    items = [
        ("Admissions", "/admissions", "Retrouver la session parent et suivre le dossier d'admission."),
        ("Inscription", "/enrollment", "Parcourir contrat, famille, services, pieces, signature et frais."),
        ("Paiement sandbox", "/finance", "Utiliser Preparer mon paiement sandbox. La confirmation simule un webhook signe, sans debit bancaire reel."),
        ("Messages et documents", "/messages, /documents, /signatures", "Lire les annonces, consulter les documents partages et signer lorsqu'une demande est publiee."),
        ("Creche", "/childcare", "Consulter le Daily Report publie pour l'enfant concerne."),
    ]
    for index, (label, path, description) in enumerate(items):
        col = index % 3
        row = index // 3
        x = 42 + col * 256
        yy = 272 - row * 126
        card(c, x, yy, 246, 108, label, description, accent=BLUE if col != 1 else SKY, label=path)
    c.setFillColor(PALE); c.roundRect(42, 64, 758, 52, 14, stroke=0, fill=1)
    c.setFillColor(BLUE); c.setFont("SchoolZ-Bold", 10)
    c.drawString(62, 94, "Repere securite")
    text_block(c, 62, 79, 700, "Les portails parent verifient le lien compte-enfant cote serveur. Changer un identifiant dans l'URL ne donne pas acces aux donnees d'une autre famille.", size=9.1, color=INK)
    c.showPage()


def page_cross(c):
    page_start(c, 8, "Scenarios de bout en bout", "Cinq petites demonstrations qui montrent la valeur de SchoolZ entre les profils.")
    scenarios = [
        ("Absence", "Administrateur", "Famille", "Saisir ou verifier l'absence dans /app/attendance, puis ouvrir /attendance et rediger la justification."),
        ("Devoir", "Professeur", "Famille", "Creer un devoir dans le portail professeur, puis verifier sa presence dans /homework et le marquer comme fait."),
        ("Facture", "Administrateur", "Famille", "Observer la facture et le reglement dans /app/finance, puis ouvrir /finance et preparer un paiement sandbox."),
        ("Document", "Administrateur", "Famille", "Partager un PDF depuis /app/documents, puis le retrouver dans /documents et tester la signature simple dans /signatures."),
        ("Activite", "Administrateur", "Famille", "Verifier capacite et session dans /app/activities, inscrire l'enfant depuis /activities et constater la charge Finance associee."),
    ]
    y = 432
    for title, source, target, description in scenarios:
        round_rect(c, 42, y - 57, 758, 74, WHITE, 14, LINE)
        c.setFillColor(BLUE); c.roundRect(59, y - 38, 82, 31, 15, stroke=0, fill=1)
        c.setFillColor(WHITE); c.setFont("SchoolZ-Bold", 9); c.drawCentredString(100, y - 27, title)
        c.setFillColor(INK); c.setFont("SchoolZ-Bold", 9.5); c.drawString(164, y - 18, source)
        c.setStrokeColor(SKY); c.setLineWidth(1.6); c.line(224, y - 22, 286, y - 22)
        c.setFillColor(SKY); c.circle(288, y - 22, 3, stroke=0, fill=1)
        c.setFillColor(INK); c.setFont("SchoolZ-Bold", 9.5); c.drawString(300, y - 18, target)
        text_block(c, 164, y - 38, 598, description, size=8.8, color=MUTED)
        y -= 84
    c.showPage()


def page_checklist(c):
    page_start(c, 9, "Checklist de demonstration", "Cochez cette page au fil de votre visite. Elle tient sur une seule session de test.")
    items = [
        ("Connexion", "Je me connecte avec chacun des trois comptes de demonstration."),
        ("Navigation", "Je visite le tableau de bord et le menu bleu du back-office."),
        ("Administrateur", "Je parcours admissions, presences, notes, emploi du temps et finance."),
        ("Professeur", "Je consulte la journee du 01/09/2026 et les actions contextualisees."),
        ("Famille", "Je bascule entre Yasmine et Amine puis consulte les informations publiees."),
        ("Services", "Je verifie au moins un flux transport, cantine ou activites."),
        ("Scenario", "Je realise un scenario croise : devoir, absence, facture, document ou activite."),
        ("Langues", "Je passe en arabe sur un portail famille et verifie le sens RTL."),
        ("Paiement", "Je teste uniquement le paiement sandbox et verifie le recu genere."),
    ]
    y = 440
    for idx, (title, body) in enumerate(items, 1):
        c.setStrokeColor(BLUE); c.setLineWidth(1.4); c.roundRect(49, y - 8, 15, 15, 3, stroke=1, fill=0)
        c.setFillColor(PALE); c.roundRect(80, y - 13, 92, 25, 12, stroke=0, fill=1)
        c.setFillColor(BLUE); c.setFont("SchoolZ-Bold", 8.5); c.drawCentredString(126, y - 5, f"{idx:02d} - {title}")
        c.setFillColor(MUTED); c.setFont("SchoolZ", 9.4); c.drawString(190, y - 4, body)
        y -= 43
    c.showPage()


def page_reference(c):
    page_start(c, 10, "Aide-memoire", "Les liens essentiels et les points de vigilance de la demonstration locale.")
    card(c, 42, 290, 355, 171, "Liens essentiels", bullets=[
        "Application : http://localhost:8088",
        "Administrateur : /app/dashboard",
        "Professeur : /app/teacher?date=2026-09-01",
        "Famille : /family",
        "Admissions : /admissions - Finance : /finance",
        "Etat systeme : /api/v1/system/status",
        "API : /swagger-ui/index.html",
    ], label="urls")
    card(c, 423, 290, 377, 171, "A retenir", bullets=[
        "Les comptes et mots de passe de ce guide sont uniquement destines au developpement local.",
        "Les integrations externes restent en mode preview ou sandbox dans la demo.",
        "La version M23 est une premiere version fonctionnelle coherente, pas une configuration de production.",
        "Pour relancer la demo : docker compose --env-file .env.example up --build",
    ], accent=SKY, label="notes")
    c.setFillColor(NAVY); c.roundRect(42, 112, 758, 122, 18, stroke=0, fill=1)
    c.setFillColor(WHITE); c.setFont("SchoolZ-Bold", 18)
    c.drawString(68, 191, "Bonne exploration de SchoolZ")
    c.setFillColor(PALE_2); c.setFont("SchoolZ", 10.5)
    c.drawString(68, 166, "Commencez par le profil administrateur, puis passez au professeur et enfin a la famille.")
    c.drawString(68, 146, "C'est le moyen le plus simple de percevoir les liens entre les modules.")
    c.setFillColor(SKY); c.circle(742, 163, 31, stroke=0, fill=1)
    c.setFillColor(NAVY); c.setFont("SchoolZ-Bold", 17); c.drawCentredString(742, 157, "SZ")
    c.showPage()


def generate():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    install_fonts()
    c = canvas.Canvas(str(OUT), pagesize=landscape(A4), pageCompression=1)
    c.setTitle("SchoolZ - Guide de demonstration par profils")
    c.setAuthor("SchoolZ")
    c.setSubject("Parcours de prise en main de la demonstration locale")
    cover(c)
    page_profiles(c)
    page_admin_overview(c)
    page_admin_journey(c)
    page_teacher(c)
    page_family(c)
    page_family_extended(c)
    page_cross(c)
    page_checklist(c)
    page_reference(c)
    c.save()
    print(OUT)


if __name__ == "__main__":
    generate()
