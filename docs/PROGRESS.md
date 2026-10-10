# État d'avancement

Dernière mise à jour : 11 août 2026.

## Release candidate 1.0 — préparation à la production

- versions backend et frontend alignées sur `1.0.0`, sans mention de jalon dans l'interface livrée ;
- construction de production séparée de la démonstration : aucun compte, mot de passe ni bouton sandbox n'est inclus dans le bundle public ;
- page `/login` unique et mémorisation locale du seul identifiant d'établissement après une connexion réussie ;
- portail famille et services associés protégés par une garde de session commune, avec retour contrôlé vers `/login` ;
- profil Spring `prod` validé au démarrage : secrets robustes et distincts, origines/issuer HTTPS, cookies sécurisés et données de démonstration désactivées ;
- stockage documentaire S3-compatible réel en production, intégrité SHA-256 vérifiée et stockage JDBC réservé à la démonstration locale ;
- images Nginx non privilégiées, systèmes de fichiers applicatifs en lecture seule, limitation de débit et routes Actuator/OpenAPI non publiées ;
- composition `compose.prod.yaml`, exemple d'environnement et guide `docs/DEPLOYMENT.md` ajoutés ;
- validation : 135 tests frontend dans 47 fichiers, lint, build production/démo, audit npm de production sans vulnérabilité, compilation/paquet backend, tests du validateur de production, images Docker et smoke test des quatre profils réussis ; la suite backend exhaustive a progressé sans échec mais a dépassé la fenêtre locale de vingt minutes et reste exécutée par la CI.

## Release 1.1 — provisionnement exploitable

- commande opérateur idempotente pour créer une école, ses rôles, son propriétaire, ses réglages Maroc et son campus principal ;
- endpoint protégé par un secret indépendant, accessible depuis le conteneur backend mais bloqué par le proxy Internet ;
- activation à usage unique : le premier administrateur et les utilisateurs choisissent eux-mêmes leur mot de passe ;
- création des comptes professeur et élève reliée obligatoirement à leur fiche métier, sans correspondance fragile par nom ou e-mail ;
- liens d'activation générés depuis l'administration, avec identifiant d'établissement prérempli à l'arrivée ;
- documentation opérateur dans `docs/PROVISIONING.md` et script `scripts/provision-school.ps1` ;
- validation : 47 migrations Flyway, test end-to-end provisionnement/activation/connexion, régression identité, contrôle Modulith, 136 tests frontend, lint, formatage et build de production réussis.

## Jalon courant

Milestone 51 — Accessibilité et stabilisation finale — terminé et validé. Extension post-M23 « Portail élève autonome et refonte UX » — terminée et validée.

Les Milestones 0 à 51 forment désormais une première version fonctionnelle cohérente et stabilisée de SchoolZ. Les jalons M43 à M51 sont revenus transversalement sur les écrans, la sécurité, les données personnelles, la reprise, l’exploitation, les tests, la performance et l’accessibilité. Une validation juridique, un audit de sécurité indépendant et une recette d’accessibilité avec utilisateurs restent nécessaires avant une mise en production contractuelle.

## Correctif de démonstration post-M51 — connexion unifiée

- la connexion principale reconnaît désormais les comptes internes et, en repli sécurisé, les comptes famille ;
- le compte `parent.demo@schoolz.ma` ouvre directement `/family` avec Yasmine et Amine, sans demander une seconde saisie ;
- un bouton « Tester le portail parent » rejoint les boutons administrateur, professeur et élève ;
- `/family` ne contient plus aucun formulaire de connexion : sans session famille, la route redirige systématiquement vers `/login?portal=family` ;
- la déconnexion famille révoque la session côté serveur, vide immédiatement les données affichées puis remplace l'historique par `/login` ;
- la page de connexion affiche un contexte parent explicite tandis que le titre du document et l'annonce d'accessibilité sont réinitialisés, sans conserver « Famille Démo » ;
- le chargement de la feuille de style de production n'utilise plus de gestionnaire `onload` en ligne incompatible avec la CSP stricte ; les styles globaux et le texte réservé aux lecteurs d'écran restent donc actifs ;
- le champ établissement affiche l’exemple `ecole-demo` et refuse clairement une adresse e-mail, afin d’éviter de placer `prof.math@schoolz.ma` dans le mauvais champ ;
- validation : tests unitaires du repli famille, de la redirection sans session et de la déconnexion, suite frontend portée à 133 tests dans 46 fichiers, recette navigateur parent et professeur réussie.

## M51 — Accessibilité

- base typographique portée à 17 px, petits textes à 14 px minimum et cibles interactives à 44 px ;
- contrastes renforcés, focus clavier visible, lien d’évitement, annonces de changement de page et titres de document ;
- états de langue, direction RTL, boutons de locale, icônes décoratives et libellés accessibles revus ;
- prise en charge de la réduction des animations, du contraste renforcé et des couleurs forcées ;
- guide de recette manuelle dans `docs/ACCESSIBILITY.md`, sans revendiquer une certification WCAG automatique ;
- recette navigateur des espaces administrateur, élève et professeur : titres/focus synchronisés, aucun bouton sans nom, aucun champ sans label, aucun texte visible sous 14 px et aucun débordement horizontal à 1280 px ;
- validation : 130 tests frontend dans 44 fichiers, lint, formatage et build Angular réussis.

## M50 — Performance

- API élèves paginée et bornée à 100 éléments, chargement frontend par pages de 40 et ancienne liste plafonnée ;
- migration V46 ajoutant les index des parcours critiques ;
- test PostgreSQL réel avec 20 000 élèves et contrôle des index ;
- script `scripts/load-test.ps1` pour mesurer débit, erreurs et percentiles sur une démo locale ;
- validation : scénario d’échelle et l’ensemble des migrations PostgreSQL réussis ; charge locale 240/240 HTTP 200, 0 échec, 23,4 req/s et p95 de lot 55 ms.

## M49 — Stratégie de tests

- rapport JaCoCo intégré au cycle Maven `verify` ;
- tests unitaires, API, sécurité, isolation tenant, architecture modulaire et migrations PostgreSQL ;
- smoke test HTTP des profils administrateur, professeur, élève et famille dans `scripts/e2e-smoke.ps1` ;
- stratégie, périmètres et critères de sortie documentés dans `docs/TEST_STRATEGY.md` ;
- validation complète backend : 195 tests, aucun échec, aucune erreur et aucun test ignoré.

## M48 — Observabilité

- métriques Prometheus et histogrammes HTTP avec seuils de latence ;
- logs JSON corrélés par identifiant de requête sans données personnelles ;
- indicateurs de santé et jauges pour webhooks, workflows, notifications, demandes de données et tâches en retard ;
- runbook d’exploitation, alertes proposées et limites documentés dans `docs/OBSERVABILITY.md`.

## M47 — Sauvegarde et reprise

- sauvegarde PostgreSQL au format custom, archive du stockage objet et manifeste avec empreintes SHA-256 ;
- restauration dans une base temporaire isolée, vérification Flyway/tenants/utilisateurs puis suppression garantie ;
- procédure de sinistre, objectifs RPO/RTO proposés et exigences de production documentés dans `docs/BACKUP_RESTORE.md` ;
- preuve locale finale : `RESTORE_OK`, migration 46, 1 tenant et 4 utilisateurs, base temporaire supprimée ; à rejouer après chaque évolution de schéma.

## M46 — Données personnelles

- registres tenant-aware des finalités, consentements, demandes de personnes, rétention, exports et hébergement ;
- permissions `privacy.read`, `privacy.manage` et `privacy.export`, isolation tenant et audit des changements ;
- centre Angular `/app/privacy` et migration V45 ;
- doctrine de minimisation et points juridiques loi 09-08/CNDP documentés, sans déclaration de conformité automatique.

## M45 — Sécurité avancée

- filtre commun des uploads : nom, chemin, taille, type MIME, signature réelle et détection EICAR, avec port antivirus extensible ;
- JWT durci, en-têtes de sécurité, CSP, limitation de débit de la connexion et absence de cache des API ;
- campagnes ciblées IDOR, isolation tenant, fichiers hostiles et persistance nulle après rejet ;
- revue et risques résiduels consignés dans `docs/SECURITY_REVIEW_M45.md`.

## M44 — PWA

- installation séparée des portails famille et professeur, manifests et service d’installation dédiés ;
- cache contrôlé des ressources statiques, absence de cache hors ligne pour les API et gestion explicite des mises à jour ;
- push non simulé : l’activation nécessite encore une infrastructure VAPID réelle ;
- limites et recette documentées dans `docs/PWA.md`.

## M43 — UX et design system

- navigation regroupée selon le rôle et les permissions, espace professeur dédié et navigation mobile contextuelle ;
- recherche et notifications intégrées à la barre supérieure ;
- tokens de lisibilité/toucher, lien d’évitement et responsive harmonisés ;
- conventions et composants documentés dans `docs/DESIGN_SYSTEM.md`.

## M42 — Calendrier établissement

- calendrier tenant-aware pour vacances, examens, réunions, sorties, activités et rencontres parents-professeurs, avec audiences établissement, campus ou classe ;
- consultation bornée à 370 jours, filtrable par période, type et campus, dates stockées en UTC et restituées selon le contexte de l’établissement ;
- création permissionnée et annulation logique auditée : un événement annulé reste visible dans l’historique et n’est jamais effacé silencieusement ;
- contrat `CalendarSyncAdapter` prévu pour une synchronisation externe ultérieure, sans inventer de fournisseur ni prétendre qu’une intégration distante est déjà active ;
- permissions `calendar.read` et `calendar.manage`, rôle `CALENDAR_MANAGER`, lecture professeur et gestion Direction/administration ;
- API `/api/v1/calendar` pour consulter une période, créer un événement et l’annuler ;
- agenda Angular `/app/calendar` : navigation sur deux mois, filtres par nature et campus, repères temporels, création guidée et annulation ;
- migration V44 compatible H2/PostgreSQL avec contraintes de dates, types et audiences, index temporels et champs de synchronisation ;
- démonstration idempotente avec six événements représentatifs ;
- validation : 3 tests backend ciblés, architecture Spring Modulith valide ; 122 tests frontend dans 40 fichiers, lint et build Angular réussis ;
- prochaine étape : M43, revue UX et design system multi-portail.

## M41 — Tâches internes

- tâches tenant-aware manuelles ou issues d’un workflow, avec responsable, échéance, priorité, statut et lien vers l’objet métier concerné ;
- priorités `LOW`, `MEDIUM`, `HIGH`, `URGENT` et cycle `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED`, avec transitions et réassignations permissionnées ;
- action `CREATE_TASK` du moteur M33 réellement branchée sur le registre de tâches et protégée contre les doublons par exécution/action ;
- visibilité réduite aux tâches assignées pour le professeur, gestion globale pour Direction/administration et rôle `TASK_MANAGER` ;
- API `/api/v1/tasks` paginée et filtrable, avec création, changement de statut et affectation ;
- espace Angular `/app/tasks` : fil de travail, compteurs, filtres, priorités, création contextualisée et actions adaptées au rôle ;
- migration V43 compatible H2/PostgreSQL et démonstration avec quatre tâches manuelles plus une tâche automatisée ;
- validation : 3 tests backend ciblés dont la création par workflow ; 120 tests frontend dans 39 fichiers, lint et build Angular réussis.

## M40 — Centre de notifications

- centre unifié tenant-aware pour notifications internes, états lu/non lu/archivé et pagination ;
- préférences indépendantes push, e-mail et SMS, avec politiques d’établissement et impossibilité de désactiver l’e-mail d’une alerte de sécurité critique ;
- API `/api/v1/notification-center` pour le flux, la lecture, l’archivage et les préférences ;
- interface Angular `/app/notifications` : rivière de statuts, alertes critiques mises en évidence et panneau de préférences explicite ;
- migration V42 compatible H2/PostgreSQL, quatre notifications de démonstration et préférences initiales ;
- permissions `notification-center.read` et `notification-center.manage` ;
- validation : 3 tests backend ciblés, architecture Spring Modulith valide ; 117 tests frontend dans 38 fichiers, lint et build Angular réussis.

## M39 — Recherche globale

- index de recherche tenant-aware couvrant élèves, parents, professeurs, factures, classes et documents ;
- normalisation des accents latins, des signes diacritiques arabes, du tatweel et des variantes usuelles de l’alif, sans exposer un résultat que l’utilisateur ne peut pas ouvrir ;
- résultats paginés et limités à 50, filtrables par type, avec facettes et reconstruction explicite de l’index ;
- permissions `global-search.read` et `global-search.manage` appliquées aussi à chaque catégorie de document ;
- API `/api/v1/search` et `/api/v1/search/refresh` ;
- interface Angular `/app/search` bilingue, avec raccourcis de catégories, facettes, pagination et liens métier ;
- migration V41 compatible H2/PostgreSQL et index de démonstration ;
- validation : 5 tests backend ciblés dont isolation des permissions et recherche arabe ; 114 tests frontend dans 37 fichiers, lint et build Angular réussis.

## M38 — Webhooks sortants

- module `webhooks` tenant-aware avec abonnements HTTPS, six événements stables, événements idempotents, livraisons, tentatives append-only et dead letters ;
- événements couverts : `student.created`, `student.enrolled`, `invoice.created`, `invoice.paid`, `absence.created` et `grade.published` ;
- secret aléatoire révélé une seule fois, empreinte SHA-256 d’identification et valeur chiffrée AES-GCM pour permettre la signature sans réexposer le secret ;
- enveloppe versionnée et signature HMAC-SHA256 portant l’horodatage, l’identifiant d’événement et le payload ; en-têtes dédiés événement, livraison, requête, timestamp et signature ;
- transport HTTP sans redirection, timeout borné et URL HTTPS absolue obligatoire ; aucune réponse distante ni donnée sensible n’est persistée ;
- reprise exponentielle, maximum configurable de 1 à 10 tentatives, traitement par lots de 20, état `RETRY_SCHEDULED`, journal de chaque essai et passage explicite en `DEAD_LETTER` ;
- rejeu humain motivé d’une livraison en échec, résolution de la dead letter sans effacer les tentatives précédentes, révocation définitive d’un abonnement et audit ;
- permissions `webhook.read` et `webhook.manage`, rôle `WEBHOOK_MANAGER`, refus confirmé pour le compte élève ;
- API `/api/v1/webhooks` pour dashboard, abonnements, publication idempotente, traitement de file, reprise et révocation ;
- centre Angular `/app/webhooks` : flux des événements, destinations, états, dead letters, création avec secret à usage unique, événement test et reprise motivée ;
- migration V40 compatible H2/PostgreSQL : cinq tables, contraintes tenant composites, statuts bornés, idempotence et index de file ;
- démonstration idempotente : deux destinations, trois livraisons illustrant succès, retry et dead letter, huit tentatives historisées ;
- validation ciblée : 5 tests backend M38, formatage et compilation réussis ; 111 tests frontend dans 36 fichiers, lint et build Angular réussis ;

## M37 — API publique

- contrat externe distinct sous `/api/public/v1`, limité à trois ressources versionnées : élèves actifs, factures publiées et classes avec capacité ;
- aucune table exposée directement : DTO dédiés, pagination obligatoire, taille bornée à 100 éléments et champs explicitement sélectionnés ;
- clés serveur générées aléatoirement, révélées une seule fois, stockées uniquement sous empreinte SHA-256 et identifiées par un préfixe non sensible ;
- scopes minimaux `students:read`, `invoices:read` et `classes:read`, expiration facultative, révocation définitive et quota propre à chaque client ;
- réponses protégées par `X-SchoolZ-Api-Key`, avec identifiant de requête et compteurs de quota dans les en-têtes ; refus explicites `401`, `403` et `429` ;
- journal tenant-aware de chaque appel authentifié : route, scope, résultat, statut, durée et identifiant de requête, sans conserver le secret ;
- schéma OpenAPI enrichi du mécanisme `schoolzApiKey` et annotations de contrat sur les ressources externes ;
- permissions `public-api.read` et `public-api.manage`, rôle `API_MANAGER`, console Angular `/app/api-access` pour les clients, scopes, quotas, révocations et traces ;
- migration V39 compatible H2/PostgreSQL : clients techniques, empreintes uniques, scopes, quotas et journal append-only indexé ;
- démonstration idempotente avec la clé locale `schoolz_demo_api_key_m37_2026`, trois scopes et un quota de 120 appels par minute ;
- validation : 167 tests backend dans 44 suites et un test PostgreSQL, zéro échec ; 39 migrations, 366 sources Java et Spring Modulith valide ; 106 tests frontend dans 35 fichiers, lint et build Angular réussis ;
- recette Docker/API/navigateur : cinq services opérationnels, 4 élèves/1 facture/3 classes visibles par leurs scopes, quota 120/minute, OpenAPI déclaré, traces réelles et écran 1280 px sans débordement ni chevauchement ;
- prochaine étape : M38, webhooks sortants signés, idempotents, rejouables et observables jusqu’à leur dead letter.

## M36 — Configuration des workflows et métier

- module `businessconfiguration` tenant-aware constituant un registre central de choix d’établissement, sans écraser les politiques spécialisées déjà gérées par chaque module ;
- dix domaines couverts : académique, calendrier, horaires, finance, absences, discipline, documents, notifications, notes et sécurité ;
- quatorze réglages de référence incluant périodes, coefficients, échelles de notes, jours scolaires, amplitude et durée de séance, catalogue de frais, plans de paiement, motifs d’absence, sanctions éducatives, templates, canaux, règles de calcul et permissions délégables ;
- neuf types validés côté serveur : `TEXT`, `NUMBER`, `BOOLEAN`, `TIME`, `SELECT`, `MULTI_SELECT`, `JSON`, `MONEY` et `PERCENT`, avec choix, bornes et taille structurée contrôlés ;
- versionnement optimiste : toute modification exige la version attendue et un motif, refuse les écrasements simultanés et crée une révision append-only ;
- retour explicite à la valeur de référence, lui-même versionné et audité ; aucune mutation silencieuse ni remise à zéro destructrice ;
- permissions `business-configuration.read` et `business-configuration.manage`, rôle `CONFIGURATION_MANAGER`, accès Direction/administration et refus confirmé pour professeur et élève ;
- API `/api/v1/business-configuration` pour le dashboard filtrable, la mise à jour typée et la restauration d’un réglage ;
- centre Angular `/app/configuration` : rivière de dix domaines, flux éditorial des réglages, contrôles adaptés au type, motif obligatoire, indicateurs et journal latéral des décisions ;
- migration V38 compatible H2/PostgreSQL : table des réglages actifs, table de révisions, clés uniques tenant, version minimale, types/catégories bornés et index de lecture ;
- démonstration idempotente : 14 réglages et 14 versions initiales, sans prétendre installer un fournisseur de notification ou appliquer automatiquement une sanction ;
- validation : 161 tests backend dans 43 suites et un test PostgreSQL, zéro échec ; 38 migrations, 357 sources Java et Spring Modulith valide ; 102 tests frontend dans 34 fichiers, lint et build Angular réussis ;
- recette Docker/API/navigateur : cinq services opérationnels, 14 réglages/10 domaines/14 révisions, filtre Finance à deux réglages et affichage 1280 px sans débordement ni chevauchement.

## M35 — Champs personnalisés

- module `customfields` tenant-aware pour étendre les dossiers `STUDENT`, `PERSON`, `HOUSEHOLD`, `EMPLOYEE`, `ADMISSION` et `INVOICE` sans altérer leurs tables métier ;
- définitions typées `TEXT`, `NUMBER`, `DATE`, `BOOLEAN`, `SELECT` et `MULTI_SELECT`, avec libellé, aide, ordre, caractère obligatoire et choix contrôlés ;
- valeurs stockées dans des colonnes dédiées au type, contrainte SQL imposant un seul stockage et validation serveur systématique des nombres, dates, booléens et options ;
- permissions `custom-field.read`, `custom-field.configure` et `custom-field.value.write`, complétées par des permissions facultatives de lecture/écriture propres à chaque définition ;
- filtrage serveur des champs sensibles : un enseignant ne reçoit pas le groupe sanguin protégé, tandis que l’infirmière ou l’administration autorisée peut le lire et l’écrire ;
- contrôle tenant des définitions, valeurs, cibles métier et permissions déclarées, avec refus des clés dupliquées, cibles inconnues et valeurs invalides ;
- API `/api/v1/custom-fields` pour le dashboard, la création/mise à jour des définitions et l’enregistrement ou l’effacement d’une valeur ;
- atelier Angular `/app/custom-fields` : sélection du type de dossier et de la personne, ruban des entités, flux des champs typés et tiroir de composition avec permissions ;
- démonstration idempotente : quatre champs Élève (`blood_group`, `internal_registration_number`, `bus_code`, `custom_reference`) et quatre valeurs réelles sur le dossier de Yasmine ;
- migration V37 compatible H2/PostgreSQL, unicités tenant, relations composites et index de lecture ; conservation explicite du stockage historique V4 pour compatibilité ;
- validation : 155 tests backend dans 42 suites et un test PostgreSQL, zéro échec ; 37 migrations, 351 sources Java et Spring Modulith valide ; 98 tests frontend dans 33 fichiers, lint et build Angular réussis ;
- recette Docker/API/navigateur : cinq services opérationnels, 4 champs dont 2 protégés et 4 valeurs chargées, six types exposés, tiroir de création fonctionnel et affichage 1280 px sans débordement ni chevauchement.

## M34 — Massar et intégrations

- module `integrations` tenant-aware avec cinq adaptateurs explicites : Massar par fichiers, Excel/CSV générique, comptabilité, rapprochement de paiements et messagerie ;
- aucune collecte Massar par scraping, aucun accès non documenté et aucune API officielle simulée : le contrat serveur et l’interface exposent en permanence ces trois limites ;
- modèles CSV UTF-8 à séparateur `;` et classeurs XLSX réels, avec jeux de données typés, en-têtes requis, import/export autorisé par schéma et profils de mapping persistants ;
- sécurité des fichiers : plafond de 10 000 lignes, en-têtes uniques, refus des formules Excel, neutralisation des cellules CSV exécutables et lecture des valeurs sans macro ;
- exports réels des élèves SchoolZ, fichiers binaires téléchargeables, nom déterministe, taille et empreinte SHA-256 conservés ; les autres schémas livrent des modèles déclarés sans prétendre représenter un format fournisseur officiel ;
- imports en mode validation : chaque ligne est conservée avec statut et erreur précise, mais aucune donnée élève, financière ou de messagerie n’est écrite automatiquement avant un futur workflow de confirmation ;
- historique append-only des jobs, lignes et fichiers, isolation tenant, audit des actions et permissions `integration.read` / `integration.manage` avec rôle `INTEGRATION_MANAGER` ;
- API `/api/v1/integrations` pour le dashboard, les exports, les validations multipart et les téléchargements empreintés ;
- centre Angular `/app/integrations` : contrat d’intégrité Massar, cinq voies d’échange, schémas et colonnes attendus, tiroirs import/export et journal des empreintes ;
- migration V36 compatible H2/PostgreSQL : cinq tables, contraintes tenant composites, états bornés, unicités et index d’historique ;
- démonstration idempotente : cinq adaptateurs prêts, profils CSV/XLSX, export initial des quatre élèves et fichier CSV téléchargeable ;
- validation : 148 tests backend dans 41 suites et un test PostgreSQL, zéro échec ; 36 migrations, 346 sources Java et Spring Modulith valide ; 93 tests frontend dans 32 fichiers, lint et build Angular réussis ;
- recette Docker/API/navigateur : cinq services opérationnels, 5/5 adaptateurs prêts, politique Massar `false/false`, export de quatre lignes, tiroir de génération vérifié et mise en page 1280 px sans débordement ni chevauchement après correction responsive.

## M33 — Workflow engine

- moteur événementiel configurable suivant la structure explicite `TRIGGER → CONDITIONS → ACTIONS`, sans couplage Java direct aux modules sources ;
- conditions typées `STRING`, `NUMBER` et `BOOLEAN`, chemins imbriqués, opérateurs `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `EXISTS` et `IN` ;
- actions ordonnées `SEND_NOTIFICATION`, `SEND_EMAIL`, `CREATE_TASK`, `NOTIFY_PARENT` et `EMIT_EVENT`, distribuées par un adaptateur local traçable avant les centres spécialisés M40/M41 ;
- exécutions tenant-aware et idempotentes par couple workflow/événement, avec événement racine, parent, profondeur, payload figé, statut et motif d’échec ;
- journal append-only de chaque déclencheur, condition, action, garde anti-boucle et état d’exécution ;
- triple protection contre les boucles : workflow déjà présent dans l’ascendance, profondeur configurable limitée à 12 et plafond de 50 exécutions par événement racine ;
- permissions `workflow.read` et `workflow.manage`, rôle `WORKFLOW_MANAGER`, accès administrateur/superviseur et refus confirmé pour le profil élève ;
- endpoints de dashboard, création, activation/pause et distribution d’événement sous `/api/v1/workflows` ;
- studio Angular `/app/workflows` : lecture graphique WHEN/IF/THEN, activation, compositeur, banc d’essai événementiel et journal détaillé pas à pas ;
- démonstration idempotente : relance de facture à J+7 avec trois actions, notification d’absence non justifiée et workflow récursif volontairement arrêté ;
- migration V35 compatible H2/PostgreSQL : six tables, contraintes tenant, unicité d’idempotence, relations parent/enfant et index d’exécution ;
- validation : 138 tests backend et un test PostgreSQL, zéro échec ; 35 migrations, 334 sources Java, Spring Modulith valide, 89 tests frontend dans 31 fichiers, lint et build Angular réussis ;
- recette Docker/API/navigateur à 1280 px : 3 workflows actifs, 4 exécutions initiales, 5 actions, 1 boucle bloquée, zéro débordement ou chevauchement, événement réel et journal TRIGGER/CONDITION/ACTION vérifiés.

## M32 — Alertes et early warning

- moteur tenant-scoped configurable avec conditions `ALL`/`ANY`, opérateurs numériques, seuils, sévérités et activation explicite des règles ;
- six familles de signaux scolaires et administratifs : absences, retards, baisse des notes, incidents, impayés et documents manquants ;
- alerte combinée de référence `absentéisme > 10 % ET moyenne < 10`, avec conservation de chaque mesure, opérateur et seuil comme preuve factuelle ;
- réévaluation idempotente : les alertes actives sont rafraîchies sans doublon, tandis qu’une résolution conserve l’historique et autorise une détection ultérieure distincte ;
- workflow humain `OPEN → ACKNOWLEDGED → RESOLVED`, note factuelle obligatoire, audit des actions et historique des passages du moteur ;
- garde-fou serveur refusant les formulations de diagnostic psychologique ou médical dans les règles, sans profilage ni étiquette comportementale ;
- permissions dédiées `early-warning.read` et `early-warning.manage`, rôle `EARLY_WARNING_MANAGER`, accès administrateur/superviseur et refus confirmé pour le profil élève ;
- endpoints de dashboard, évaluation, création de règle, prise en charge et résolution sous `/api/v1/early-warning` ;
- centre de vigilance Angular `/app/early-warning` : file éditoriale, preuves, vue par élève, logique des règles, historique et tiroirs d’action ;
- démonstration idempotente : 3 élèves observés, 6 règles, 9 alertes actives dont 3 élevées, 7 définitions de signal et un passage initial du moteur ;
- migration V34 compatible H2/PostgreSQL, six tables, contraintes tenant, unicités et index ; correction annexe du rôle `LIBRARIAN` afin de préserver `student.read` et toutes ses permissions métier ;
- validation : 128 tests backend et un test PostgreSQL, zéro échec ; 34 migrations, 328 sources Java, Spring Modulith valide, 85 tests frontend dans 30 fichiers, lint et build Angular réussis ;
- contrôle Docker/API et navigateur réel à 1280 px : données exactes, route permissionnée, onglets et tiroir de règle fonctionnels, zéro débordement et chevauchement responsive corrigé.

## M31 — Analytics Direction

- modèle tenant-scoped de snapshots datés par campus, complété par des dimensions historisées pour classes, admissions, services financiers et ancienneté des impayés ;
- huit KPI exécutifs consolidés : élèves, présents, absents, enseignants absents, revenu attendu, encaissé, impayés et nouvelles candidatures, avec taux de présence, recouvrement et capacité calculés côté serveur ;
- douze mois de tendances pour les inscriptions, le recouvrement et l’absentéisme, sans recalcul arbitraire dans le navigateur ;
- vues de pilotage pour la répartition par cycle, l’entonnoir d’admission, le revenu par service, l’aging des impayés et la capacité ;
- comparaison native des campus Casablanca Centre, Rabat Agdal et Marrakech Guéliz, plus filtre qui recalcule KPI, tendances et ventilations sur un seul campus ;
- permission dédiée `analytics.read`, rôle `ANALYTICS_VIEWER`, refus confirmé pour le profil élève et contrôle tenant systématique ;
- endpoint `GET /api/v1/analytics/direction?asOf=&campusId=` borné dans le temps, avec date de snapshot explicite et signaux de décision éditoriaux ;
- cockpit Angular `/app/analytics` responsive : rivière de KPI, graphiques d’évolution, barres de recouvrement, sparkline d’absentéisme, funnel, chemin des impayés et comparaison des campus ;
- identité visuelle organique bleue : grands volumes, lignes éditoriales, surfaces asymétriques, respiration typographique et absence d’une grille uniforme de cartes blanches ;
- démonstration idempotente sur septembre 2025–août 2026 : 1 284 élèves, 1 216 présents, 68 absents, 3 enseignants absents, 1,42 M MAD attendus, 1,18 M encaissés, 240 k MAD d’impayés et 14 nouvelles candidatures ;
- migration V33 compatible H2/PostgreSQL, deux tables et index de lecture ; seeder conditionnel pour rester compatible avec les contextes de tests partiels ;
- validation : 117 tests backend et un test PostgreSQL, zéro échec ; 33 migrations, 322 sources Java, Spring Modulith valide, 81 tests frontend dans 29 fichiers, lint et build Angular réussis.

## M30 — Bibliothèque

- catalogue tenant-scoped complet : auteurs, collections, titres, relations livre–auteur et exemplaires physiques, sans dupliquer les lieux déjà gérés par l’inventaire M29 ;
- identification de chaque exemplaire par numéro d’inventaire et au moins un code-barres ou QR unique, avec recherche unifiée par scan ;
- cycle de prêt contrôlé : seul un exemplaire disponible ou réservé au bon élève peut sortir, avec état au départ, date d’échéance, retour, état constaté et historique conservé ;
- réservations équitables : exemplaire disponible immédiatement mis de côté, sinon file d’attente ordonnée ; un retour ou une annulation promeut automatiquement le prochain lecteur ;
- pénalités entièrement optionnelles et configurables par montant journalier et plafond ; elles ne naissent qu’au retour réellement en retard et peuvent être réglées ou levées avec justification ;
- neuf tables dédiées pour configuration, auteurs, collections, livres, auteurs de livre, exemplaires, prêts, réservations et pénalités, toutes contraintes par tenant ;
- cinq permissions distinctes `library.read/catalog.manage/circulation.manage/fine.manage/configure` et rôle `LIBRARIAN`, avec refus confirmé du dashboard interne au profil élève ;
- interface bibliothécaire `/app/library` responsive : catalogue narratif, scan code-barres/QR, prêts et retours, file de réservations, règles et pénalités, plus formulaires guidés pour tous les référentiels et opérations ;
- vraie rubrique `/student/library` dans le portail élève indépendant : emprunts actifs, échéances, réservations prêtes ou en attente, lectures rendues et pénalités éventuelles ;
- vraie rubrique `/library` dans le portail famille : lecture limitée aux enfants liés, sélection Amine/Yasmine conservée et entrée directe depuis la grille de services famille ;
- démonstration idempotente : trois auteurs, trois collections, trois titres, quatre exemplaires, un prêt actif « Le Petit Prince », une réservation prête « Mathématiques 1AP » et un retour tardif produisant une pénalité de 10 MAD ;
- migration Flyway V32 validée sur H2 et PostgreSQL 18.4 ; le seeder reste conditionnel afin que les contextes de tests sans portail élève puissent démarrer isolément ;
- validation : 112 tests unitaires/API/architecture et un test PostgreSQL, zéro échec ; Spring Modulith valide, 79 tests frontend dans 28 fichiers, lint et build Angular de production réussis ;
- recette réelle : quatre onglets et formulaire de prêt vérifiés, scan `SZ-LIB-QR-0001` réussi, accès élève et parent contrôlés, changement d’enfant corrigé puis revalidé, et aucun débordement horizontal à 1 265 px.

## M29 — Inventaire

- deux flux tenant-scoped complémentaires : actifs unitaires identifiés par tag et numéro de série, et articles en stock suivis par quantités et emplacements ;
- sept tables dédiées pour `Asset`, `InventoryItem`, `StockMovement`, `Location`, soldes par emplacement, `AssetAssignment` et événements d’actif append-only ;
- catégories livrées pour ordinateur, tablette, projecteur, livre, uniforme, matériel pédagogique et autre, sans enfermer l’école dans un catalogue fixe ;
- mouvements contrôlés `INBOUND`, `OUTBOUND`, `TRANSFER`, `ADJUSTMENT_IN` et `ADJUSTMENT_OUT` ; une transaction met à jour les deux emplacements d’un transfert et interdit toujours un stock négatif ;
- affectation d’un actif disponible à une personne, classe, département, service ou autre bénéficiaire, avec lieu, état de départ, échéance et responsabilité explicite ;
- restitution contrôlée avec date, emplacement et état constaté ; l’affectation reste conservée et l’actif redevient disponible sans réécrire son passé ;
- statuts `AVAILABLE`, `ASSIGNED`, `MAINTENANCE`, `LOST` et `RETIRED` historisés ; un actif affecté doit être restitué avant tout changement de statut ;
- cinq permissions distinctes `inventory.read/write/assign/adjust/configure` et rôle `INVENTORY_MANAGER`, avec refus confirmé pour le profil élève ;
- interface `/app/inventory` responsive : parc identifié, étagères de stocks, mouvements, carte des lieux, fil historique et registre des affectations, avec sept formulaires guidés ;
- démonstration idempotente : quatre lieux, trois références de stock, trois actifs, quatre mouvements, deux affectations et sept événements ; ordinateur confié au professeur de mathématiques, tablette restituée et projecteur en maintenance ;
- migration Flyway V31 validée sur H2 et PostgreSQL 18.4, avec clés étrangères composites tenant-scoped, contraintes de quantité, statut et cohérence des mouvements ;
- validation : 108 tests backend réussis, Spring Modulith valide, PostgreSQL V1–V31 réussi, 74 tests frontend dans 27 fichiers, lint et build Angular de production réussis ;
- recette réelle : 120 livres, 80 uniformes en alerte sous le seuil de 100, 45 kits répartis sur deux lieux, quatre rubriques et formulaire d’actif vérifiés ; le débordement intermédiaire détecté a été corrigé puis revalidé à 1 265 px.

## M28 — Achats et fournisseurs

- workflow tenant-scoped complet `demande → approbation → commande → réception → facture → paiement`, avec configuration, seuils d'approbation, lignes, décisions, réceptions partielles, rapprochements et règlements fournisseurs ;
- source de vérité unique pour les fournisseurs, factures et dépenses : le module Achats réutilise les objets comptables M27 et les échanges inter-modules passent par un port public explicite ;
- montants calculés côté serveur, seuils non chevauchants et nombre d'approbations configurable de un à cinq ; une commande ne peut naître que d'une demande entièrement approuvée ;
- contrôle de réception empêchant tout dépassement commandé et autorisant ou refusant les livraisons partielles selon la politique de l'école ;
- rapprochement à trois pièces strict : une facture fournisseur n'est comptabilisée qu'après réception complète et égalité exacte entre commande, réception et facture ;
- paiement borné au solde restant, avec génération automatique d'une écriture équilibrée ; le règlement total propage l'état `PAID` jusqu'à la demande d'origine ;
- huit permissions distinctes `procurement.read/request/approve/order/receive/invoice/pay/configure`, avec profils achats, demandeur et comptable ;
- interface `/app/procurement` responsive et lisible : flux organique, demandes, commandes et réceptions, factures et paiements, référentiel fournisseurs et règles, plus formulaires latéraux guidés ;
- démonstration idempotente : achat pédagogique de 12 600 MAD entièrement livré, facturé et payé chez Papeterie Atlas, demande stratégique de 38 000 MAD en attente d'une seconde approbation et dépense directe de 1 200 MAD ;
- migration Flyway V30 validée sur H2 et PostgreSQL 18.4, avec onze nouvelles tables, contraintes composites tenant-scoped et index de suivi ;
- validation : 105 tests backend réussis dans 32 suites, Spring Modulith valide, PostgreSQL V1–V30 réussi, 70 tests frontend dans 26 fichiers, lint et build Angular de production réussis ;
- recette réelle : seuils, huit permissions, deux demandes, une commande, une réception, une facture rapprochée et un paiement visibles ; comptabilité équilibrée, quatre rubriques et formulaire de demande vérifiés sans débordement horizontal.

## M27 — Comptabilité en partie double

- module comptable optionnel et tenant-scoped : configuration, plan comptable, journaux, exercices, centres de coût, écritures, fournisseurs, factures, dépenses, comptes bancaires et rapprochements ;
- invariants de partie double imposés par le service, les contraintes SQL et les tests : au moins deux lignes, un seul côté strictement positif par ligne et total débit exactement égal au total crédit ;
- aucune API ni interface de saisie libre du Grand Livre : les écritures naissent uniquement d’opérations métier guidées, tandis qu’une correction produit une extourne qui conserve l’original ;
- passerelle événementielle du Family Ledger vers le General Ledger pour factures, paiements, annulations, avoirs, remboursements et ajustements, avec mappings configurés et déduplication par origine ;
- cycle fournisseurs et dépenses avec brouillons, comptabilisation contrôlée et ventilation analytique optionnelle ; rapprochement bancaire figé après contrôle exact du mouvement net ;
- permissions distinctes `accounting.read`, `accounting.configure`, `accounting.post` et `accounting.reconcile`, attribuées à la direction et au rôle comptable ;
- interface `/app/accounting` responsive : Grand Livre narratif, balance de contrôle, plan comptable, fournisseurs, dépenses, banque, rapprochement et formulaires latéraux guidés ;
- démonstration idempotente : dix comptes, cinq journaux, un exercice ouvert, deux centres de coût, quatre écritures équilibrées, un fournisseur, une facture, une dépense et un rapprochement terminé ;
- migration Flyway V29 validée sur H2 et PostgreSQL 18.4, avec quatorze nouvelles tables, contraintes composites tenant-scoped et contrôles comptables ;
- validation : 101 tests backend réussis, Spring Modulith valide, PostgreSQL V1–V29 réussi, 67 tests frontend dans 25 fichiers, lint et build Angular de production réussis ;
- recette réelle : 81 150 MAD au débit comme au crédit, deux événements famille reliés, quatre vues et tiroir de facture fonctionnels, aucune saisie directe du Grand Livre et aucun débordement horizontal.

## M26 — Paie configurable

- architecture tenant-scoped complète : périodes, composants de salaire, règles, profils salariaux, composants de profil, runs, bulletins, lignes, ajustements et exports comptables ;
- moteur reproductible prenant en charge salaire de base, indemnités, primes, heures supplémentaires, absences et retenues, sans intégrer aucun taux fiscal ou social marocain non vérifié ;
- règles entièrement configurables par montant fixe, pourcentage de base ou quantité × taux ; une règle déclarée juridiquement validée exige obligatoirement une source explicite ;
- cycle contrôlé `DRAFT → CALCULATED → FINALIZED → EXPORTED`, recalcul idempotent avant finalisation et bulletins immuables une fois figés ;
- exports comptables CSV ou JSON équilibrés, associés au run, dédupliqués et protégés par une empreinte SHA-256 ; les comptes de charge et de dette restent configurables par composant ;
- permissions séparées `payroll.read`, `payroll.configure`, `payroll.run`, `payroll.finalize` et `payroll.export`, attribuées selon les responsabilités de direction, RH et comptabilité ;
- avertissement permanent dans l’API et l’interface : la configuration livrée est une démonstration non validée juridiquement et les règles fiscales/sociales marocaines doivent être sourcées, vérifiées puis approuvées avant production ;
- interface `/app/payroll` responsive : cycle visuel de paie, contrôle des bulletins et de leurs lignes, règles et profils transparents, ajustements, formulaires latéraux, finalisation confirmée et téléchargement d’export ;
- démonstration idempotente d’août 2026 : six composants, trois règles non réglementaires, quatre profils RH existants, trois ajustements et quatre bulletins pour un net cumulé de 43 787,50 MAD ;
- migration Flyway V28 validée sur H2 et PostgreSQL 18.4, avec dix nouvelles tables, contraintes tenant-scoped et contrôles de cohérence ;
- validation : 97 tests backend réussis sans échec ni erreur, contrôle Spring Modulith valide, test PostgreSQL V1–V28 réussi, 64 tests frontend dans 24 fichiers, lint et build Angular de production réussis ;
- recette réelle : droits paie présents, API cohérente, calcul reproductible, finalisation et export idempotent testés, navigation dans les quatre rubriques, tiroir d’ajustement, dates françaises et absence de débordement horizontal.

## M25 — Ressources humaines

- référentiel tenant-scoped complet : départements, postes, employés, contrats, horaires hebdomadaires, demandes de congé, présence du personnel, documents RH et qualifications enseignantes ;
- respect strict de « one person / one record » : chaque employé réutilise le `Person` et le `StaffProfile` centraux existants, avec contraintes d’unicité et test SQL de non-duplication ;
- workflow de congé contrôlé `REQUESTED → MANAGER_REVIEW → APPROVED/REJECTED`, interdisant toute décision anticipée et auditant chaque transition ;
- permissions distinctes `hr.read`, `hr.write`, `hr.leave.review` et `hr.attendance.manage`, attribuées à la direction et au rôle `HR_MANAGER` selon leur responsabilité ;
- présence journalière idempotente avec statuts présent, à distance ou absent, heure d’arrivée et note opérationnelle ;
- dossier collaborateur consolidé : contrat actif, rythme hebdomadaire, historique des congés et présences, documents à expiration et qualifications vérifiées ;
- interface `/app/hr` responsive et sans débordement : annuaire vivant, congés, présence et organisation, avec design bleu organique, hiérarchie typographique lisible et formulaires latéraux ;
- démonstration idempotente : trois départements, trois postes et quatre collaborateurs existants — Fouad Mellouli, Nadia El Fassi, Leïla Chraïbi et Salma Bennani — avec contrats, horaires, congés, pointages, documents et qualifications ;
- migration Flyway V27 validée sur H2 et PostgreSQL 18.4, avec neuf nouvelles tables, clés étrangères composites et index tenant-scoped ;
- validation : 92 tests backend réussis, contrôle Spring Modulith sans dépendance illégale, migration PostgreSQL V1–V27 réussie, 61 tests frontend, lint et build Angular de production réussis ;
- recette réelle : droits RH présents dans le jeton administrateur, API avec 3 départements, 3 postes, 4 employés, 3 congés, 4 pointages et 1 document à renouveler, puis navigation navigateur dans les quatre rubriques et le dossier enseignant sans erreur ni débordement horizontal.

## M24 — Santé & infirmerie

- dossier santé tenant-scoped séparé du dossier académique : profil, allergies, consignes médicamenteuses, incidents factuels, visites à l’infirmerie, actions d’urgence et vaccinations optionnelles ;
- politique établissement activant ou désactivant explicitement le suivi vaccinal, sans rendre ce champ obligatoire pour les écoles qui ne le souhaitent pas ;
- cinq permissions distinctes `health.read`, `health.sensitive.read`, `health.write`, `health.vaccination` et `health.configure`, attribuées à la direction et au rôle `NURSE` selon leur responsabilité ;
- accès élève et famille refusé par défaut aux endpoints internes ; chaque ouverture d’un dossier sensible et chaque mutation produit une entrée dans l’audit append-only ;
- garde-fou métier et éditorial : SchoolZ ne génère aucun diagnostic, les formulaires ne consignent que des faits observés, des informations transmises et les actions réalisées ;
- interface `/app/health` lisible et responsive : indicateurs, annuaire élèves, synthèse confidentielle et six onglets dédiés avec formulaires latéraux ;
- intégration au dossier élève 360° : la Santé est désormais signalée comme disponible et redirige vers l’espace protégé uniquement pour les utilisateurs autorisés ;
- démonstration idempotente : `infirmiere.demo@schoolz.ma` / `SchoolZNurse2026!`, dossier de Yasmine avec allergie, consigne familiale, incident, visite, action et vaccination déclarée ;
- migration Flyway V26 validée sur H2 et PostgreSQL 18.4, avec huit nouvelles tables et contraintes tenant-scoped ;
- validation : 88 tests backend métier réussis, contrôle Spring Modulith sans dépendance illégale, migration PostgreSQL V1–V26 réussie, 58 tests frontend, lint et build Angular de production réussis ;
- recette réelle : API administration et infirmière, contrôle d’accès élève, audit d’ouverture, navigation entre les six sections, ouverture/fermeture du formulaire d’incident et absence de débordement horizontal à largeur intermédiaire.

## Extension post-M23 — Portail élève autonome et refonte UX

- identité dédiée : compte `STUDENT` relié tenant par tenant à un seul dossier élève grâce à `student_portal_profile`, permissions `student-portal.read/write` et migration Flyway V25 ;
- portail Angular `/student` hors du shell administratif : accueil personnel directement centré sur cinq flux — journée, dernières notes, travail à faire, derniers cours et prochains DS — sans hero ni mini-dashboard récapitulatif ;
- pages élève réelles `/student/day`, `/student/grades`, `/student/homework`, `/student/courses` et `/student/assessments`, chacune accessible par « Voir tout » et alimentée par l'endpoint complet `/api/v1/student-portal/all` ;
- agrégation sans duplication : emploi du temps, cahier de texte, évaluations, notes et absences restent la propriété de leurs modules métier ;
- sécurité de navigation : un élève est redirigé vers son portail après connexion et ne peut pas ouvrir `/app/**` ; un autre rôle ne peut pas ouvrir le portail élève ;
- démonstration idempotente : `yasmine.eleve@schoolz.ma` / `SchoolZStudent2026!`, reliée à Yasmine Alaoui et aux données académiques 2026–2027 existantes ;
- refonte transversale : palette bleue/violette avec accents or et menthe, fonds illustrés en CSS, surfaces asymétriques, panneaux regroupés en flux et diminution de l'effet « grille de rectangles » ;
- lisibilité : base typographique à 16 px, libellés et métadonnées remontés à 12–15 px minimum, navigation et contrôles agrandis, largeurs utiles augmentées ;
- validation frontend : ESLint, Prettier, 55 tests Vitest dans 21 fichiers et build Angular de production réussis ;
- validation backend : suite complète réussie avec 86 tests, zéro échec, zéro erreur et zéro test ignoré ; scénario connexion/agrégation/statut de devoir inclus, migration V25 validée sur H2 et stack PostgreSQL reconstruite ;
- robustesse calendrier : le contrôle global a révélé puis corrigé le calcul UTC d'une journée de garderie autour de minuit ; les dates opérationnelles utilisent désormais le fuseau scolaire `Africa/Casablanca` ;
- recette réelle : connexion élève, redirection `/student`, garde `/app/**`, agrégation de 2 cours, 3 devoirs, 3 DS, 3 notes et 1 cours publié, mutation puis restauration d'un devoir, portail famille refondu et largeur 390 px sans débordement ; aucune erreur console.

## M18–M23 — état exact

- M18 Communications : sept tables, annonces école/campus/classe, conversations administration/professeur–famille, notifications automatisées, cinq canaux et préférences ; adaptateurs externes honnêtement limités à `PREVIEW`.
- M19 Documents : dix types, catégories, versions, expiration, six modèles PDF, grants famille, empreintes SHA-256 et liens signés temporaires ; port de stockage S3 compatible avec adaptateur JDBC de démonstration explicite.
- M20 Signatures : workflow strict généré/envoyé/vu/accepté/signé, version figée, signataire, IP, timestamps, événements append-only et provider local non qualifié.
- M21 Transport : véhicule, chauffeur, accompagnateur, circuit, arrêt, affectation, abonnement, trajet, montée, descente et position ; `GpsProvider`, présence bus, capacité et ETA conditionnelle.
- M22 Cantine : plans mensuel/ponctuel, menus, abonnements, présences, allergènes, restrictions protégées et charges `CAFETERIA` automatiques dans le ledger.
- M23 Activités : cinq exemples, coachs, sessions, capacité verrouillée, inscription parent autorisée, liste d'attente et charges `ACTIVITY` automatiques.
- Backend : 322 sources Java principales, 33 migrations Flyway et 117 tests unitaires/API/architecture réussis après M31 ; Spring Modulith valide les frontières des modules.
- Frontend : ESLint et Prettier propres, 81 tests répartis dans 29 fichiers via le runner Angular officiel et build de production réussi.
- PostgreSQL : migrations V1 à V33 appliquées sur PostgreSQL 18.4 ; les tables M18–M31 et le mapping du portail élève sont tenant-scoped.

## Validation du jalon 23

- backend : `mvn verify` réussi avec 85 tests au total, zéro échec et zéro test ignoré ; scénarios M18–M23 couvrant les messages, documents, signatures, suivi transport, données cantine et inscription aux activités ;
- migrations : V1 à V24 validées sur H2 de test puis réellement appliquées sur PostgreSQL 18.4 ;
- architecture : contrôle Spring Modulith réussi sans cycle ni accès Java illégal entre modules ;
- frontend : lint, formatage, 53 tests et build de production réussis ;
- Docker : stack complète reconstruite et démarrée avec PostgreSQL 18.4, Redis, MinIO, backend sain, frontend et reverse proxy sur `http://localhost:8088` ;
- smoke test HTTP : santé `UP`, frontend HTTP 200 et douze dashboards M18–M23 vérifiés en administration et en famille ;
- recette navigateur : dashboard et six modules administrateur, portail famille et six modules publics, français et arabe RTL, données de démonstration visibles et aucune erreur console ;
- sécurité métier : accès famille limité aux enfants liés, santé cantine limitée à la famille concernée ou à la permission dédiée, URLs documentaires temporaires et audit de signature conservé ;
- limites honnêtes : canaux externes en `PREVIEW`, signature locale non qualifiée, GPS local de démonstration et stockage documentaire JDBC à remplacer par les adaptateurs de production.

## Jalons terminés

### M0 — Fondation

- monolithe modulaire Spring Boot/Angular, PostgreSQL, Redis, MinIO et Docker Compose ;
- PWA responsive bilingue français/arabe avec RTL ;
- Flyway, CI, formatage, lint, tests, OpenAPI, Actuator et documentation d'architecture.

### M1 — Identité et sécurité

- comptes multi-tenant, 18 rôles de base, permissions fines, BCrypt et verrouillage ;
- JWT court, refresh token rotatif HttpOnly, détection de rejeu et révocation ;
- vérification e-mail, reset, audit append-only et préparation MFA/TOTP ;
- écrans login/récupération, dashboard et administration utilisateurs/rôles.

### M2 — Organisations et campus

- `Organization`, `SchoolSettings` et plusieurs `Campus` sous un seul tenant ;
- année scolaire, périodes, cycles, niveaux, niveaux proposés, salles et classes ;
- clés étrangères composites `(id, tenant_id)` empêchant les relations cross-tenant ;
- écrans Angular « Établissement & campus » et « Académique », seed multi-campus FR/AR/RTL.

### M3 — Personnes, familles et élèves

- enregistrement central `Person`, adresses et moyens de contact normalisés ;
- `Household`, profils élève/responsable, relations familiales et contacts d'urgence ;
- inscriptions historiques par campus, année et classe, identifiants scolaires uniques ;
- champs personnalisés, documents et photo sous forme de clés objet ;
- dossier élève 360° : identité, famille, scolarité, absences, notes, documents, finance, transport, santé et historique ;
- les sections dépendant de futurs modules sont explicitement signalées, sans donnée fictive ;
- aucun endpoint de suppression physique : l'archivage conserve le dossier, l'historique et clôture l'inscription ;
- permissions `person.*` et `student.*`, audit des mutations et isolation tenant jusque dans les clés étrangères ;
- annuaire et fiche 360° Angular responsive, français/arabe et RTL ;
- seed local : Yasmine Alaoui, sa responsable, son foyer, son inscription, un document et un champ personnalisé.

### M4 — Import et migration de données

- assistant complet `Upload → Mapping colonnes → Validation → Preview → Import` pour CSV UTF-8 et Excel XLSX ;
- six domaines pris en charge de bout en bout : élèves, parents/responsables, personnel, classes, paiements historiques et anciens soldes ;
- suggestions automatiques de mapping avec alias français, tout en exigeant la confirmation des champs obligatoires ;
- validation ligne par ligne des formats, dates ISO, montants décimaux, doublons, contacts et références tenant-scoped ;
- rapport précis conservant le numéro de ligne source, le champ et le motif de chaque anomalie ;
- import complet dans une transaction unique : si une ligne échoue, toutes les écritures métier du job sont annulées ;
- historique des jobs et lignes d'import, compteurs, statuts, audit et permissions `data-import.read/write` ;
- création du profil personnel minimal ; les paiements et soldes repris restent en zone contrôlée `PENDING` jusqu'au rapprochement par le futur module finance ;
- six modèles Excel professionnels avec exemples, guide des champs, formats de dates/montants et validation des booléens ;
- interface Angular responsive FR/AR/RTL avec reprise d'un job, téléchargement authentifié des modèles et historique.

### M5 — Admissions CRM

- modèle tenant-scoped complet pour `Prospect`, `Applicant`, `Application`, historique `ApplicationStatus`, `AdmissionCampaign`, `AdmissionStep`, `AdmissionDocument`, `Visit`, `Interview`, `AdmissionTest`, `AdmissionDecision` et `EnrollmentOffer` ;
- pipeline métier à 11 états, de `NEW_LEAD` à `ENROLLED`, avec transitions autorisées explicitement et historique append-only ;
- CRM interne protégé par `admissions.read/write` : campagnes, prospects, Kanban déplaçable, visites, entretiens, tests, revue documentaire, décisions et offres ;
- dashboard par campagne : prospects, dossiers, conversion, origine des leads, dossiers incomplets, acceptations, inscriptions et taux d'acceptation ;
- portail public bilingue FR/AR : création de compte parent, session révocable en cookie HttpOnly, ajout d'enfants, création et mise à jour du dossier, pièces PDF/JPEG/PNG, suivi et notifications ;
- contrôle des pièces obligatoires avant soumission, empreinte SHA-256, taille maximale de 5 Mo, type MIME autorisé et téléchargement strictement limité au compte propriétaire ;
- frais de dossier de démonstration, décision, émission d'une offre et acceptation par le parent jusqu'au statut `ENROLLED` ;
- isolation cross-tenant testée sur les comptes, enfants, dossiers et documents ;
- seed local avec une campagne ouverte, trois prospects, cinq dossiers répartis dans le pipeline et un compte parent de démonstration.

### M6 — Inscription / réinscription

- workflow persistant et reprenable à huit étapes : contrat, informations famille, services, documents, signature, frais, classe et activation ;
- deux origines strictes : admission avec offre acceptée pour `INITIAL`, ou élève déjà lié au compte parent pour `RE_ENROLLMENT` ;
- contrat versionné et accepté, instantané famille confirmable/modifiable, sélection transport/circuit, cantine, activités et plan mensuel/trimestriel/annuel ;
- upload privé PDF/JPEG/PNG limité à 5 Mo, type de pièce obligatoire et empreinte SHA-256 ;
- signature électronique avec consentement, signataire, IP, horodatage et empreinte ; frais d'inscription `DEMO_CARD` explicites ;
- administration protégée par `enrollment.read/write` : liste des parcours, contrôle de complétude, classes compatibles, affectation et activation ;
- activation atomique d'une admission en personne, foyer, responsable, profil élève, identifiant et inscription académique ;
- réinscription annuelle conservant l'élève et préremplissant les informations existantes avant d'ajouter l'inscription de l'année cible ;
- contraintes tenant-scoped et unicité par admission ou par couple élève/année ; session parent partagée de manière sûre sous `/api/v1/public` ;
- portail parent bilingue FR/AR guidé et interface interne responsive avec indicateurs, progression et actions d'affectation ;
- seed local créant une offre acceptée et un parcours M6 prêt à être parcouru avec le compte parent de démonstration.

### M7 — Crèche et garderie

- verticale petite enfance dédiée, séparée des écrans académiques génériques, avec `ChildcareProfile`, groupe, éducateur référent, horaires attendus et consignes repas/biberon/sommeil/toilette ;
- liste tenant-scoped des personnes de récupération `AUTHORIZED` ou `PROHIBITED`, avec refus métier et test du départ confié à une personne interdite ;
- journal quotidien typé couvrant arrivée, départ, repas, biberon, sommeil, change, toilette, activité, humeur, observation, incident, température manuelle et médicament ;
- journal `DRAFT` modifiable puis `PUBLISHED` immuable, avec synthèse et Daily Report chronologique visible uniquement par le parent explicitement lié à l'élève ;
- politique médicaments par campus : activation explicite, permission `childcare.medication` et référence d'autorisation obligatoire selon configuration ;
- aucune logique de diagnostic ou de prescription : températures et incidents restent des mesures et observations factuelles ;
- permissions `childcare.read/write/publish/medication`, rôles éducateur, superviseur, infirmier et direction configurés selon responsabilité ;
- audit append-only des profils, arrivées, départs, événements et publications, sans recopier les notes sensibles dans les métadonnées ;
- écran éducateur mobile et très rapide : arrivée en un clic, actions repas/biberon/change/toilette, saisies guidées sieste/activité/humeur/observation/incident/température, départ sécurisé et publication ;
- portail parent FR/AR/RTL sur `/childcare`, en lecture seule, avec synthèse, humeur, arrivée/départ et chronologie du jour ;
- seed local idempotent : groupe « Les Papillons », éducatrice Salma Bennani, Amine El Idrissi, personnes autorisée/interdite, politique médicament désactivée et journal publié de sept événements.

### M8 — Présences et vie scolaire

- modèle tenant-scoped complet : `AttendanceSession`, `AttendanceRecord`, `Absence`, `LateArrival`, `EarlyDeparture`, `ExcusedAbsence`, `AbsenceReason`, notification parent, `DisciplinaryIncident`, `Sanction` et `TeacherObservation` ;
- séance `OPEN` puis `SUBMITTED`, initialisée automatiquement avec tous les élèves actifs de la classe au statut `PRESENT` ;
- appel professeur optimisé smartphone : boutons présent/absent/retard/départ anticipé, minutes de retard, heure de départ, motif et note, sauvegarde intermédiaire puis clôture ;
- clôture atomique créant les faits d'absence, retard et départ anticipé ainsi que les notifications parent en application ;
- portail parent `/attendance` limité aux enfants explicitement liés : historique, notifications, justification avec motif, commentaire et référence de pièce ;
- workflow administratif `PENDING → APPROVED/REJECTED`, motif de décision, absence recalculée `EXCUSED` après approbation et notification du parent ;
- statistiques filtrables par période, classe ou élève : présents, absents, retards, départs anticipés, absences non justifiées, taux et tendance quotidienne ;
- vie scolaire : incident disciplinaire, sanction rattachée et observation professeur positive, neutre ou préoccupante avec visibilité parent configurable ;
- permissions séparées `attendance.read/write/manage` et `discipline.write`, affectées aux rôles concernés, avec audit append-only de toutes les mutations ;
- interface interne responsive avec vue du jour, feuille d'appel, validations en attente, courbe d'évolution et saisie rapide d'incident/observation ;
- seed local idempotent : séance de mathématiques soumise pour `1AP-A`, trois élèves, un présent, une absence non justifiée, un retard de douze minutes, notification parent, incident/sanction et observation positive.

### M9 — Emploi du temps

- modèle tenant-scoped complet : matières, plannings, périodes, créneaux, équipements de salle, affectations enseignant/matière/classe, cours, disponibilités enseignant/salle et contraintes configurables ;
- construction hebdomadaire manuelle avec filtre par classe, enseignant ou salle, glisser-déposer natif et sélection/toucher de secours sur mobile ;
- prévalidation serveur avant chaque création ou déplacement et contraintes uniques PostgreSQL empêchant aussi les collisions simultanées d'un enseignant, d'une salle ou d'une classe ;
- conflits bloquants pour occupation enseignant/salle/classe, indisponibilités, capacité, type et équipement de salle, volume hebdomadaire, maximum quotidien et heures consécutives ;
- préférences souples `AVOID_SLOT` et `PREFERRED_SLOT` pondérées séparément des contraintes bloquantes ;
- moteur Java pur et déterministe, sans dépendance HTTP ni UI, alimenté par un instantané immuable du planning ;
- optimisation en deux temps : preview de propositions et non-planifiés sans écriture, puis application transactionnelle uniquement après confirmation explicite ;
- publication rendant le planning immuable et protégée par une permission distincte ;
- permissions `timetable.read/write/optimize/publish`, affectées aux rôles concernés, avec audit append-only des mutations et isolation cross-tenant ;
- interface Angular FR/AR/RTL responsive : grille hebdomadaire, rail des affectations restantes, formulaires matières/affectations/contraintes/disponibilités/équipements, dialogue de conflits et validation de l'optimiseur ;
- seed local idempotent : planning primaire brouillon, 35 créneaux, trois matières, trois enseignants, trois salles, trois affectations, cinq cours manuels, quatre contraintes, trois disponibilités et quatre propositions d'optimisation.

### M10 — Notes, évaluations et compétences

- modèles tenant-scoped complets pour barèmes numériques et de compétences, politiques par cycle, types d'évaluation, coefficients, évaluations, notes, historique, compétences, observations, appréciations, modèles et instantanés de bulletins ;
- quatre modes explicites : `NONE` pour la crèche, `COMPETENCY_ONLY` pour la maternelle, `HYBRID` pour le primaire et `NUMERIC` pour le secondaire, tous configurables par établissement ;
- moteur Java pur sans formule unique imposée : moyenne pondérée ou moyenne par catégories, poids configurables, gestion explicite des catégories manquantes, suppression des notes les plus faibles, arrondi et coefficients matières ;
- saisie des statuts noté, absent, dispensé ou non rendu, normalisation sur le barème du cycle et refus serveur de toute note numérique pour un enfant de crèche ;
- historique append-only de chaque création ou correction avec ancienne/nouvelle valeur, statut, motif obligatoire, acteur et horodatage ;
- compétences hiérarchiques par cycle et matière, évaluations par degré de maîtrise, observation pédagogique et appréciations enseignant avec visibilité famille préparatoire ;
- calcul transactionnel des brouillons de bulletins, résultats par matière figés, moyenne générale et classement optionnel selon la politique du cycle ;
- permissions séparées `grade.read`, `grade.write`, `grade.configure` et `report-card.calculate`, audit des mutations et isolation cross-tenant ;
- interface Angular FR/AR/RTL responsive : carnet matriciel, historique visuel, compétences, synthèses, classement, calcul des bulletins et configuration complète du cycle ;
- seed local idempotent : politiques crèche/maternelle/primaire/collège, deux barèmes, types, coefficients, compétences, trois évaluations, notes historisées, observations, appréciations et modèles de bulletins.

### M11 — Bulletins PDF versionnés

- moteur PDF A4 fondé sur PDFBox, avec polices embarquées/résolues pour le français, l'arabe correctement façonné et le rendu bilingue ;
- contenu complet : établissement et logo SchoolZ, élève, classe, période, matières, notes, moyennes, coefficients, commentaires, compétences, absences, retards, appréciation, signature et référence de document ;
- templates configurables par cycle, couleur, langue et options de mise en page, sans HTML externe ni service tiers ;
- prévisualisation filigranée sans écriture, puis publication immuable avec nom de fichier, binaire, taille, empreinte SHA-256, signataire, note, instantané et horodatage ;
- correction post-publication créant une nouvelle ligne numérotée avec `supersedes_report_card_id`, tandis que chaque PDF antérieur reste téléchargeable et vérifiable ;
- endpoints de preview, publication, téléchargement et historique protégés par `report-card.publish`/`grade.read`, contrôle tenant et audit append-only ;
- interface Angular FR/AR/RTL : versions publiées, prévisualisation, choix de langue, signature, note de publication, téléchargement, preuve SHA-256 et historique chronologique ;
- seed local idempotent publiant les bulletins de démonstration et conservant les versions successives utilisées pour vérifier la traçabilité ;
- trois PDF de référence contrôlés visuellement page par page dans `output/pdf/` : français, arabe et bilingue.

### M12 — Cahier de texte et devoirs

- modèles tenant-scoped `LessonContent`, `Homework`, `Attachment` et statut élève `TO_DO`/`DONE`, reliés aux affectations professeur–matière–classe et aux périodes académiques existantes ;
- contenus de cours et devoirs créés en brouillon, fichiers ajoutés avant publication, puis publication immuable et clôture explicite des devoirs ;
- échéance horodatée, durée estimée, calendrier par classe/période et progression agrégée sans confondre le statut « fait » avec une remise ou une note ;
- pièces PDF/JPEG/PNG/TXT/DOCX/PPTX limitées à 10 Mo, nom assaini, empreinte SHA-256 et téléchargement autorisé par tenant ou lien parent-élève ;
- permissions séparées `homework.read/write/publish`, audit append-only et défense en profondeur avec clés étrangères composites incluant `tenant_id` ;
- portail interne FR/AR/RTL : filtres classe/période/dates, indicateurs, création guidée, pièces jointes, publication, clôture et suivi de complétion ;
- portail famille `/homework` multi-enfants : calendrier des échéances, cours publiés, téléchargement et bascule `À faire/Fait` depuis la session parent sécurisée ;
- périmètre volontairement borné : aucun dépôt de copie, quiz, forum, correction ou notation en ligne ; `HomeworkSubmission` n'est pas créé ;
- seed local idempotent : un cours de mathématiques, trois devoirs, deux pièces jointes et le premier devoir marqué fait pour Yasmine.

### M13 — Portail professeur

- relation tenant-scoped explicite `teacher_portal_profile` entre le compte interne et le `StaffProfile`, sans correspondance implicite par nom ou e-mail ;
- projection personnelle des seuls cours de plannings publiés, avec matière, classe, salle, horaires, état chronologique, appel associé, résumé du jour et prochaine journée enseignée ;
- permission dédiée `teacher-portal.read` ajoutée au rôle professeur, tandis que chaque action rapide dépend aussi de la permission du module cible ;
- raccourcis contextuels vers l'appel, les notes, le devoir et l'observation, avec classe, matière, affectation, professeur et date préremplis ;
- message explicitement marqué « bientôt » jusqu'au jalon communications, sans faux écran ni fausse donnée ;
- tableau de bord Angular mobile-first FR/AR/RTL, navigation de date, état sans cours, carrousel d'actions horizontal borné au mobile et accès direct après connexion professeur ;
- compte de démonstration `prof.math@schoolz.ma`, lié à Fouad Mellouli et à l'emploi du temps publié `CENTRE-2026` ;
- intégration avec les écrans existants sans déplacer la propriété des mutations hors de `attendance`, `grading` et `homework`.

### M14 — Portail famille

- compte famille unique reposant sur la session parent opaque existante, avec liste des seuls enfants explicitement liés et préférence persistante tenant-scoped ;
- sélection initiale privilégiant l'enfant scolarisé, puis changement Yasmine/Amine en un clic sans nouvelle connexion ;
- façade `familyportal` en lecture seule : identité scolaire, campus, classe, présence du jour, dernières notes publiées, prochain devoir, emploi du temps publié, bulletins PDF et absences ;
- téléchargement d'un bulletin publié après nouvelle vérification du triplet compte parent–enfant–tenant ;
- aucune seconde source de vérité : les modules `timetable`, `grading`, `homework`, `attendance` et `childcare` restent propriétaires de leurs données et mutations ;
- cartes finance, messages, documents, transport et activités honnêtement indisponibles avec leur jalon cible, sans valeur fictive ;
- PWA `/family` installable, manifeste avec raccourcis famille/devoirs/absences, cache limité à l'enveloppe statique et exclusion complète de `/api/**` ;
- interface Angular FR/AR/RTL responsive : connexion directe, démonstration en un clic, sélecteur d'enfant, synthèse du jour, modules, calendrier, notes, bulletins et absences ;
- les portails devoirs, assiduité et préscolaire reprennent le `studentId` choisi dans leurs liens profonds ;
- seed local : compte famille de démonstration lié à Yasmine Alaoui (`1AP — Groupe A`) et Amine El Idrissi (`Les Papillons`).

### M15 — Finance I / Family ledger

- module `billing` tenant-scoped couvrant `FamilyAccount`, charges, factures/lignes, paiements/allocations, avoirs, remboursements, remises, échéanciers préparatoires, reçus et ajustements financiers ;
- catalogue de services : scolarité, inscription, transport, cantine, activité, garderie et autres frais, avec bénéficiaire optionnel et montant décimal exact en MAD ;
- factures composables en brouillon puis immuables après émission, avec numérotation tenant/année sérialisée et contraintes d'unicité ;
- allocations plusieurs-à-plusieurs : un paiement peut couvrir plusieurs factures et une facture recevoir plusieurs paiements ;
- reçus à numéro unique, ledger append-only, avoirs, remboursements et ajustements de débit/crédit ;
- aucun endpoint de suppression d'un paiement validé : l'annulation complète crée une écriture inverse, des allocations négatives et un avis de reçu, tous audités ;
- cockpit Angular `/app/finance` : portefeuille familles, synthèse, ledger, détail, composition de facture, émission, ventilation d'un encaissement, reçus et annulation contrôlée ;
- portail famille `/finance` FR/AR/RTL : connexion directe, total, payé, reste, factures détaillées, mouvements et reçus imprimables ;
- seed local : `FAM-DEMO-0001`, scolarité 32 000 MAD, transport 6 000 MAD, cantine 3 500 MAD, remise fratrie 2 000 MAD, paiement 38 000 MAD et reste 1 500 MAD.

### M16 — Finance II / Échéanciers et recouvrement

- échéanciers mensuels, trimestriels, annuels ou personnalisés, avec positions, dates, montants, paiements affectés et statuts calculés `UPCOMING`, `DUE`, `PARTIALLY_PAID`, `PAID` ou `OVERDUE` ;
- un même encaissement peut être ventilé sur les factures et pointé sur les échéances correspondantes sans doubler le montant du paiement ; l'annulation produit aussi les allocations d'échéance négatives ;
- cockpit de recouvrement à date : attendu, encaissé, taux, impayé, nombre d'échéances et aging strictement supérieur à 7, 30 et 60 jours ;
- règles configurables J−3, J+1, J+7 et J+15 avec canal `PUSH`, `EMAIL`, `SMS` ou `WHATSAPP`, mode manuel/automatique et activation indépendante ;
- port `ReminderChannelAdapter` et adaptateur local explicite `PREVIEW` : aucune livraison externe n'est prétendue tant qu'un fournisseur n'est pas configuré ;
- préparation automatique idempotente par règle, échéance et date de déclenchement, ainsi que relance manuelle auditée ;
- permissions distinctes `collections.read/manage`, isolation tenant et compte, contraintes composites et historique des actions de recouvrement ;
- cockpit Angular `/app/finance` enrichi : métriques, aging, détail des dates, création de calendrier, relance manuelle, règles, actions récentes et ventilation d'encaissement sur échéance ;
- portail parent `/finance` enrichi avec les dates, montants et états payés/à venir/en retard en français et en arabe ;
- seed local idempotent : `ECH-DEMO-2026-0001`, trois échéances de 4 000 MAD, deux réglées, une en retard et quatre règles de rappel de démonstration.

### M17 — Paiement en ligne

- port `PaymentProvider` indépendant du fournisseur avec les quatre opérations `initializePayment`, `handleWebhook`, `verifyPayment` et `refund` ;
- fournisseur local `sandbox` uniquement : aucune marque bancaire marocaine ni capacité de débit réel n'est inventée sans documentation officielle ;
- intentions de paiement tenant-scoped et idempotentes, reliées au compte famille, à une facture et/ou à une échéance, avec montant décimal exact et expiration ;
- webhooks HMAC-SHA256 signés, journalisés et idempotents par événement fournisseur, sans persister le corps brut : seule son empreinte SHA-256 est conservée ;
- vérification fournisseur obligatoire avant comptabilisation, création unique du paiement `ONLINE_PAYMENT`, allocations et reçu via le ledger M15 ;
- nouvelle livraison du même webhook comptée mais jamais recomptabilisée ; signature invalide, événement malformé et fournisseur inconnu tracés avec leur état d'échec ;
- remboursement fournisseur complet et idempotent, matérialisé par une écriture compensatoire du ledger sans supprimer l'original ;
- permissions séparées `online-payment.read/manage/refund`, audit des initialisations, confirmations et remboursements, endpoint webhook public étroitement borné ;
- cockpit administratif `/app/finance` enrichi avec état fournisseur, vérification et chaîne d'opérations ; portail famille `/finance` FR/AR/RTL avec préparation et confirmation sandbox explicites ;
- démonstration honnête : la carte indique clairement qu'aucun débit bancaire réel n'est effectué.

## Validation du jalon 17

- backend : 73 tests unitaires, contextuels et API réussis, dont huit scénarios finance couvrant initialisation idempotente, signature invalide, webhook signé, double livraison, comptabilisation unique, vérification, remboursement et compensation ;
- PostgreSQL : test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V18 et les trois nouvelles tables de paiement, webhook et remboursement vérifiées ;
- frontend : ESLint et Prettier propres, 45 tests Vitest répartis dans 17 fichiers et build Angular de production réussi sans avertissement ;
- architecture : Spring Modulith valide les frontières et l'ADR-014 consigne le port fournisseur, la vérification avant ledger et le journal de webhooks sans corps brut ;
- Docker/HTTP : six services opérationnels, backend sain, migration V18 appliquée sur la base existante et vues administration/famille servies par l'API réelle ;
- contrôle navigateur réel : fournisseur sandbox `READY`, intention absente correctement expliquée, parcours famille français et arabe `dir="rtl"`, libellé « aucun débit bancaire réel » et console sans erreur ;
- sécurité : secret sandbox externe à l'application, HMAC vérifié en temps constant, idempotence événement et commande, empreinte de payload, permissions dédiées et audit append-only.

## Validation du jalon 16

- backend : 71 tests unitaires, contextuels et API réussis, dont six scénarios finance couvrant échéanciers, double ventilation, aging, annulation, règles, idempotence automatique, relance manuelle et audit ;
- PostgreSQL : test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V17 et trois nouvelles tables de recouvrement vérifiées ;
- frontend : ESLint et Prettier propres, 43 tests Vitest répartis dans 17 fichiers et build Angular de production réussi ;
- architecture : Spring Modulith valide les frontières et l'ADR-013 sépare le calcul de recouvrement des adaptateurs de livraison externes ;
- Docker/HTTP : six services opérationnels, backend sain, migration V17 appliquée sur la base existante et parcours administrateur/parent vérifiés via l'API réelle ;
- scénario réel : 12 000 MAD attendus, 8 000 MAD encaissés, taux de 66,67 %, 4 000 MAD impayés à plus de 30 jours, statuts `PAID, PAID, OVERDUE` ;
- contrôle navigateur réel : cockpit de recouvrement, quatre cadences, détail des échéances, double ventilation, règles J−3/J+1/J+7/J+15, portail parent et arabe avec `lang="ar-MA"`/`dir="rtl"` validés sans créer d'opération financière.

## Validation du jalon 15

- backend : 69 tests unitaires, contextuels et API réussis, dont quatre scénarios finance couvrant ventilation plusieurs-à-plusieurs, reçus uniques, annulation compensatoire, avoir/remboursement/ajustement et frontières d'accès ;
- PostgreSQL : test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V16 et tables du ledger financier vérifiées ;
- frontend : ESLint et Prettier propres, 41 tests Vitest répartis dans 17 fichiers et build Angular de production réussi ;
- architecture : Spring Modulith valide les frontières du module `billing` et l'ADR-012 consigne l'immutabilité des documents émis et des paiements validés ;
- Docker/HTTP : six services opérationnels, backend sain, migration V16 appliquée, dashboard administratif et ledger parent vérifiés via l'API réelle ;
- scénario réel : 39 500 MAD facturés, 38 000 MAD encaissés, 1 500 MAD restant, une facture partiellement réglée et un reçu `REC-DEMO-2026-0001` ;
- contrôle navigateur réel : portail famille français/arabe avec `lang="ar-MA"`/`dir="rtl"`, facture, remise, reçu imprimable, cockpit finance, formulaire de facture et ventilation d'un paiement vérifiés sans mutation des données de démonstration.

## Validation du jalon 14

- backend : 65 tests unitaires, contextuels et API réussis, dont trois scénarios M14 couvrant sélection/préférence multi-enfants, enfant non lié, session absente, date bornée et bulletin introuvable ;
- PostgreSQL : test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V15 et table `family_portal_preference` vérifiée ;
- frontend : ESLint et Prettier propres, 38 tests Vitest répartis dans 16 fichiers et build Angular de production réussi sans avertissement ;
- architecture : Spring Modulith valide les frontières et l'ADR-011 consigne la façade de lecture sans duplication de données ;
- Docker/HTTP : six services opérationnels, backend sain, migration V15 appliquée sur la base existante et PWA disponible sur `http://localhost:8088/family` ;
- scénario réel : connexion parent, deux enfants, Yasmine sélectionnée, 3 notes, 3 bulletins, 2 absences, prochain devoir et emploi du temps du 1er septembre 2026 ;
- PDF réel : réponse HTTP 200 `application/pdf`, nom `bulletin-sz-2026-0001-v3-bilingual.pdf` et 43 110 octets après contrôle d'accès familial ;
- contrôle navigateur réel : changement Yasmine/Amine persistant, groupe « Les Papillons », modules non applicables correctement signalés, retour en haut après connexion, installation PWA visible, largeur 390 px sans débordement et arabe avec `lang="ar-MA"`/`dir="rtl"` validés.

## Validation du jalon 13

- backend : 62 tests unitaires, contextuels et API réussis, dont trois scénarios M13 couvrant journée personnelle, actions autorisées, compte non lié et garde-fou de date ;
- PostgreSQL : test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V14 et table de liaison professeur vérifiée ;
- frontend : ESLint propre, 36 tests Vitest répartis dans 15 fichiers et build Angular de production réussi ;
- architecture : Spring Modulith valide les frontières et l'ADR-010 consigne la relation d'identité et la façade d'agrégation sans seconde source de vérité ;
- Docker/HTTP : six services opérationnels, backend sain, migration V14 appliquée, compte professeur lié et planning de démonstration publié ;
- contrôle navigateur réel : connexion professeur, date du 1er septembre 2026, appel prérempli, notes et devoir contextualisés, absence de débordement global à 390 px et arabe avec `lang="ar-MA"`/`dir="rtl"` validés ;
- sécurité locale : les origines explicites `localhost` et `127.0.0.1` sont admises sur les ports de développement, sans joker CORS.

## Validation du jalon 12

- backend : 59 tests unitaires, contextuels et API réussis, dont trois scénarios M12 couvrant publication immuable, fichiers, création des statuts, calendrier parent, changement `TO_DO/DONE`, refus d'un autre enfant et isolation tenant ;
- PostgreSQL : test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V13 et quatre tables M12 vérifiées ;
- frontend : ESLint et Prettier propres, 35 tests Vitest répartis dans 14 fichiers et build Angular de production réussi ;
- architecture : Spring Modulith valide l'absence de cycle et d'accès à un type interne ; périmètre « cahier de texte, pas LMS » consigné dans l'ADR-009 ;
- Docker/HTTP : migration V12 vers V13 appliquée sur la base existante, six services opérationnels, backend sain et interface disponible sur `http://localhost:8088` ;
- données réelles : 1 cours, 3 devoirs, 2 pièces jointes et 9 statuts élève présents dans PostgreSQL après le seed ;
- contrôle navigateur réel : vue professeur, progression 1/3, calendrier parent, téléchargement visible, changement de statut puis restauration et rendu arabe avec `dir="rtl"` validés dans le navigateur intégré.

## Validation du jalon 11

- backend : 56 tests unitaires, contextuels et API réussis, dont génération PDF dans les trois langues, publication immuable, création d'une correction versionnée et refus cross-tenant ;
- PostgreSQL : 1 test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V12 et les colonnes documentaires/versionnées vérifiées ;
- frontend : ESLint et Prettier propres, 32 tests Vitest répartis dans 13 fichiers et build Angular de production réussi sans avertissement ;
- architecture : stratégie de publication immuable, stockage du PDF et chaîne de versions documentés dans l'ADR-008 ;
- Docker/HTTP : migration V11 vers V12 appliquée sur la base existante, six services opérationnels, backend sain et interface disponible sur `http://localhost:8088` ;
- PDF réels : `schoolz-bulletin-demo-fr.pdf`, `schoolz-bulletin-demo-ar.pdf` et `schoolz-bulletin-demo-bilingual.pdf`, chacun en A4 sur une page, rendus en PNG à 150 DPI puis inspectés visuellement ;
- traçabilité réelle : les versions correctives référencent leur prédécesseur, les empreintes SHA-256 sont persistées et l'API restitue l'historique complet ;
- contrôle navigateur réel : 11 versions publiées visibles, modal avec langue, empreinte, signataire, historique v1→v3 et actions de téléchargement validée dans le navigateur intégré.

## Validation du jalon 10

- backend : 51 tests unitaires, contextuels et API réussis, dont cinq tests M10 couvrant deux stratégies de calcul, catégories manquantes, suppression de note, historique, classement, crèche et isolation tenant ;
- PostgreSQL : 1 test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V11 et les treize tables M10 vérifiées ;
- frontend : ESLint et Prettier propres, 31 tests Vitest répartis dans 13 fichiers et build Angular de production réussi sans avertissement ;
- architecture : moteur de calcul Java pur, configuration persistée et choix de séparation documentés dans l'ADR-007 ;
- Docker/HTTP : migration V10 vers V11 appliquée sur PostgreSQL 18.4 existant, six services opérationnels, backend `UP`, connexion administrateur et page `/app/grading` en HTTP 200 ;
- données de démonstration vérifiées par API : 4 politiques de cycle, 2 barèmes, 3 types, 2 compétences, 1 modèle, 3 évaluations, 9 notes, 10 événements d'historique, 3 synthèses et 3 bulletins calculés ;
- contrôle navigateur réel : carnet de 3 élèves, correction historisée `15 → 16` avec motif, calcul des 3 bulletins, politique collège avec classement et interface arabe en `dir="rtl"` vérifiés dans le navigateur intégré.

## Validation du jalon 9

- backend : 46 tests unitaires, contextuels et API réussis, dont trois scénarios M9 couvrant conflits, capacité/équipement, glisser-déposer, suppression, optimisation, publication et isolation tenant ;
- PostgreSQL : 1 test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V10 et les dix tables M9 vérifiées ;
- frontend : ESLint et Prettier propres, 27 tests Vitest répartis dans 12 fichiers et builds Angular développement/production réussis sans avertissement ;
- architecture : contrôle Spring Modulith réussi sans cycle ni accès Java à l'interne d'un autre module ; séparation du moteur documentée dans l'ADR-006 ;
- Docker/HTTP : migration V9 vers V10 appliquée sur PostgreSQL 18.4 existant, cinq services sains, connexion administrateur réussie et page `/app/timetable` en HTTP 200 ;
- données de démonstration vérifiées par API : un planning brouillon, 7 périodes, 35 créneaux, 3 matières, 3 enseignants, 3 salles, 3 affectations, 5 cours, 4 contraintes, 3 disponibilités et 4 propositions ;
- contrôle navigateur réel : login de démonstration, navigation, rendu de la grille et preview de l'optimiseur vérifiés dans le navigateur intégré.

## Validation du jalon 8

- backend : 41 tests unitaires, contextuels et API réussis, dont trois scénarios M8 couvrant le cycle absence/notification/justification/validation, l'accès parent croisé refusé et la vie scolaire ;
- PostgreSQL : 1 test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V9 et les onze tables M8 vérifiées ;
- frontend : ESLint, Prettier, 23 tests Vitest répartis dans 11 fichiers et builds Angular développement/production réussis ;
- architecture : contrôle Spring Modulith réussi sans cycle ni accès Java à l'interne d'un autre module ;
- Docker/HTTP : migration V8 vers V9 appliquée sur PostgreSQL 18.4 existant, cinq services sains, connexions administrateur et parent réussies, pages `/app/attendance` et `/attendance` en HTTP 200 ;
- données de démonstration vérifiées par API : une séance soumise, trois appels, un présent, un absent, un retard, une absence non justifiée et une notification parent.

## Validation du jalon 7

- backend : 38 tests unitaires, contextuels et API réussis, dont trois scénarios M7 sur le cycle complet, une récupération interdite et le refus de médicament par politique ;
- PostgreSQL : 1 test Testcontainers réussi sur PostgreSQL 18.4, migrations V1 à V8 et les six tables M7 vérifiées ;
- scénario d'acceptation : arrivée, repas, sieste, change, activité, humeur, départ par personne autorisée, publication puis lecture du Daily Report par le parent lié ;
- frontend : ESLint, Prettier, 20 tests Vitest répartis dans 10 fichiers et builds Angular développement/production réussis ;
- architecture : contrôle Spring Modulith réussi sans cycle ni accès Java à l'interne d'un autre module ;
- Docker/HTTP : migration V7 vers V8 appliquée sur PostgreSQL 18.4 existant, seed M7 chargé, backend `UP`, pages `/childcare` et `/app/childcare` en HTTP 200, un enfant et son rapport publié de sept événements vérifiés avec les sessions administrateur et parent.

## Limites connues

- Docker Desktop doit être démarré pour l'environnement et le test PostgreSQL réel ;
- les fichiers et photos sont référencés par clé objet ; l'upload sécurisé sera branché avec le module documentaire ;
- les fichiers d'import sont lus puis transformés en lignes contrôlées ; leur binaire source n'est volontairement pas conservé dans PostgreSQL ;
- les paiements historiques et anciens soldes importés attendent le rapprochement du module finance avant de devenir des écritures financières ;
- le paiement des frais d'admission utilise un acquittement `DEMO_CARD` sans prestataire bancaire ; l'abstraction de paiement réelle appartient aux jalons finance ;
- les notifications Admissions sont disponibles dans le portail ; l'envoi e-mail/SMS/push sera branché par le module communications ;
- les notifications d'absence M8 sont disponibles immédiatement dans le portail parent ; aucun e-mail, SMS ou push externe n'est encore envoyé ;
- la référence de pièce d'une justification d'absence est une clé objet préparatoire ; l'upload sécurisé sera fourni par le module documentaire ;
- la matière d'une séance M8 reste un libellé figé ; son rapprochement automatique avec un `Lesson` M9 appartient au portail professeur et au cahier de texte ;
- les pièces Admissions sont bornées à 5 Mo et conservées en binaire privé dans PostgreSQL pour cette première verticale ; leur migration vers MinIO/S3 est requise avant production ;
- les pièces d'inscription suivent la même stratégie binaire privée transitoire et devront migrer vers le module documentaire avant production ;
- les frais d'inscription utilisent le même acquittement de démonstration ; aucun prestataire bancaire réel n'est branché à ce stade ;
- les opérations quotidiennes transport, cantine et activités sont disponibles ; leurs adaptateurs fournisseurs réels restent à configurer avant production ;
- la politique médicament de démonstration est volontairement désactivée ; son activation exige une décision établissement, une permission dédiée et, selon la politique, une autorisation parentale référencée ;
- les heures des événements sont persistées en UTC ; l'affichage navigateur applique la locale, tandis que la prise en compte complète du fuseau de chaque campus reste un chantier transversal ;
- l'optimiseur M9 est une heuristique gloutonne déterministe : il respecte les contraintes du snapshot mais ne prétend pas démontrer un optimum global ;
- les plannings M9 sont hebdomadaires et bornés par une période de validité ; les exceptions datées, jours fériés et remplacements ne sont pas encore modélisés ;
- les portails professeur et famille exposent les seuls emplois du temps publiés ; les exceptions datées restent à modéliser ;
- les PDF de bulletins sont conservés en binaire PostgreSQL pour garantir leur immutabilité ; une externalisation vers un stockage objet versionné pourra être ajoutée lors du module documentaire sans changer les identifiants ni les empreintes ;
- les pièces du cahier de texte sont conservées en binaire PostgreSQL pour cette verticale locale ; le module documentaire devra les externaliser vers un stockage objet privé avant production sans changer leurs identifiants ni empreintes ;
- le statut parent `DONE` est un repère d'organisation : il ne constitue ni un dépôt de copie, ni une preuve de remise, ni une note ;
- les notes numériques publiées sont exposées au portail famille ; le détail des compétences familiales reste à enrichir lors d'un jalon UX transversal ;
- le DMS utilise un stockage JDBC de démonstration ; l'adaptateur S3/MinIO privé doit être activé et testé avant production ;
- les rappels de recouvrement sont préparés et journalisés avec un statut `PREVIEW` ; aucun canal externe n'est encore appelé sans adaptateur fournisseur configuré ;
- le seul fournisseur de paiement branché est le sandbox local ; un prestataire bancaire réel exige sa documentation officielle, ses secrets de production et une revue de conformité dédiée ;

## Prochain jalon

Milestone 37 — API publique.
