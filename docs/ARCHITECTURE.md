# Architecture globale

## Contexte

SchoolZ sert des groupes scolaires privés marocains pouvant exploiter plusieurs campus, de la crèche au lycée. La plateforme doit remplacer des processus dispersés sans sacrifier l'isolation des données, la traçabilité financière ni la simplicité d'usage.

## Vue d'ensemble

```text
Navigateurs / PWA
        |
        v
Reverse proxy (TLS en production)
  |                         |
  v                         v
Angular                  Spring Boot API
backoffice/portails      monolithe modulaire
                            |      |      |
                            v      v      v
                       PostgreSQL Redis  S3 compatible
```

Le backend est stateless autant que possible. PostgreSQL est la source de vérité transactionnelle, Redis supporte les données éphémères et traitements nécessitant expiration, et le stockage S3-compatible reçoit les objets binaires. Aucun fichier utilisateur n'est stocké sous son nom d'origine.

## Modules métier planifiés

| Pilier     | Modules principaux                                                                   |
| ---------- | ------------------------------------------------------------------------------------ |
| Fondation  | identity, organization, people, data-import, audit, documents                        |
| Académique | students, admissions, academics, attendance, timetable, grading, homework, childcare, student portal |
| Familles   | communications, notifications, family portal                                                  |
| Finance    | billing, payments, accounting                                                        |
| Opérations | transport, cafeteria, activities, health, hr, payroll, library, inventory            |
| Plateforme | analytics, integrations, automation, search, webhooks                                |

Chaque module expose des services applicatifs et événements documentés. Les accès directs aux repositories d'un autre module sont interdits. Spring Modulith vérifie les cycles et les dépendances de packages pendant les tests.

Le module `dataimport` dépend des contrats de fondation, identité, organisation et personnes. Il possède ses jobs, lignes et zones de reprise. L'exécution atomique reste dans cette frontière et n'écrit dans les domaines cibles qu'après validation complète.

Le module `admissions` possède le CRM, le pipeline et le portail public. Les collaborateurs utilisent le JWT SchoolZ et les permissions `admissions.read/write`. Les familles utilisent une session opaque distincte, hachée en base et transmise uniquement par cookie HttpOnly. Les services vérifient le tenant et le compte propriétaire sur chaque enfant, dossier, document, notification et offre. Le portail public ne réutilise donc jamais un identifiant métier nu comme preuve d'accès.

Le module `enrollment` orchestre inscription et réinscription sans accéder aux internes des autres modules. Il consomme le contrat public `AdmissionPortalAccess` pour la session parent et `StudentImportOperations` pour la création atomique du dossier élève. Les permissions internes `enrollment.read/write` séparent consultation, affectation de classe et activation. Le workflow possède ses propres instantanés contractuels et familiaux ; la création du domaine Person/Student n'intervient qu'à l'activation finale.

Le module `childcare` constitue une verticale petite enfance distincte des écrans académiques. Il possède les groupes, profils, règles de récupération, politiques et journaux quotidiens. Les éducateurs utilisent les permissions `childcare.read/write/publish`; la saisie d'un médicament exige en plus `childcare.medication` et une politique de campus active. Le portail parent ne reçoit que des journaux `PUBLISHED` pour les élèves liés à sa session, via le contrat public `PortalStudentAccess` du module inscription. Températures, observations et incidents restent des faits saisis manuellement, sans diagnostic médical.

Le module `attendance` possède les séances d'appel, états par élève, absences, retards, départs anticipés, justifications, notifications et faits de vie scolaire. Les enseignants disposent de `attendance.read/write`; l'administration valide les justificatifs avec `attendance.manage`, tandis que `discipline.write` protège incidents, sanctions et observations. Le portail parent réutilise le contrat public `PortalStudentAccess` et ne voit que les enfants liés. La notification M8 est persistée dans la transaction de clôture et consultable en application ; sa livraison future vers e-mail/SMS/push restera un effet externe découplé.

Le module `timetable` possède matières, plannings, créneaux, affectations, cours, disponibilités, équipements et contraintes. Son service applicatif charge un snapshot tenant-scoped puis délègue les conflits et propositions à un moteur Java pur, sans dépendance HTTP, JDBC ou Angular. L'interface ne contient donc aucune règle d'optimisation : elle demande une validation serveur, affiche les conflits, puis applique explicitement une proposition. Les contraintes d'unicité PostgreSQL restent une dernière défense contre les écritures concurrentes. Ce choix est détaillé dans [ADR-006](adr/ADR-006-timetable-constraint-engine.md).

Le module `grading` possède barèmes, politiques par cycle, types d'évaluation, coefficients, évaluations, notes, historique, compétences, appréciations et instantanés de bulletins. Le service applicatif résout toujours la politique à partir de la classe et du tenant, puis fournit au moteur Java pur un ensemble immuable de notes normalisées. Le moteur ne dépend ni de JDBC ni du web et applique uniquement une stratégie validée (`WEIGHTED_MEAN` ou `CATEGORY_WEIGHTED`) et sa configuration persistée. L'historique d'une note est append-only ; le bulletin calculé fige les résultats par matière sans prétendre être encore un document publié. Ce choix est détaillé dans [ADR-007](adr/ADR-007-configurable-grading-engine.md).

Le module `homework` possède contenus de cours, devoirs, pièces jointes et statuts d'organisation par élève. Il réutilise l'affectation professeur–matière–classe du module emploi du temps comme référence stable et le contrat public `PortalStudentAccess` pour vérifier chaque lecture ou mutation famille. Les brouillons peuvent recevoir des fichiers ; une publication devient immuable et initialise les statuts des élèves actifs. Le module ne possède ni remise de copie, ni quiz, ni notation afin de rester un cahier de texte et non un LMS. Ce choix est détaillé dans [ADR-009](adr/ADR-009-focused-homework-not-lms.md).

Le module `teacherportal` est une façade de lecture personnalisée, pas un nouveau propriétaire des données pédagogiques. Il relie explicitement le compte interne au `StaffProfile`, puis agrège uniquement les cours publiés de cet enseignant et calcule des liens contextuels vers `attendance`, `grading` et `homework`. Les modules cibles restent propriétaires des mutations et revalident permissions, tenant et références. Le raccourci message reste désactivé jusqu'au module communications. Ce choix est détaillé dans [ADR-010](adr/ADR-010-teacher-portal-identity-and-aggregation.md).

Le module `familyportal` est la façade de lecture du compte parent unique. Il ne copie ni notes, ni bulletins, ni absences, ni devoirs : il résout les enfants autorisés par le contrat public `PortalStudentAccess`, agrège uniquement les données publiées et conserve seulement la préférence d'enfant du compte. Chaque bulletin est retéléchargé après une nouvelle vérification compte–enfant–tenant. Finance est disponible par son portail dédié ; communications, documents, transport et activités restent explicitement indisponibles jusqu'à leur livraison. Ce choix est détaillé dans [ADR-011](adr/ADR-011-family-portal-read-facade.md).

Le module `studentportal` est une façade pédagogique personnelle distincte du portail famille. Il relie un compte interne portant le rôle `STUDENT` à exactement un `StudentProfile` dans le même tenant, puis agrège uniquement les données publiées de cet élève. Le mapping est unique des deux côtés ; il évite de déduire l'identité scolaire depuis un e-mail ou un nom. Les modules `timetable`, `homework`, `grading` et `attendance` restent propriétaires de leurs données. Le portail peut seulement modifier le statut d'organisation personnel d'un devoir, après revalidation du compte, du tenant et de l'appartenance. Les gardes Angular séparent `/student` et `/app/**`, mais l'autorisation de référence reste appliquée par l'API. Ce choix est détaillé dans [ADR-021](adr/ADR-021-student-portal-identity-and-aggregation.md).

Le module `billing` possède le compte financier familial et son sous-ledger append-only. Charges et lignes couvrent scolarité, inscription, transport, cantine, activité, garderie et autres frais ; les factures émises deviennent immuables. Un paiement validé peut être ventilé sur plusieurs factures et une facture peut recevoir plusieurs paiements. L'annulation ne supprime aucune écriture : elle crée un paiement inverse, des allocations négatives, un avis d'annulation et une trace d'audit. Les numéros de facture, paiement et reçu sont attribués sous verrou tenant/année, puis protégés par unicité en base. Le portail famille résout son compte depuis la session parent opaque sans accepter de tenant ou de compte libre. Ce choix est détaillé dans [ADR-012](adr/ADR-012-family-ledger-and-payment-reversal.md).

Le recouvrement M16 reste dans `billing` afin de calculer ses indicateurs depuis les mêmes écritures. Un échéancier possède ses dates et montants attendus ; les allocations de paiement sur échéance classifient l'encaissement sans créer une seconde somme d'argent. Les vues recalculent attendu, reçu, impayé et aging à une date explicite. Les règles de rappel et les actions sont persistées, tandis que `ReminderChannelAdapter` isole push, e-mail, SMS et WhatsApp. L'adaptateur local retourne uniquement `PREVIEW` : une préparation automatique idempotente n'est jamais présentée comme une livraison externe. Ce choix est détaillé dans [ADR-013](adr/ADR-013-installment-collections-and-reminder-adapters.md).

Le paiement en ligne M17 reste orchestré dans `billing`, mais toute dépendance fournisseur passe par le port `PaymentProvider`. Une intention idempotente précède l'appel externe ; un webhook signé est journalisé, dédupliqué puis revérifié auprès du fournisseur avant de produire exactement une écriture `ONLINE_PAYMENT` dans le ledger. Le corps brut n'est pas conservé : son empreinte SHA-256 permet la corrélation sans augmenter inutilement l'exposition de données. Un remboursement fournisseur crée une compensation ledger et ne modifie jamais l'écriture originale. Le profil local ne branche que `SandboxPaymentProvider` et ne prétend pas intégrer une banque réelle. Ce choix est détaillé dans [ADR-014](adr/ADR-014-payment-provider-and-signed-webhooks.md).

Les jalons M18–M23 ajoutent six modules bornés : `communications`, `documents`, `transport`, `cafeteria` et `activities`, le workflow de signature restant dans `documents`. Les ports `DocumentObjectStorage`, `DocumentSignatureProvider` et `GpsProvider` isolent respectivement stockage objet, fournisseur de signature et géolocalisation. Cantine et activités ne possèdent aucun solde : elles créent des charges référencées dans le ledger `billing`. Voir ADR-015 à ADR-020.

## Flux d'une requête métier

```text
HTTP DTO
  -> authentification et contexte tenant
  -> autorisation par permission
  -> service applicatif
  -> règles/invariants du domaine
  -> repository tenant-aware
  -> transaction PostgreSQL
  -> audit et événements de domaine
  -> DTO de réponse
```

Les effets externes fiables utilisent une transactional outbox. Ils ne sont pas exécutés dans la transaction HTTP principale.

## Multi-tenancy

La stratégie initiale est une base et un schéma partagés, avec colonne `tenant_id` obligatoire sur chaque donnée métier. Cette stratégie optimise les opérations SaaS tout en conservant la possibilité future de déplacer un tenant vers une base dédiée derrière les mêmes ports de persistance.

Défenses cumulatives :

1. le tenant provient de l'identité authentifiée, jamais d'un paramètre libre ;
2. le contexte tenant est obligatoire dans la couche applicative ;
3. les repositories filtrent par tenant et refusent les identifiants nus ;
4. les contraintes et index incluent le tenant ;
5. les tests tentent explicitement les accès croisés ;
6. PostgreSQL Row Level Security sera évalué comme défense supplémentaire avant les premières données métier sensibles.

Le détail et les compromis sont consignés dans [ADR-002](adr/ADR-002-multi-tenancy.md).

## Frontend

Le Milestone 0 démarre avec une application Angular unique structurée par domaines. Le backoffice, le portail famille, le portail élève et le portail admissions possèdent déjà des points d'entrée et des gardes distincts ; leur séparation en applications déployables sera introduite quand les politiques de cache ou de cycle de livraison diffèreront réellement. Les bibliothèques de design, i18n et API restent partageables dès le départ.

La locale active contrôle simultanément les traductions, les formats et la direction `ltr/rtl`. Le portail famille est installable en PWA avec `/family` comme point d'entrée et des raccourcis vers devoirs et absences. Le service worker met en cache l'enveloppe applicative et les ressources statiques, jamais les routes `/api/**` ni les données familiales.

## Observabilité

- logs JSON en production, sans données personnelles ni secrets ;
- correlation ID propagé sur chaque requête ;
- Actuator pour health, readiness, liveness et métriques ;
- métriques Micrometer ;
- hooks OpenTelemetry et suivi d'erreurs ajoutés avant production.

## Déploiement

L'environnement local utilise Docker Compose. La production cible des conteneurs distincts pour reverse proxy, frontend, API, PostgreSQL managé, Redis managé et stockage objet. Les secrets viennent d'un secret manager ou de variables injectées, jamais de Git.
