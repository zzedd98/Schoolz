# SchoolZ — School ERP Maroc

SchoolZ est un SaaS de gestion d'établissements scolaires privés marocains, conçu comme une source de vérité unique pour l'académique, la finance, la relation familles et les opérations de l'établissement.

Le dépôt suit une architecture de **monolithe modulaire** : un seul déploiement backend au départ, avec des frontières métier vérifiées automatiquement. L'interface Angular réunit le backoffice, le portail familles, le portail élève autonome et le portail admissions autour d'un design system commun.

## État du projet

La **version 1.0** couvre le socle scolaire et ses quatre espaces, la finance, les opérations, les RH, la comptabilité, les achats, l'inventaire, la bibliothèque, les intégrations et les automatisations. Elle sépare strictement la démonstration de la construction de production, impose une configuration sécurisée au démarrage et utilise un stockage documentaire S3-compatible en production. Consultez [docs/PROGRESS.md](docs/PROGRESS.md) pour l'historique fonctionnel et [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) pour la procédure et les prérequis d'une mise en ligne.

## Prérequis

- Docker Desktop avec Docker Compose v2 ;
- Java 21 et Maven 3.9+ pour exécuter le backend hors conteneur ;
- Node.js 24 et npm 11 pour exécuter le frontend hors conteneur.

Les commandes Docker constituent le chemin reproductible recommandé et ne nécessitent pas d'installation locale de PostgreSQL, Redis ou MinIO.

## Démarrage rapide

1. Démarrer la démo : `docker compose --env-file .env.example up --build`.
2. Pour personnaliser les valeurs, copier ensuite `.env.example` vers `.env` et lancer `docker compose up --build`.
3. Ouvrir l'application sur `http://localhost:8088`.
4. Se connecter avec le tenant `ecole-demo`, l'e-mail `admin@schoolz.ma` et le mot de passe `SchoolZDemo2026!`.
5. Explorer « Admissions CRM » puis « Inscriptions » pour le pilotage des dossiers, l'affectation des classes et l'activation des élèves.
6. Sur l'unique page de connexion `http://localhost:8088/login`, choisir « Tester le portail parent » ou saisir `ecole-demo`, `parent.demo@schoolz.ma` et `SchoolZParent2026!` : SchoolZ reconnaît la session famille et redirige vers `http://localhost:8088/family`. Sans session parent, `/family` revient automatiquement vers `/login?portal=family`. Basculer entre Yasmine et Amine, choisir le 1er septembre 2026, consulter notes, devoirs, absences et télécharger un bulletin.
7. Ouvrir le portail Admissions sur `http://localhost:8088/admissions` avec la même session parent.
8. Depuis le dossier de démonstration accepté, ouvrir `http://localhost:8088/enrollment` pour parcourir contrat, famille, services, document, signature et frais.
9. Avec le compte administrateur, ouvrir « Crèche & garderie » ou `http://localhost:8088/app/childcare` pour consulter le groupe « Les Papillons » et le journal publié d'Amine.
10. Avec la session parent, ouvrir `http://localhost:8088/childcare` pour consulter son Daily Report.
11. Avec le compte administrateur, ouvrir « Présences » ou `http://localhost:8088/app/attendance` : la séance de mathématiques du jour contient trois élèves et illustre présent, absent et retard.
12. Avec la session parent, consulter `http://localhost:8088/attendance` pour voir l'absence de Yasmine, sa notification et le formulaire de justification.
13. Avec le compte administrateur, ouvrir « Emploi du temps » ou `http://localhost:8088/app/timetable` : le planning primaire contient cinq cours manuels, trois affectations et quatre cours proposés par l'optimiseur. La proposition reste sans effet tant que « Appliquer » n'est pas confirmé.
14. Ouvrir « Notes & compétences » ou `http://localhost:8088/app/grading` : comparer les politiques maternelle, primaire et collège, modifier une note avec son motif, consulter son historique, évaluer une compétence puis recalculer les brouillons de bulletins.
15. Ouvrir « Cahier de texte » ou `http://localhost:8088/app/homework` : consulter le cours de fractions, les trois devoirs, leurs pièces jointes et la progression par classe.
16. Avec la session parent, consulter `http://localhost:8088/homework` pour voir le calendrier de Yasmine et marquer un devoir comme fait.
17. Sur la page de connexion, choisir « Tester le portail professeur » ou utiliser l'établissement `ecole-demo`, le compte `prof.math@schoolz.ma` et son mot de passe dédié `SchoolZTeacher2026!` — différent du mot de passe administrateur. Ouvrir ensuite `http://localhost:8088/app/teacher?date=2026-09-01` pour tester la journée, l'appel, les notes et l'ajout de devoir contextualisés. La prévisualisation de production utilise les mêmes données locales sur le port `8089`.
18. Sur la page de connexion, choisir « Tester le portail élève » ou utiliser `yasmine.eleve@schoolz.ma` avec `SchoolZStudent2026!`. La route `http://localhost:8088/student` affiche directement sa journée, ses dernières notes, ses devoirs, ses derniers cours et ses prochains DS. Chaque rubrique possède une vraie page « Voir tout » sous `/student/day`, `/student/grades`, `/student/homework`, `/student/courses` et `/student/assessments` ; le compte élève ne peut pas entrer dans le backoffice.
19. Avec le compte administrateur, ouvrir « Finance » ou `http://localhost:8088/app/finance` pour consulter le compte famille, la facture de 39 500 MAD, le paiement ventilé de 38 000 MAD, le reçu unique et le reste de 1 500 MAD. Les formulaires permettent aussi de composer une facture et d'enregistrer un règlement sans effacer les écritures validées.
20. Dans le cockpit « Recouvrement », développer `ECH-DEMO-2026-0001` : deux échéances de 4 000 MAD sont réglées, la troisième est en retard, le taux vaut 66,67 % et les règles J−3/J+1/J+7/J+15 sont disponibles sur push, e-mail, SMS et WhatsApp en prévisualisation.
21. Avec la session parent, ouvrir `http://localhost:8088/finance` pour consulter le même ledger, le détail de facture, le reçu imprimable et l'échéancier en français ou en arabe.
22. Dans ce portail, utiliser « Préparer mon paiement sandbox » pour créer une intention idempotente. La confirmation sandbox simule un webhook HMAC signé et produit le paiement/reçu SchoolZ ; aucun débit bancaire réel n'est effectué.
23. Vérifier l'API sur `http://localhost:8088/api/v1/system/status`. En développement uniquement, Swagger reste accessible directement sur le port backend `http://localhost:18080/swagger-ui/index.html` ; le reverse proxy public le bloque.
24. Ouvrir `http://localhost:8088/app/communications` pour piloter annonces, conversations et livraisons, puis `http://localhost:8088/messages` avec la session parent.
25. Ouvrir `http://localhost:8088/app/documents` et `http://localhost:8088/app/signatures` pour les PDF versionnés, partages temporaires et signatures simples ; les vues famille sont `/documents` et `/signatures`.
26. Ouvrir `http://localhost:8088/app/transport` pour la flotte, les circuits et les trajets, puis `http://localhost:8088/transport` pour le suivi famille et l'ETA de démonstration.
27. Ouvrir `http://localhost:8088/app/cafeteria` pour les menus, abonnements, présences et restrictions, puis `http://localhost:8088/cafeteria` côté famille.
28. Ouvrir `http://localhost:8088/app/activities` pour les capacités et sessions, puis `http://localhost:8088/activities` pour tester l'inscription parent et la charge Finance associée.
29. Ouvrir `http://localhost:8088/app/health` avec le compte administrateur, ou utiliser le profil infirmière `infirmiere.demo@schoolz.ma` / `SchoolZNurse2026!`. Parcourir le dossier de Yasmine, les allergies, médicaments, incidents, visites et vaccinations ; toute consultation sensible est auditée et l’interface ne produit aucun diagnostic.
30. Ouvrir `http://localhost:8088/app/hr` avec le compte administrateur. Parcourir l’annuaire des quatre collaborateurs, leur contrat, horaires, documents et qualifications, puis les rubriques « Congés », « Présence » et « Organisation ». Le workflow de congé impose la revue manager avant toute décision et chaque employé reste relié à son unique dossier personne SchoolZ.
31. Ouvrir `http://localhost:8088/app/payroll` avec le compte administrateur. Parcourir le cycle d’août 2026, contrôler les quatre bulletins et leurs lignes, puis consulter « Règles & profils » et « Ajustements ». Le recalcul reste reproductible avant finalisation et l’export comptable CSV/JSON est empreinté ; cette configuration de démonstration ne prétend pas être juridiquement conforme et n’intègre aucun taux marocain non vérifié.
32. Ouvrir `http://localhost:8088/app/accounting` avec le compte administrateur. Parcourir le Grand Livre, la balance et le plan, le cycle fournisseur puis le rapprochement bancaire. La démonstration affiche 81 150 MAD au débit comme au crédit ; les événements du compte famille sont dédupliqués et aucune saisie libre des lignes comptables n’est exposée.
33. Ouvrir `http://localhost:8088/app/procurement` avec le compte administrateur. Suivre l'achat pédagogique de 12 600 MAD depuis la demande jusqu'au paiement, contrôler son rapprochement exact avec Papeterie Atlas, puis consulter la demande stratégique de 38 000 MAD encore en attente d'une seconde approbation.
34. Ouvrir `http://localhost:8088/app/inventory` avec le compte administrateur. Parcourir les actifs identifiés, les stocks et mouvements, la carte des lieux puis le fil d’histoire. La démonstration montre un ordinateur affecté, une tablette restituée, un projecteur en maintenance, 120 livres, 80 uniformes sous le seuil d’alerte et 45 kits répartis sur deux emplacements.
35. Ouvrir `http://localhost:8088/app/library` avec le compte administrateur. Scanner `SZ-LIB-QR-0001`, parcourir catalogue, prêts, réservations et règles, puis ouvrir le formulaire de prêt. Avec Yasmine, consulter `http://localhost:8088/student/library` ; avec la session parent, ouvrir la tuile « Bibliothèque » ou `http://localhost:8088/library` et basculer entre Amine et Yasmine.
36. Ouvrir `http://localhost:8088/app/analytics` avec le compte administrateur. Comparer les 1 284 élèves, 1,18 M MAD encaissés et 240 k MAD d’impayés du groupe, parcourir les douze mois d’évolution, puis filtrer successivement Casablanca, Rabat et Marrakech pour observer les KPI, capacités et taux de recouvrement recalculés.
37. Ouvrir `http://localhost:8088/app/early-warning` avec le compte administrateur. Examiner les 9 alertes issues de 6 règles, ouvrir « Élèves & signaux », lire la logique du moteur puis tester les tiroirs de prise en charge et de création. Les preuves restent factuelles et le serveur refuse tout vocabulaire de diagnostic psychologique ou médical.
38. Ouvrir `http://localhost:8088/app/workflows` avec le compte administrateur. Lire les trois chaînes WHEN/IF/THEN, ouvrir le banc d’essai et distribuer une facture échue à 12 jours, puis consulter le journal détaillé. L’événement `workflow.demo.loop` prouve que la réémission récursive est arrêtée et conservée comme `LOOP_BLOCKED`.
39. Ouvrir `http://localhost:8088/app/integrations` avec le compte administrateur. Contrôler le contrat d’intégrité Massar, parcourir les cinq adaptateurs, générer un export élèves en CSV ou Excel, puis consulter son empreinte dans la traçabilité. Le tiroir d’import valide les colonnes et les lignes sans modifier les données métier ; aucun scraping ni accès Massar officiel n’est utilisé ou simulé.
40. Ouvrir `http://localhost:8088/app/custom-fields` avec le compte administrateur. Sélectionner le dossier Élèves puis Yasmine, modifier une valeur typée et ouvrir « Dessiner un nouveau champ » pour parcourir les six types, les choix et les permissions. Les champs protégés restent filtrés côté serveur pour les profils non autorisés.
41. Ouvrir `http://localhost:8088/app/configuration` avec le compte administrateur. Parcourir les dix domaines, filtrer « Frais & paiements », modifier un réglage avec un motif puis utiliser « Valeur de référence ». Chaque décision incrémente la version et apparaît dans le journal ; aucune sanction ni notification externe n’est déclenchée automatiquement.
42. Ouvrir `http://localhost:8088/app/api-access` avec le compte administrateur. Contrôler le client « SIS de démonstration », ses trois scopes, son quota et les traces. Tester `GET /api/public/v1/students` avec l’en-tête `X-SchoolZ-Api-Key: schoolz_demo_api_key_m37_2026` ; la clé locale est réservée à la démonstration et ne doit jamais être réutilisée en production.
43. Ouvrir `http://localhost:8088/app/webhooks` avec le compte administrateur. Parcourir les deux destinations et les trois livraisons de référence : réussite, reprise planifiée et dead letter. Créer un abonnement pour observer le secret révélé une seule fois, puis publier un événement test idempotent ; les domaines `.invalid` de la démonstration ne désignent aucun service externe réel.
44. Ouvrir `http://localhost:8088/app/search` et rechercher successivement `Yasmine`, `Alaoui` puis une entrée arabe. Filtrer par élève, parent, professeur, facture, classe ou document : les facettes et la pagination restent limitées aux ressources accessibles au compte connecté.
45. Ouvrir `http://localhost:8088/app/notifications`. Parcourir les statuts non lu, lu et archivé, marquer une information comme lue puis consulter les préférences. L’e-mail de l’alerte critique de connexion reste obligatoire par politique de sécurité.
46. Ouvrir `http://localhost:8088/app/tasks`. Filtrer les tâches urgentes et en cours, ouvrir une tâche liée à son dossier métier, modifier son statut puis créer une tâche manuelle. La tâche automatique de relance illustre le branchement réel de l’action `CREATE_TASK` des workflows.
47. Ouvrir `http://localhost:8088/app/calendar`. Naviguer entre les périodes, filtrer vacances, examens ou réunions, puis créer et annuler un événement. L’annulation est logique et auditée ; le connecteur de synchronisation externe reste volontairement un point d’extension inactif.
48. Ouvrir `http://localhost:8088/app/privacy` avec le compte administrateur. Parcourir finalités, consentements, demandes d'accès/export/rectification/effacement, politiques de rétention et registre d'hébergement. Le module outille la gouvernance mais ne constitue pas une certification CNDP automatique.
49. Installer la PWA depuis les portails famille ou professeur. Les données d'API ne sont pas mises en cache hors ligne et une nouvelle version est signalée avant activation.
50. Contrôler l'exploitation depuis le réseau interne sur `http://localhost:18080/actuator/health` et, avec l'accès d'exploitation approprié, les métriques Prometheus ; Actuator n'est pas publié par le reverse proxy public.
51. Exécuter `scripts/e2e-smoke.ps1`, `scripts/load-test.ps1` puis `scripts/restore-test.ps1` sur la démo Docker pour rejouer respectivement les parcours critiques, une charge locale bornée et une restauration réelle isolée.

Les identifiants et secrets présents dans `.env.example` sont exclusivement destinés au développement local. Le profil de production ne crée aucun compte de démonstration et exige des secrets externes.

## Construction de production

La composition dédiée est validée et lancée avec `docker compose -f compose.prod.yaml --env-file .env.production ...`. Ne réutilisez jamais `.env.example` pour un hébergement public. La procédure complète, la configuration S3, les sauvegardes et la liste de contrôle d'ouverture sont documentées dans [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). La création d'une nouvelle école et de son premier administrateur est automatisée par [docs/PROVISIONING.md](docs/PROVISIONING.md).

## Prévisualiser le frontend de production en local

Exécuter `scripts/start-production-preview.ps1`, puis ouvrir `http://localhost:8089/login`. Cette prévisualisation utilise le frontend de production sans encart ni identifiants de démonstration, sur une origine neuve pour éviter les anciens caches PWA. Le backend conserve volontairement les données locales de recette : les comptes de `.env.example` peuvent donc être saisis manuellement. Ce mode permet de valider l'interface livrable ; il ne remplace pas le profil `prod`, qui exige HTTPS, des secrets externes et un bucket S3 privé.

## Développement local

Backend :

```text
cd backend
./mvnw spring-boot:run
```

Frontend :

```text
cd frontend
npm ci
npm start
```

Services d'infrastructure uniquement :

```text
docker compose up -d postgres redis minio
```

## Vérifications

- Backend : `backend/mvnw verify` (`backend\mvnw.cmd verify` sous Windows)
- Frontend : `npm --prefix frontend run check`
- Ensemble : `scripts/verify.ps1` sous Windows ou `scripts/verify.sh` sous Linux/macOS
- Configuration Docker : `docker compose config --quiet`

## Organisation du dépôt

```text
backend/        API Spring Boot, modules métier et migrations Flyway
frontend/       application Angular, PWA et design system
infrastructure/ reverse proxy et configuration locale
docs/           architecture, sécurité, domaine, ADR et progression
scripts/        commandes reproductibles de développement et de CI
```

## Principes non négociables

- isolation multi-tenant testée ;
- une personne physique correspond à un enregistrement central ;
- permissions fines en complément des rôles ;
- aucune suppression physique des écritures financières validées ;
- montants en décimal exact avec devise ;
- timestamps techniques en UTC, affichage selon le fuseau du campus ;
- français et arabe (`fr-MA`, `ar-MA`) avec RTL ;
- aucune intégration Massar inventée ou obtenue par scraping.

Les conventions détaillées sont dans [docs/CONVENTIONS.md](docs/CONVENTIONS.md).
