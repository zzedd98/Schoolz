# Modèle de domaine initial

## Agrégats structurants

### Organization et Campus

`Organization` représente le tenant commercial et juridique principal, par exemple un groupe scolaire. `Campus` représente un établissement physique. Un tenant possède un ou plusieurs campus ; les utilisateurs reçoivent une portée organisation et, si nécessaire, une portée campus.

Le jalon 2 matérialise cette frontière : un `identity_tenant` possède exactement une `Organization`, plusieurs `Campus` et un `SchoolSettings`. Les `AcademicYear`, `Term`, `SchoolLevel` et `GradeLevel` sont communs au groupe. Les `PhysicalRoom` et `Classroom` appartiennent à un campus. `CampusGradeOffering` déclare explicitement les niveaux proposés par chaque campus.

Une seule année peut être `ACTIVE` dans un tenant. Une période doit être incluse dans son année et ne peut chevaucher une autre période. Une classe référence une année, un niveau offert, un campus et éventuellement une salle de ce même campus.

### Person

Une personne physique possède exactement un enregistrement `Person` au sein d'un tenant. Ses rôles sont des profils et relations, jamais des copies : `StudentProfile`, `StaffProfile`, `UserAccount`, `GuardianRelationship`, `EmergencyContact` et `FinancialResponsibility`.

La déduplication s'appuie sur des identifiants contrôlés, contacts normalisés et revues humaines. Aucun rapprochement probabiliste ne fusionne automatiquement des personnes.

### Household

`Household` représente la cellule familiale et relie plusieurs responsables et enfants. Le compte financier est principalement porté par la famille afin de permettre factures consolidées, remises fratrie et allocations de paiements multi-enfants.

### Enrollment

`Enrollment` relie un élève, un campus, une année académique et un groupe de classe. Son statut conserve l'historique ; un élève n'est pas supprimé lorsqu'une inscription existe.

Le jalon 3 matérialise ces concepts dans les tables `person`, `person_address`, `person_contact_method`, `household`, `student_profile`, `guardian_profile`, `household_relationship`, `emergency_contact`, `student_enrollment`, `student_identifier`, `person_custom_field`, `person_document` et `student_history`. Les relations structurantes combinent systématiquement l'identifiant avec `tenant_id`.

Le dossier élève est archivé par statut. L'archivage clôture l'inscription active, ajoute un événement historique et conserve les documents, responsables et identifiants. Il n'existe volontairement aucune suppression physique d'un élève ayant un historique.

### DataImportJob

`DataImportJob` porte le type métier, le format source, les en-têtes, le mapping confirmé, les compteurs et le statut du parcours. Chaque `DataImportRow` conserve le numéro réel de la ligne, les données brutes, les valeurs normalisées, les erreurs structurées et éventuellement l'identifiant cible.

Upload et mapping sont persistés avant validation. La validation est sans effet métier. L'exécution de toutes les lignes valides appartient à une seule transaction PostgreSQL : une contrainte devenue fausse entre validation et exécution annule toutes les cibles, puis le job est marqué `FAILED` dans une transaction séparée.

Le jalon 4 matérialise aussi `StaffProfile` avec un matricule unique par tenant. `ImportedLegacyPayment` et `ImportedOpeningBalance` sont des zones de reprise explicites, liées à l'élève et au job source. Leur statut initial `PENDING` interdit de les confondre avec le futur ledger familial ; le module finance devra les rapprocher avant toute reconnaissance comptable.

### Admission

`Prospect` représente un premier contact et son origine marketing. Un compte `AdmissionPortalAccount` peut créer plusieurs `Applicant`, chacun représentant un enfant candidat. Une `Application` relie un candidat à une `AdmissionCampaign`, un campus et un niveau souhaités. Une contrainte empêche deux dossiers du même enfant dans la même campagne.

Le statut courant accélère les vues Kanban, tandis que chaque transition produit un `ApplicationStatus` immuable. Le chemin normal est `NEW_LEAD → CONTACTED → SCHOOL_VISIT → APPLICATION_STARTED → APPLICATION_COMPLETE → TEST_SCHEDULED → REVIEW`, puis une décision vers `ACCEPTED`, `WAITLISTED` ou `REJECTED`. Une offre acceptée mène à `ENROLLED`; le module inscription réalise ensuite la conversion contrôlée vers le dossier élève et l'inscription académique.

`AdmissionStep` configure le parcours d'une campagne. Les pièces exigées sont contrôlées avant soumission. `Visit`, `Interview` et `AdmissionTest` portent les activités planifiées. `AdmissionDecision` est unique par dossier et une acceptation crée un `EnrollmentOffer` daté. Les notifications et frais éventuels restent rattachés au compte parent et au dossier.

### EnrollmentWorkflow

`EnrollmentWorkflow` est un parcours persistant et reprenable, soit `INITIAL` depuis une admission acceptée, soit `RE_ENROLLMENT` depuis un élève déjà lié au compte parent. Il appartient à un tenant, un compte portail, une année et un campus. Son origine est exclusive : application pour l'inscription initiale, élève pour la réinscription.

Le parcours ordonné est `CONTRACT → FAMILY_INFORMATION → SERVICES → DOCUMENTS → SIGNATURE → REGISTRATION_FEE → CLASS_ASSIGNMENT → READY → ACTIVE`. Le contrat est versionné. La confirmation famille conserve un instantané modifiable. Les services matérialisent transport, circuit, cantine, activités et plan `MONTHLY`, `QUARTERLY` ou `ANNUAL`. Les documents possèdent type MIME, taille et SHA-256 ; la signature possède consentement, signataire, IP et empreinte.

L'affectation à une classe n'est possible que dans l'année et le campus du workflow. L'activation est transactionnelle : toutes les conditions doivent être vraies. Une inscription initiale crée le dossier élève complet et un lien parent-élève ; une réinscription réutilise le dossier existant et ajoute l'inscription de l'année cible. Les contraintes uniques empêchent deux workflows pour la même admission ou deux réinscriptions du même élève sur la même année.

### ChildcareProfile et Daily Report

`ChildcareProfile` spécialise un élève pour la petite enfance. Il appartient à un `ChildcareGroup`, définit ses horaires attendus et conserve les consignes repas, biberon, sommeil et toilette. Les personnes de récupération sont explicites : `AUTHORIZED` ou `PROHIBITED`; un départ ne peut référencer qu'une personne autorisée.

`ChildcareDailyLog` représente une journée locale et évolue de `DRAFT` à `PUBLISHED`. Il porte arrivée, départ, personne de récupération, humeur et synthèse. Ses événements horodatés sont typés : repas, biberon, sommeil, change, toilette, activité, humeur, observation, incident, température ou médicament. Après publication, le journal devient immuable et visible dans le portail parent sous forme de `DailyReport`.

`ChildcarePolicy` est configurée par campus. Un médicament ne peut être enregistré que si la politique l'autorise, que l'utilisateur possède `childcare.medication` et, lorsque requis, qu'une référence d'autorisation est fournie. Une température est une mesure manuelle et un incident une observation factuelle : le domaine ne produit ni diagnostic ni prescription.

### AttendanceSession et vie scolaire

`AttendanceSession` représente l'appel d'une classe à une date et un horaire local, avec un enseignant et un libellé de matière. Sa création fige la population active dans des `AttendanceRecord` initialement `PRESENT`. L'enseignant peut les passer à `ABSENT`, `LATE` ou `EARLY_DEPARTURE` tant que la séance est `OPEN`. La soumission est irréversible et matérialise un `Absence`, `LateArrival` ou `EarlyDeparture` selon chaque état.

Une absence démarre `UNEXCUSED`. Le parent lié peut créer un `ExcusedAbsence` `PENDING` avec motif, commentaire et éventuelle clé objet. Seule l'administration décide `APPROVED` ou `REJECTED`; l'approbation rend l'absence `EXCUSED`, le rejet autorise une nouvelle soumission. Chaque création et décision produit une notification parent tenant-scoped.

`DisciplinaryIncident` décrit un fait daté et peut recevoir plusieurs `Sanction`. `TeacherObservation` conserve une observation positive, neutre ou préoccupante, avec un choix explicite de visibilité parent. Ces objets restent séparés de l'appel pour éviter qu'une appréciation comportementale ne modifie les faits de présence.

### Schedule et Lesson

`Subject` porte une matière tenant-scoped réutilisable. `Schedule` représente un planning hebdomadaire d'un campus et d'une année, borné par des dates et évoluant de `DRAFT` à `PUBLISHED`. Ses `Period` ordonnent la journée et ses `TimeSlot` matérialisent le couple jour/période. Les pauses existent comme périodes non enseignées et ne reçoivent aucun cours.

`TeacherAssignment` relie une classe, une matière et un enseignant avec un volume hebdomadaire, un type de salle et des équipements requis. `Lesson` place une unité de cette affectation sur un créneau et dans une salle physique. Un créneau ne peut occuper deux fois le même enseignant, la même salle ou la même classe ; capacité, type et équipements sont revalidés à chaque écriture.

`TeacherAvailability` et `RoomAvailability` déclarent disponibilité, préférence ou indisponibilité. `Constraint` est ciblée et configurée : maximum quotidien, maximum consécutif, volume hebdomadaire et préférences de créneau. Le moteur produit des `OptimizationProposal` éphémères ; elles ne deviennent des `Lesson` qu'après application explicite et revalidation transactionnelle.

### Assessment, Grade et ReportCard

`GradeScale` définit soit une plage numérique avec seuil et arrondi, soit une liste ordonnée de degrés de maîtrise. `GradingPolicy` appartient à un cycle scolaire ou au périmètre crèche et choisit un mode `NONE`, `COMPETENCY_ONLY`, `HYBRID` ou `NUMERIC`. Elle référence les barèmes applicables, la stratégie de calcul, sa configuration, l'activation des classements et celle des bulletins.

`AssessmentType` classe une évaluation dans une catégorie de calcul sans imposer de formule. `Assessment` relie classe, période, matière, enseignant et type ; son coefficient local complète le `Coefficient` de la matière pour le niveau et l'année. Une `Grade` porte résultat, statut et commentaire courant. Chaque création ou modification ajoute un `GradeHistory` immuable avec valeurs avant/après, motif et acteur ; la ligne courante peut évoluer mais son passé ne peut être réécrit.

`Competency` est hiérarchique, rattachée à un cycle et éventuellement une matière. `CompetencyEvaluation` observe un élève sur une période avec un degré provenant du barème de compétences et une observation. `TeacherComment` conserve les appréciations générales, par matière ou du professeur principal.

`ReportCard` est au jalon 10 un instantané calculé et recalculable tant qu'il n'est pas publié. Ses `ReportCardSubjectResult` figent moyenne, coefficient, catégories et nombre de notes retenues. Le classement n'existe que si la politique l'active. `ReportCardTemplate` prépare langue et mise en page ; le document PDF publié et versionné appartient au jalon 11.

### LessonContent et Homework

`LessonContent` décrit ce qui a réellement été travaillé à une date dans une classe et une matière. `Homework` décrit un travail demandé, sa consigne, sa date d'attribution, son échéance et sa durée estimée ; il peut référencer un contenu de cours compatible dans la même classe, matière et période. Les deux objets réutilisent un `TeacherAssignment` actif, ce qui évite d'inventer un enseignant ou une matière hors planning.

Un contenu évolue de `DRAFT` à `PUBLISHED`. Un devoir évolue de `DRAFT` à `PUBLISHED`, puis éventuellement `CLOSED`. La publication est immuable et le devoir publié initialise un `HomeworkStudentStatus` `TO_DO` pour chaque inscription active de la classe. Un parent lié peut le passer à `DONE` puis revenir à `TO_DO`; ce statut est un repère d'organisation et non une remise.

`HomeworkAttachment` appartient exclusivement à un cours ou un devoir. Il conserve nom assaini, type MIME, taille, SHA-256 et binaire privé. Les fichiers sont ajoutés uniquement au brouillon puis téléchargés après revalidation du tenant ou du lien parent-élève. Aucun `HomeworkSubmission` n'est modélisé au jalon 12.

### TeacherPortalProfile et journée professeur

`TeacherPortalProfile` matérialise le lien tenant-scoped entre un `UserAccount` interne et un `StaffProfile`. Un compte ne possède qu'un profil professeur actif dans un tenant et un membre du personnel ne peut pas être exposé par deux comptes. Cette relation évite toute résolution ambiguë par e-mail, nom ou matricule transmis par le navigateur.

La journée professeur est une projection calculée à la demande : les `Lesson` d'un `Schedule` publié sont filtrées par le personnel lié, ordonnées selon leurs `TimeSlot` et enrichies avec matière, classe, salle et éventuelle `AttendanceSession`. Les états `COMPLETED`, `IN_PROGRESS`, `NEXT` et `UPCOMING` restent dérivés de l'heure ; aucune copie de planning n'est persistée. Les actions rapides transportent le contexte de la leçon ciblée mais les mutations demeurent dans leurs agrégats propriétaires.

## Invariants transverses

- Toute entité métier appartient à un tenant.
- Une référence inter-entité ne traverse jamais les tenants.
- Les relations structurantes du jalon 2 utilisent aussi des clés étrangères composites `(id, tenant_id)` comme défense en profondeur.
- Les jobs et lignes d'import, profils personnels et données financières de reprise utilisent également des références composites tenant-scoped.
- Les comptes portail, prospects, candidats, dossiers, pièces, activités, décisions, offres, notifications et paiements d'admission utilisent des clés étrangères composites tenant-scoped.
- Les workflows, contrats, confirmations famille, services, documents, signatures, frais et liens parent-élève d'inscription utilisent des références composites tenant-scoped.
- Les groupes, profils, personnes de récupération, politiques, journaux et événements petite enfance utilisent des références composites tenant-scoped ; seuls les journaux publiés sont exposés aux parents liés.
- Les séances, appels, absences, retards, départs anticipés, justificatifs, notifications et objets de vie scolaire utilisent des références composites tenant-scoped ; une séance soumise est immuable et le parent ne peut agir que sur un élève explicitement lié.
- Les matières, plannings, périodes, créneaux, affectations, cours, disponibilités et contraintes utilisent des références composites tenant-scoped ; un planning publié est immuable et aucune collision enseignant/salle/classe n'est acceptée.
- Les barèmes, politiques, évaluations, notes, historiques, compétences, appréciations et bulletins calculés utilisent des références composites tenant-scoped ; toute correction de note est historisée et les enfants de crèche sont exclus des notes numériques.
- Les contenus de cours, devoirs, pièces jointes et statuts élève utilisent des références composites tenant-scoped ; seuls les éléments publiés sont visibles par les parents explicitement liés.
- Le profil portail professeur lie explicitement compte et personnel dans un même tenant ; sa projection ne consulte que les plannings publiés de cet enseignant.
- Les statuts explicites remplacent les booléens ambigus.
- Les écritures financières confirmées sont corrigées par reversal, jamais supprimées.
- Les documents publiés, contrats, bulletins et workflows importants sont versionnés.
- Toute donnée monétaire associe un montant décimal exact à une devise ISO 4217, MAD par défaut.
- Les timestamps techniques sont des `Instant` UTC ; les dates scolaires restent des `LocalDate` dans le calendrier du campus.

## Événements de domaine initiaux

Le catalogue prévu inclut `StudentEnrolled`, `StudentAbsent`, `InvoiceIssued`, `PaymentConfirmed`, `ApplicationSubmitted`, `ApplicationAccepted` et `ReportCardPublished`. Leur schéma est versionné. Les consommateurs externes traitent les événements de manière idempotente.

## Sensibilité des données

| Donnée               | Sensibilité               | Contrôles attendus                                  |
| -------------------- | ------------------------- | --------------------------------------------------- |
| Identité et contacts | personnelle               | permissions, audit des exports, rétention           |
| Santé et allergies   | très restreinte           | permissions dédiées, chiffrement, audit d'accès     |
| Notes et incidents   | confidentielle scolaire   | portée classe/campus, historique des changements    |
| Finance famille      | confidentielle financière | permissions fines, audit append-only, idempotence   |
| Documents d'identité | très restreinte           | stockage privé, URL signée, validation des fichiers |

Le modèle détaillé de chaque module sera ajouté avant son milestone, pas anticipé par des tables génériques.
