# Roadmap produit

La roadmap suit les dépendances métier et le Definition of Done. Un milestone ne démarre que lorsque les critères du précédent sont vérifiés.

## Release 1 — School Core

1. **M0 Fondation — terminé** — monorepo, architecture, environnements, CI, migrations, shell Angular.
2. **M1 Identité et sécurité — terminé** — comptes, sessions, reset, vérification, RBAC/permissions, isolation tenant.
3. **M2 Organisations et campus — terminé** — configuration école, années, périodes, niveaux, classes et salles.
4. **M3 Personnes, familles et élèves — terminé** — one person/one record, households, inscriptions, fiche 360°.
5. **M4 Import — terminé** — CSV/Excel, mapping, validation, preview, rollback et modèles.
6. **M5 Admissions CRM — terminé** — prospects, candidatures, pipeline Kanban, décisions, offres et portail public.
7. **M6 Inscription / réinscription — terminé** — conversion d'une admission, documents, contrats, services, affectation et workflow annuel.
8. **M7 Crèche / garderie — terminé** — groupes petite enfance, présences, repas, siestes, changes, règles de récupération et Daily Report famille.
9. **M8 Attendance — terminé** — appel mobile, absences/retards/départs anticipés, notifications, justification parent, validation et statistiques.
10. **M9 Emploi du temps — terminé** — planning manuel, glisser-déposer, disponibilités, contraintes, conflits classe/professeur/salle et optimisation contrôlée.
11. **M10 Notes et compétences — terminé** — modèles configurables selon cycle, évaluations, compétences, coefficients, historique des notes, calculs et classements optionnels.
12. **M11 Bulletins — terminé** — PDF FR/AR/bilingue, publication immuable, templates, signatures, empreintes et versioning.
13. **M12 Cahier de texte — terminé** — contenus de cours, devoirs, échéances, pièces jointes, calendrier famille et statuts sans dérive vers un LMS complet.
14. **M13 Portail professeur — terminé** — journée mobile-first, emploi du temps personnel et raccourcis contextualisés vers appel, notes, devoirs et observations.
15. **M14 Portail famille — terminé** — PWA parent multi-enfants, scolarité, emploi du temps, devoirs, notes, bulletins et absences publiés.
16. **M15 Finance I — terminé** — family ledger, factures multi-services, remises, paiements ventilés, avoirs, remboursements, ajustements, reçus uniques et annulation compensatoire.
17. **M16 Finance II — terminé** — échéanciers mensuels, trimestriels, annuels ou personnalisés, retards, aging, recouvrement, rappels configurables et actions manuelles/automatiques.
18. **M17 Paiement en ligne — terminé** — abstraction fournisseur, sandbox, vérification, remboursement et webhooks signés/idempotents.
19. **M18 Communications — terminé** — conversations, messages, annonces, notifications, préférences et livraisons multicanales.
20. **M19 Documents — terminé** — DMS privé, catégories, versions, permissions, expirations et URLs temporaires signées.
21. **M20 Génération et validation — terminé** — documents générés, envoyés, consultés, acceptés et signés avec preuve d'audit.
22. **M21 Transport — terminé** — circuits, arrêts, véhicules, chauffeurs, accompagnateurs, inscriptions, présences et abstraction GPS.
23. **M22 Cantine — terminé** — menus, régimes, allergies à accès restreint, réservations, consommations et liaison finance.
24. **M23 Activités — terminé** — catalogue, sessions, capacité, liste d'attente, inscription parent et liaison finance.
25. **Extension post-M23 Portail élève autonome — terminée** — compte élève indépendant, agrégation pédagogique personnelle, journée, devoirs, DS, notes, cours, garde de routes et refonte UX lisible.
26. **M39–40 Recherche et notifications** — recherche globale latin/arabe et centre de notifications.

## Release 1.1 — Mise en service multi-écoles

- **Provisionnement opérateur — terminé** : création transactionnelle et idempotente d'un tenant, du catalogue de rôles, du propriétaire, de l'organisation, des réglages Maroc et du campus principal.
- **Activation des comptes — terminée** : invitation à usage unique et choix du mot de passe par l'utilisateur, sans secret initial partagé.
- **Rattachement des portails — terminé** : sélection obligatoire de la fiche personnel pour un professeur et du dossier scolaire pour un élève lors de la création du compte.
- **Exploitation — terminée** : secret dédié validé au démarrage, route bloquée au proxy public, script serveur et documentation opérateur.

## Release 2 — School Operations

- **M24 Santé / infirmerie — terminé et validé** — profils de santé, allergies, médicaments, incidents, visites, urgences et vaccinations optionnelles, avec accès fortement restreint et audit ;
- **M25 RH — terminé et validé** — employés, contrats, postes, départements, horaires, présence, qualifications et workflow de congés ;
- **M26 Paie configurable — terminé et validé** — périodes, composants, règles, profils salariaux, bulletins, ajustements et exports, sans taux marocains non vérifiés ;
- **M27 Comptabilité — terminé et validé** — plan comptable, journaux, écritures en partie double, périodes, centres de coût, banque et rapprochement ;
- **M28 Achats / fournisseurs — terminé et validé** — demandes, approbations, commandes, réceptions, factures fournisseurs et paiements ;
- **M29 Inventaire — terminé et validé** — équipements, stocks, mouvements, emplacements, affectations et historique ;
- **M30 Bibliothèque — terminé et validé** — ouvrages, exemplaires, réservations, prêts, retours, pénalités optionnelles et QR/barcode, visibles par les élèves et leurs familles.
- **M31 Analytics direction — terminé et validé** — KPI exécutifs, tendances, admissions, finances, capacité et comparaison multi-campus avec filtre daté.
- **M32 Alertes / early warning — terminé et validé** — moteur de règles factuelles, preuves, suivi humain et historique, sans diagnostic psychologique.
- **M33 Workflow engine — terminé et validé** — déclencheurs, conditions typées, actions, exécutions, journaux et protection contre les boucles.
- **M34 Massar et intégrations — terminé et validé** — adaptateurs fichiers, CSV/Excel, comptabilité, paiement et messagerie, sans scraping ni API officielle inventée.
- **M35 Custom fields — terminé et validé** — champs personnalisés typés et permissionnés sur les objets métier.
- **M36 Configuration métier — terminé et validé** — périodes, horaires, coefficients, frais, plans de paiement, absences, sanctions, documents, notifications, notes et permissions configurables.
- **M37 API publique — terminé et validé** — API serveur `/api/public/v1`, OpenAPI, clés à secret non récupérable, scopes, quotas, révocation et audit des appels.

## Première version fonctionnelle — jalons 18 à 23

18. **Communications — terminé et validé** — annonces, messages et notifications multicanales.
19. **Documents — terminé et validé** — DMS, versions, PDF, partage et liens signés.
20. **Signatures — terminé et validé** — preuve simple, transitions et port fournisseur.
21. **Transport — terminé et validé** — flotte, circuits, présence bus et abstraction GPS.
22. **Cantine — terminé et validé** — menus, repas, santé protégée et finance.
23. **Activités — terminé et validé** — capacité, attente, inscription famille et finance.

## Release 3 — School ERP

- **M31 Analytics direction — terminé et validé** — indicateurs élèves, présence, RH, admissions, finance, capacité et comparaison multi-campus ;
- **M32 Alertes / early warning — terminé et validé** — règles combinant absences, retards, baisse de notes, incidents, impayés et dossiers incomplets, sans diagnostic psychologique ;
- **M33 Workflow engine — terminé et validé** — déclencheurs, conditions, actions, exécutions, logs et protection contre les boucles ;
- **M34 Massar et intégrations — terminé et validé** — adaptateurs CSV/Excel/comptabilité/paiement/messagerie, sans scraping ni API officielle inventée ;
- **M35 Custom fields — terminé et validé** — champs personnalisés typés et permissionnés sur les objets métier ;
- **M36 Configuration métier — terminé et validé** — périodes, horaires, coefficients, frais, plans de paiement, absences, sanctions, documents, notifications, notes et permissions configurables ;
- **M37 API publique — terminé et validé** — API versionnée, OpenAPI, clés/scopes, rate limiting et audit ;
- **M38 Webhooks — terminé et validé** — événements signés, retries, idempotence, journaux et dead letters ;
- **M39 Recherche globale — terminé et validé** — élèves, familles, enseignants, classes, factures et documents en latin et arabe ;
- **M40 Centre de notifications — terminé et validé** — lu/non lu/archivé et préférences push/e-mail/SMS ;
- **M41 Tâches internes — terminé et validé** — assignation, échéance, priorité, statut, liaison métier et création automatique ;
- **M42 Calendrier établissement — terminé et validé** — vacances, examens, réunions, sorties, activités et rencontres parents-professeurs.

## Release 4 — Automation Platform

- **M43 UX / design system — terminé et validé** — navigation contextualisée par rôle, composants et tokens partagés, responsive, lisibilité et usages mobile/desktop ;
- **M44 PWA — terminé et validé** — installation des portails famille et professeur, manifests dédiés, cache contrôlé et stratégie de mise à jour ;
- **M45 Sécurité avancée — terminé et validé** — revue multi-tenant, uploads durcis et analysables, JWT, en-têtes, limitation de connexion et tests hostiles ;
- **M46 Données personnelles / Maroc — terminé et validé** — finalités, consentements, demandes, rétention, export traçable et registre d'hébergement, avec validation juridique CNDP restant explicite ;
- **M47 Sauvegarde / reprise — terminé et validé** — sauvegardes PostgreSQL/objets, manifeste d'intégrité, restauration isolée réellement exécutée et procédure de reprise ;
- **M48 Observabilité — terminé et validé** — health, métriques Prometheus, logs structurés corrélés, latence et cinq files opérationnelles supervisées sans données sensibles ;
- **M49 Tests — terminé et validé** — tests unitaires, intégration PostgreSQL, sécurité, isolation tenant, frontend, rapport JaCoCo et smoke tests multi-profils ;
- **M50 Performance — terminé et validé** — index, pagination bornée, requêtes contrôlées, dataset PostgreSQL de 20 000 élèves et script de charge ;
- **M51 Accessibilité — terminé et validé** — typographie agrandie, clavier, focus, contrastes, annonces de navigation, labels, RTL, responsive et préférences de mouvement ;
- **M52 IA après stabilisation** — résumés, aide à la rédaction, appréciations proposées et tendances via une couche sécurisée, sans décision sensible autonome.

Les jalons M36 puis M43 à M51 sont explicitement prévus pour **revenir sur les fonctionnalités déjà livrées** : configuration par école, cohérence visuelle, PWA, sécurité, conformité, sauvegarde, supervision, tests, performance et accessibilité. Les modules M0–M23 ne sont donc pas considérés comme figés dans leur état de démonstration.

## Chantiers transverses

UX, PWA, sécurité avancée, données personnelles, sauvegarde/restauration, observabilité, tests, performance et accessibilité sont des exigences continues, avec revues dédiées M43 à M51 avant la mise en production.
