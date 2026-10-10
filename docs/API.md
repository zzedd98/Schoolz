# API

L'API est versionnée sous `/api/v1`. OpenAPI est généré sur `/v3/api-docs` et Swagger UI est disponible directement sur le backend hors production. Le reverse proxy public bloque systématiquement ces routes.

## Endpoints publics

| Méthode          | Route                                              | Usage                                                   |
| ---------------- | -------------------------------------------------- | ------------------------------------------------------- |
| `GET`            | `/api/v1/system/status`                            | état public minimal                                     |
| `POST`           | `/api/v1/auth/login`                               | connexion par tenant, e-mail et mot de passe            |
| `POST`           | `/api/v1/auth/refresh`                             | rotation du cookie de session                           |
| `POST`           | `/api/v1/auth/logout`                              | révocation et suppression du cookie                     |
| `POST`           | `/api/v1/auth/forgot-password`                     | demande de réinitialisation sans divulgation de compte  |
| `POST`           | `/api/v1/auth/reset-password`                      | consommation du jeton et révocation des sessions        |
| `POST`           | `/api/v1/auth/verify-email`                        | vérification à usage unique                             |
| `GET`            | `/api/v1/public/admissions/{tenantSlug}/catalog`   | campagnes, campus et niveaux ouverts                    |
| `POST`           | `/api/v1/public/admissions/{tenantSlug}/prospects` | demande d'information sans compte                       |
| `POST`           | `/api/v1/public/admissions/accounts`               | création du compte parent                               |
| `POST`, `DELETE` | `/api/v1/public/admissions/sessions` / `session`   | connexion et révocation parent                          |
| `GET`            | `/api/v1/public/childcare/reports?date=YYYY-MM-DD` | Daily Reports publiés des enfants liés au compte parent |

`POST /auth/login` renvoie un JWT d'accès court dans le corps et place le refresh token dans le cookie `schoolz_refresh`, `HttpOnly`, `SameSite=Strict`. Le JWT s'envoie avec `Authorization: Bearer <token>` et n'est pas conservé dans `localStorage`.

## Endpoints authentifiés

| Méthode | Route                             | Permission           |
| ------- | --------------------------------- | -------------------- |
| `GET`   | `/api/v1/auth/me`                 | compte authentifié   |
| `GET`   | `/api/v1/admin/users`             | `user.read`          |
| `GET`   | `/api/v1/admin/users/{id}`        | `user.read`          |
| `POST`  | `/api/v1/admin/users`             | `user.write`         |
| `PATCH` | `/api/v1/admin/users/{id}/status` | `user.disable`       |
| `PUT`   | `/api/v1/admin/users/{id}/roles`  | `user.write`         |
| `GET`   | `/api/v1/admin/roles`             | `role.read`          |
| `POST`  | `/api/v1/admin/roles`             | `role.write`         |
| `GET`   | `/api/v1/admin/permissions`       | session authentifiée |
| `GET`   | `/api/v1/admin/audit?limit=50`    | `audit.read`         |

Les recherches sont systématiquement bornées au tenant issu du principal. Une permission manquante produit `403`; une authentification absente ou invalide produit `401`. Les erreurs de validation utilisent Problem Details et ne publient ni stack trace ni secret.

En profil `dev` uniquement, les réponses de reset et de création d'utilisateur peuvent inclure un jeton de développement afin de tester le flux sans fournisseur e-mail.

## Organisation et campus

| Méthode       | Route                                | Permission                                 |
| ------------- | ------------------------------------ | ------------------------------------------ |
| `GET`, `PUT`  | `/api/v1/organization`               | `organization.read` / `organization.write` |
| `GET`, `PUT`  | `/api/v1/organization/settings`      | `organization.read` / `organization.write` |
| `GET`, `POST` | `/api/v1/campuses`                   | `campus.read` / `campus.write`             |
| `PUT`         | `/api/v1/campuses/{id}`              | `campus.write`                             |
| `PUT`         | `/api/v1/campuses/{id}/grade-levels` | `campus.write`                             |

La configuration couvre identité juridique, logo sous forme de clé objet, adresse, contacts, identifiants configurables (ICE, etc.), langues, MAD ou autre devise ISO, fuseau, calendrier et horaires. Un même tenant peut créer plusieurs campus.

## Structure académique

| Méthode       | Route                                  | Usage                              |
| ------------- | -------------------------------------- | ---------------------------------- |
| `GET`, `POST` | `/api/v1/academics/years`              | années scolaires                   |
| `PATCH`       | `/api/v1/academics/years/{id}/status`  | activation/fermeture               |
| `POST`        | `/api/v1/academics/years/{id}/terms`   | périodes, semestres ou trimestres  |
| `GET`, `POST` | `/api/v1/academics/levels`             | cycles scolaires                   |
| `POST`        | `/api/v1/academics/levels/{id}/grades` | niveaux d'un cycle                 |
| `GET`, `POST` | `/api/v1/academics/rooms`              | salles physiques                   |
| `GET`, `POST` | `/api/v1/academics/classrooms`         | groupes de classe par année/campus |

Toutes ces routes exigent `academic-structure.read` ou `academic-structure.write`. Les dates de période sont bornées par l'année, les chevauchements sont refusés et une classe ne peut utiliser qu'un niveau offert et une salle de son campus.

## Personnes, familles et élèves

| Méthode | Route                                 | Permission                        |
| ------- | ------------------------------------- | --------------------------------- |
| `GET`   | `/api/v1/students?search=`            | `student.read`                    |
| `POST`  | `/api/v1/students`                    | `student.write` et `person.write` |
| `GET`   | `/api/v1/students/{id}`               | `student.read`                    |
| `PATCH` | `/api/v1/students/{id}/archive`       | `student.write`                   |
| `PUT`   | `/api/v1/students/{id}/photo`         | `student.write`                   |
| `PUT`   | `/api/v1/students/{id}/custom-fields` | `student.write`                   |
| `POST`  | `/api/v1/students/{id}/documents`     | `student.write`                   |

La création réalise atomiquement la personne, le foyer, le responsable, les contacts, l'inscription et l'identifiant scolaire. La lecture renvoie le dossier 360° complet. Les rubriques non encore implémentées renvoient un état `PENDING_MODULE`, jamais des données inventées. L'archivage remplace toute suppression et conserve l'historique.

## Import et migration de données

| Méthode | Route                                   | Permission          |
| ------- | --------------------------------------- | ------------------- |
| `GET`   | `/api/v1/imports`                       | `data-import.read`  |
| `POST`  | `/api/v1/imports?type=STUDENTS`         | `data-import.write` |
| `GET`   | `/api/v1/imports/{jobId}`               | `data-import.read`  |
| `PUT`   | `/api/v1/imports/{jobId}/mapping`       | `data-import.write` |
| `POST`  | `/api/v1/imports/{jobId}/validate`      | `data-import.write` |
| `GET`   | `/api/v1/imports/{jobId}/preview`       | `data-import.read`  |
| `POST`  | `/api/v1/imports/{jobId}/execute`       | `data-import.write` |
| `GET`   | `/api/v1/imports/templates/{type}.xlsx` | compte authentifié  |

Les types acceptés sont `STUDENTS`, `PARENTS`, `STAFF`, `CLASSES`, `PAYMENTS` et `OPENING_BALANCES`. L'upload accepte `.csv` et `.xlsx`, au maximum 5 Mo et 10 000 lignes. Le mapping associe les clés canoniques exposées dans `fields` aux noms présents dans `headers`.

La validation ne crée aucune donnée métier. La preview fournit pour chaque ligne son numéro d'origine, ses valeurs normalisées et une liste `{field, message}`. L'exécution n'est autorisée que si toutes les lignes sont valides. Elle utilise une transaction unique ; le statut `FAILED` garantit `importedRows = 0` et signale qu'aucune donnée métier du job n'a été conservée.

## Admissions CRM interne

| Méthode       | Route                                             | Permission                             |
| ------------- | ------------------------------------------------- | -------------------------------------- |
| `GET`, `POST` | `/api/v1/admissions/campaigns`                    | `admissions.read` / `admissions.write` |
| `POST`        | `/api/v1/admissions/prospects`                    | `admissions.write`                     |
| `GET`         | `/api/v1/admissions/board?campaignId=`            | `admissions.read`                      |
| `GET`         | `/api/v1/admissions/dashboard?campaignId=`        | `admissions.read`                      |
| `PATCH`       | `/api/v1/admissions/pipeline`                     | `admissions.write`                     |
| `POST`        | `/api/v1/admissions/applications/{id}/activities` | `admissions.write`                     |
| `POST`        | `/api/v1/admissions/applications/{id}/decision`   | `admissions.write`                     |
| `PATCH`       | `/api/v1/admissions/documents/{id}/review`        | `admissions.write`                     |

Le Kanban renvoie toujours les 11 colonnes du pipeline, même vides. Une carte `PROSPECT` ne peut avancer que jusqu'à la visite ; la création du dossier parent réalise la conversion. Une carte `APPLICATION` suit les transitions autorisées. Une décision acceptée émet une offre datée.

## Portail public Admissions

Après création ou connexion, le serveur place `schoolz_admission` dans un cookie `HttpOnly`, `SameSite=Strict`. Le jeton opaque est haché en base, expire après sept jours et peut être révoqué. Toutes les routes ci-dessous exigent ce cookie, sans exposer de JWT au navigateur.

| Méthode       | Route                                                      | Usage parent                               |
| ------------- | ---------------------------------------------------------- | ------------------------------------------ |
| `GET`         | `/api/v1/public/admissions/me`                             | compte, enfants, dossiers et notifications |
| `POST`        | `/api/v1/public/admissions/applicants`                     | ajouter un enfant                          |
| `GET`, `POST` | `/api/v1/public/admissions/applications`                   | lister ou créer les dossiers               |
| `PUT`         | `/api/v1/public/admissions/applications/{id}`              | enregistrer le formulaire                  |
| `POST`        | `/api/v1/public/admissions/applications/{id}/documents`    | upload PDF/JPEG/PNG, 5 Mo maximum          |
| `GET`         | `/api/v1/public/admissions/documents/{id}`                 | télécharger sa propre pièce                |
| `POST`        | `/api/v1/public/admissions/applications/{id}/submit`       | contrôler les pièces et soumettre          |
| `POST`        | `/api/v1/public/admissions/applications/{id}/fee-payments` | acquittement de démonstration              |
| `POST`        | `/api/v1/public/admissions/offers/{id}/accept`             | accepter l'offre d'inscription             |
| `GET`         | `/api/v1/public/admissions/notifications`                  | centre de notifications                    |
| `POST`        | `/api/v1/public/admissions/notifications/{id}/read`        | marquer comme lue                          |

Le propriétaire du dossier est recalculé par jointure à chaque accès. Un identifiant de document, d'offre ou de notification provenant d'un autre compte ou tenant renvoie `404`.

## Inscription et réinscription

La session parent Admissions couvre tout `/api/v1/public`, ce qui permet de poursuivre une offre acceptée sans réauthentification. Une inscription initiale exige une application `ENROLLED` avec une offre `ACCEPTED`. Une réinscription exige un lien explicite entre le compte parent et l'élève ; ses informations famille sont préremplies depuis le dossier existant.

| Méthode       | Route                                             | Usage parent                                              |
| ------------- | ------------------------------------------------- | --------------------------------------------------------- |
| `GET`, `POST` | `/api/v1/public/enrollments`                      | lister les parcours ou démarrer une inscription initiale  |
| `POST`        | `/api/v1/public/enrollments/re-enrollments`       | démarrer une réinscription pour une nouvelle année        |
| `POST`        | `/api/v1/public/enrollments/{id}/contract/accept` | accepter le contrat versionné                             |
| `PUT`         | `/api/v1/public/enrollments/{id}/family`          | confirmer ou modifier les informations famille            |
| `PUT`         | `/api/v1/public/enrollments/{id}/services`        | choisir transport, cantine, activités et plan de paiement |
| `POST`        | `/api/v1/public/enrollments/{id}/documents`       | ajouter une pièce PDF/JPEG/PNG de 5 Mo maximum            |
| `POST`        | `/api/v1/public/enrollments/{id}/signature`       | consentement et signature électronique avec empreinte     |
| `POST`        | `/api/v1/public/enrollments/{id}/fee-payments`    | acquittement de démonstration des frais d'inscription     |

| Méthode | Route                               | Permission interne |
| ------- | ----------------------------------- | ------------------ |
| `GET`   | `/api/v1/enrollments`               | `enrollment.read`  |
| `GET`   | `/api/v1/enrollments/{id}/classes`  | `enrollment.read`  |
| `PATCH` | `/api/v1/enrollments/{id}/class`    | `enrollment.write` |
| `POST`  | `/api/v1/enrollments/{id}/activate` | `enrollment.write` |

L'activation vérifie atomiquement le contrat, la famille, les services, les pièces, la signature, les frais et la classe. Pour une admission, elle crée la personne, le foyer, le responsable, le profil élève, l'identifiant et l'inscription académique. Pour une réinscription, elle conserve l'élève et ajoute uniquement l'inscription de la nouvelle année.

## Crèche et garderie

| Méthode | Route                                              | Permission                                                      |
| ------- | -------------------------------------------------- | --------------------------------------------------------------- |
| `GET`   | `/api/v1/childcare/dashboard?date=&groupId=`       | `childcare.read`                                                |
| `GET`   | `/api/v1/childcare/groups`                         | `childcare.read`                                                |
| `POST`  | `/api/v1/childcare/profiles`                       | `childcare.write`                                               |
| `POST`  | `/api/v1/childcare/children/{studentId}/arrival`   | `childcare.write`                                               |
| `POST`  | `/api/v1/childcare/children/{studentId}/departure` | `childcare.write`                                               |
| `POST`  | `/api/v1/childcare/children/{studentId}/events`    | `childcare.write`, et `childcare.medication` pour un médicament |
| `POST`  | `/api/v1/childcare/children/{studentId}/publish`   | `childcare.publish`                                             |

Le départ exige l'identifiant d'une personne `AUTHORIZED`; une personne `PROHIBITED` est refusée. Un événement `MEDICATION` est en outre contrôlé par la politique du campus. La publication fige le journal et autorise sa lecture par le compte parent lié. Aucun endpoint ne produit de diagnostic médical.

## Présences et vie scolaire

| Méthode | Route                                                             | Permission interne  |
| ------- | ----------------------------------------------------------------- | ------------------- |
| `GET`   | `/api/v1/attendance/references`                                   | `attendance.read`   |
| `GET`   | `/api/v1/attendance/sessions?date=`                               | `attendance.read`   |
| `GET`   | `/api/v1/attendance/sessions/{id}`                                | `attendance.read`   |
| `POST`  | `/api/v1/attendance/sessions`                                     | `attendance.write`  |
| `PUT`   | `/api/v1/attendance/sessions/{id}/records`                        | `attendance.write`  |
| `POST`  | `/api/v1/attendance/sessions/{id}/submit`                         | `attendance.write`  |
| `GET`   | `/api/v1/attendance/justifications?status=PENDING`                | `attendance.manage` |
| `PATCH` | `/api/v1/attendance/justifications/{id}`                          | `attendance.manage` |
| `GET`   | `/api/v1/attendance/statistics?from=&to=&classroomId=&studentId=` | `attendance.read`   |
| `POST`  | `/api/v1/attendance/incidents`                                    | `discipline.write`  |
| `POST`  | `/api/v1/attendance/incidents/{id}/sanctions`                     | `discipline.write`  |
| `POST`  | `/api/v1/attendance/observations`                                 | `discipline.write`  |

À la création d'une séance, tous les élèves actifs de la classe sont présents par défaut. La clôture rend la séance non modifiable et matérialise les absences, retards, départs anticipés et notifications. Une justification approuvée change l'absence en `EXCUSED`; une justification rejetée peut être soumise à nouveau.

| Méthode | Route                                                    | Usage parent                             |
| ------- | -------------------------------------------------------- | ---------------------------------------- |
| `GET`   | `/api/v1/public/attendance?from=&to=`                    | absences et notifications de ses enfants |
| `POST`  | `/api/v1/public/attendance/absences/{id}/justifications` | déposer ou redéposer une justification   |
| `PATCH` | `/api/v1/public/attendance/notifications/{id}/read`      | marquer une notification comme lue       |

Le cookie parent Admissions est réutilisé, mais chaque absence et notification est revalidée par jointure avec `portal_student_link`; un identifiant d'un autre parent ou tenant renvoie `404`.

## Emploi du temps

| Méthode           | Route                                                                               | Permission                           |
| ----------------- | ----------------------------------------------------------------------------------- | ------------------------------------ |
| `GET`             | `/api/v1/timetable/references`                                                      | `timetable.read`                     |
| `GET`, `POST`     | `/api/v1/timetable/subjects`                                                        | `timetable.read` / `timetable.write` |
| `GET`, `POST`     | `/api/v1/timetable/schedules`                                                       | `timetable.read` / `timetable.write` |
| `GET`             | `/api/v1/timetable/schedules/{id}`                                                  | `timetable.read`                     |
| `POST`            | `/api/v1/timetable/rooms/{roomId}/equipment`                                        | `timetable.write`                    |
| `POST`            | `/api/v1/timetable/schedules/{id}/assignments`                                      | `timetable.write`                    |
| `PUT`             | `/api/v1/timetable/schedules/{id}/teachers/{teacherId}/slots/{slotId}/availability` | `timetable.write`                    |
| `PUT`             | `/api/v1/timetable/schedules/{id}/rooms/{roomId}/slots/{slotId}/availability`       | `timetable.write`                    |
| `POST`            | `/api/v1/timetable/schedules/{id}/constraints`                                      | `timetable.write`                    |
| `POST`            | `/api/v1/timetable/schedules/{id}/lessons/validate`                                 | `timetable.write`                    |
| `POST`            | `/api/v1/timetable/schedules/{id}/lessons`                                          | `timetable.write`                    |
| `PATCH`, `DELETE` | `/api/v1/timetable/schedules/{id}/lessons/{lessonId}`                               | `timetable.write`                    |
| `POST`            | `/api/v1/timetable/schedules/{id}/optimizer/proposals`                              | `timetable.optimize`                 |
| `POST`            | `/api/v1/timetable/schedules/{id}/optimizer/apply`                                  | `timetable.optimize`                 |
| `POST`            | `/api/v1/timetable/schedules/{id}/publish`                                          | `timetable.publish`                  |

La validation et la preview de l'optimiseur sont sans effet. Une création ou un déplacement renvoie `409` avec une liste de conflits structurés si une contrainte bloquante échoue. L'application de propositions revalide l'instantané dans une transaction unique ; la publication rend ensuite le planning immuable.

## Notes, évaluations et compétences

| Méthode | Route                                                                             | Permission              |
| ------- | --------------------------------------------------------------------------------- | ----------------------- |
| `GET`   | `/api/v1/grading/references`                                                      | `grade.read`            |
| `GET`   | `/api/v1/grading/dashboard?classroomId=&academicTermId=`                          | `grade.read`            |
| `POST`  | `/api/v1/grading/scales`                                                          | `grade.configure`       |
| `PUT`   | `/api/v1/grading/policies`                                                        | `grade.configure`       |
| `POST`  | `/api/v1/grading/assessment-types`                                                | `grade.configure`       |
| `PUT`   | `/api/v1/grading/coefficients`                                                    | `grade.configure`       |
| `POST`  | `/api/v1/grading/competencies`                                                    | `grade.configure`       |
| `POST`  | `/api/v1/grading/report-card-templates`                                           | `grade.configure`       |
| `POST`  | `/api/v1/grading/assessments`                                                     | `grade.write`           |
| `POST`  | `/api/v1/grading/assessments/{id}/publish`                                        | `grade.write`           |
| `PUT`   | `/api/v1/grading/assessments/{assessmentId}/grades/{studentId}`                   | `grade.write`           |
| `GET`   | `/api/v1/grading/grades/{gradeId}/history`                                        | `grade.read`            |
| `POST`  | `/api/v1/grading/competency-evaluations`                                          | `grade.write`           |
| `POST`  | `/api/v1/grading/teacher-comments`                                                | `grade.write`           |
| `POST`  | `/api/v1/grading/report-cards/calculate?classroomId=&academicTermId=&templateId=` | `report-card.calculate` |
| `POST`  | `/api/v1/grading/report-cards/{id}/preview`                                       | `report-card.publish`   |
| `POST`  | `/api/v1/grading/report-cards/{id}/publish`                                       | `report-card.publish`   |
| `GET`   | `/api/v1/grading/report-cards/{id}/pdf`                                           | `grade.read`            |
| `GET`   | `/api/v1/grading/report-cards/{id}/versions`                                      | `grade.read`            |

La politique résolue depuis le cycle de la classe décide si les notes numériques, compétences, classements et bulletins sont autorisés. Le client ne transmet jamais une formule libre : il sélectionne une stratégie validée et une configuration structurée. Une correction de note existante sans motif renvoie `400`; une note pour un enfant de crèche ou un cycle non numérique renvoie `409`.

Le calcul produit une version `CALCULATED`. La prévisualisation génère un PDF filigrané sans écriture. La publication accepte `FR`, `AR` ou `BILINGUAL`, fige le binaire, son nom, sa taille, son empreinte SHA-256, le signataire, la note et l'instantané de publication. Une nouvelle correction ne modifie jamais une version `PUBLISHED` : elle crée une version supérieure liée par `supersedes_report_card_id`. Le téléchargement et l'historique revalident toujours le tenant de l'utilisateur.

## Cahier de texte et devoirs

| Méthode | Route                                                               | Permission         |
| ------- | ------------------------------------------------------------------- | ------------------ |
| `GET`   | `/api/v1/homework/references`                                       | `homework.read`    |
| `GET`   | `/api/v1/homework/dashboard?classroomId=&academicTermId=&from=&to=` | `homework.read`    |
| `POST`  | `/api/v1/homework/lesson-contents`                                  | `homework.write`   |
| `PUT`   | `/api/v1/homework/lesson-contents/{id}`                             | `homework.write`   |
| `POST`  | `/api/v1/homework/lesson-contents/{id}/attachments`                 | `homework.write`   |
| `POST`  | `/api/v1/homework/lesson-contents/{id}/publish`                     | `homework.publish` |
| `POST`  | `/api/v1/homework/assignments`                                      | `homework.write`   |
| `PUT`   | `/api/v1/homework/assignments/{id}`                                 | `homework.write`   |
| `POST`  | `/api/v1/homework/assignments/{id}/attachments`                     | `homework.write`   |
| `POST`  | `/api/v1/homework/assignments/{id}/publish`                         | `homework.publish` |
| `POST`  | `/api/v1/homework/assignments/{id}/close`                           | `homework.publish` |
| `GET`   | `/api/v1/homework/attachments/{id}`                                 | `homework.read`    |

Le fichier est envoyé en `multipart/form-data` sous la part `file`, avant publication uniquement. PDF, JPEG, PNG, TXT, DOCX et PPTX sont acceptés jusqu'à 10 Mo. La publication d'un devoir initialise un statut `TO_DO` pour chaque élève actif de la classe ; elle exige que son éventuel contenu de cours lié soit déjà publié.

| Méthode | Route                                             | Usage parent                                  |
| ------- | ------------------------------------------------- | --------------------------------------------- |
| `GET`   | `/api/v1/public/homework?from=&to=&studentId=`    | cours, devoirs et enfants liés                |
| `PATCH` | `/api/v1/public/homework/assignments/{id}/status` | passer son enfant entre `TO_DO` et `DONE`     |
| `GET`   | `/api/v1/public/homework/attachments/{id}`        | télécharger une pièce d'un élément publié lié |

Ces routes exigent le cookie parent Admissions. Le serveur ignore tout tenant transmis par le client et recalcule l'accès par jointure avec `enrollment_portal_student_link`. Le statut `DONE` ne crée aucune remise de devoir et n'alimente pas les notes.

## Portail professeur

| Méthode | Route                                | Permission            |
| ------- | ------------------------------------ | --------------------- |
| `GET`   | `/api/v1/teacher-portal/today?date=` | `teacher-portal.read` |

La date est optionnelle et vaut la date locale du serveur par défaut. Elle est bornée à ±400 jours. Le compte courant est résolu vers un unique `StaffProfile` actif par `teacher_portal_profile`; seules ses leçons appartenant à un planning `PUBLISHED` sont retournées. La réponse agrège l'identité professeur, la chronologie du jour, l'état de l'appel, les compteurs, la prochaine journée enseignée et les raccourcis autorisés. Les chemins d'action transportent uniquement un contexte vérifié classe/matière/affectation/date ; chaque module cible réapplique ses propres permissions et contrôles tenant.

## Portail famille

| Méthode | Route                                                                               | Usage parent                                      |
| ------- | ----------------------------------------------------------------------------------- | ------------------------------------------------- |
| `GET`   | `/api/v1/public/family-portal?studentId=&date=`                                     | accueil agrégé et sélection explicite d'un enfant |
| `PUT`   | `/api/v1/public/family-portal/preference`                                           | conserver l'enfant préféré du compte              |
| `GET`   | `/api/v1/public/family-portal/children/{studentId}/report-cards/{reportCardId}/pdf` | télécharger un bulletin publié lié                |

Ces routes exigent le cookie parent Admissions. `studentId` n'est jamais une preuve d'accès : il doit appartenir à `enrollment_portal_student_link` pour le compte et le tenant de la session. La date est optionnelle et bornée à ±400 jours. La réponse ne contient que des données publiées : emploi du temps, notes, devoirs et bulletins, avec la présence du jour et les absences déjà matérialisées. Un enfant ou bulletin non lié renvoie `404`.

`PUT /preference` accepte `{ "studentId": "uuid" }` et renvoie le tableau de bord du profil sélectionné. La préférence ne crée aucun droit supplémentaire. Le PDF est servi en `application/pdf` avec `Content-Disposition: attachment` et `nosniff`; le lien compte–enfant est revérifié au téléchargement.

## Finance et family ledger

| Méthode | Route                                                                  | Permission              |
| ------- | ---------------------------------------------------------------------- | ----------------------- |
| `GET`   | `/api/v1/billing/dashboard`                                            | `invoice.read`          |
| `GET`   | `/api/v1/billing/accounts/{accountId}`                                 | `invoice.read`          |
| `POST`  | `/api/v1/billing/accounts`                                             | `invoice.create`        |
| `POST`  | `/api/v1/billing/accounts/{accountId}/invoices`                        | `invoice.create`        |
| `POST`  | `/api/v1/billing/invoices/{invoiceId}/issue`                           | `invoice.create`        |
| `POST`  | `/api/v1/billing/accounts/{accountId}/payments`                        | `payment.record`        |
| `POST`  | `/api/v1/billing/payments/{paymentId}/reverse`                         | `payment.reverse`       |
| `POST`  | `/api/v1/billing/accounts/{accountId}/credits`                         | `invoice.adjust`        |
| `POST`  | `/api/v1/billing/accounts/{accountId}/refunds`                         | `payment.record`        |
| `POST`  | `/api/v1/billing/accounts/{accountId}/adjustments`                     | `invoice.adjust`        |
| `GET`   | `/api/v1/billing/receipts/{receiptId}`                                 | `invoice.read`          |
| `GET`   | `/api/v1/billing/collections/dashboard?asOf=`                          | `collections.read`      |
| `GET`   | `/api/v1/billing/collections/accounts/{accountId}/installment-plans`   | `collections.read`      |
| `POST`  | `/api/v1/billing/collections/accounts/{accountId}/installment-plans`   | `collections.manage`    |
| `POST`  | `/api/v1/billing/collections/reminder-rules`                           | `collections.manage`    |
| `PUT`   | `/api/v1/billing/collections/reminder-rules/{ruleId}`                  | `collections.manage`    |
| `POST`  | `/api/v1/billing/collections/payment-schedules/{scheduleId}/reminders` | `collections.manage`    |
| `POST`  | `/api/v1/billing/collections/reminders/run?asOf=`                      | `collections.manage`    |
| `GET`   | `/api/v1/billing/online-payments?accountId=`                           | `online-payment.read`   |
| `POST`  | `/api/v1/billing/online-payments/{paymentId}/verify`                   | `online-payment.manage` |
| `POST`  | `/api/v1/billing/online-payments/{paymentId}/refunds`                  | `online-payment.refund` |
| `POST`  | `/api/v1/payments/webhooks/{providerKey}`                              | webhook signé public    |

Une facture accepte des lignes `TUITION`, `REGISTRATION`, `TRANSPORT`, `CAFETERIA`, `ACTIVITY`, `DAYCARE` ou `OTHER_FEE`, chacune avec quantité, prix unitaire et remise. Son émission fige les lignes et écrit la charge dans le ledger. Un paiement accepte des allocations facture `{ invoiceId, amount }` et échéance `{ paymentScheduleId, amount }`. Chaque liste est validée contre le montant reçu et ses propres restes : le pointage d'une même somme sur sa facture et sa date attendue ne crée pas une seconde écriture monétaire.

`POST /payments/{paymentId}/reverse` exige un motif et ne supprime jamais le paiement. La réponse représente l'écriture inverse ; l'original, ses allocations et son reçu restent présents avec leur statut d'annulation. Les méthodes supportées sont `CASH`, `BANK_TRANSFER`, `CHECK`, `CARD`, `ONLINE_PAYMENT` et `OTHER`.

## API M18–M31

- Communications : `/api/v1/communications/**` et `/api/v1/public/communications/**`.
- Documents : `/api/v1/documents/**`, `/api/v1/public/documents/**`, liens temporaires `/downloads/{token}`.
- Signatures : `/api/v1/document-signatures/**` et `/api/v1/public/document-signatures/**`.
- Transport : `/api/v1/transport/**` et `/api/v1/public/transport`.
- Cantine : `/api/v1/cafeteria/**` et `/api/v1/public/cafeteria`.
- Activités : `/api/v1/activities/dashboard`, `/api/v1/public/activities` et `/api/v1/public/activities/enrollments`.
- Santé : `/api/v1/health/**`, exclusivement dans le backoffice restreint.
- Ressources humaines : `/api/v1/hr/**`, exclusivement dans le backoffice autorisé.
- Paie : `/api/v1/payroll/**`, exclusivement dans le backoffice autorisé.
- Comptabilité : `/api/v1/accounting/**`, exclusivement dans le backoffice autorisé et sans endpoint de saisie libre du Grand Livre.
- Bibliothèque : `/api/v1/library/**`, `/api/v1/student-portal/library` et `/api/v1/public/library`.
- Analytics Direction : `/api/v1/analytics/direction?asOf=&campusId=`, réservé à `analytics.read`.

### Analytics Direction

| Méthode | Route                                         | Permission       |
| ------- | --------------------------------------------- | ---------------- |
| `GET`   | `/api/v1/analytics/direction?asOf=&campusId=` | `analytics.read` |

Sans `campusId`, la réponse consolide le dernier snapshot disponible à la date demandée sur tous les campus du tenant. Avec un campus, les KPI, les tendances et les dimensions sont recalculés sur ce périmètre ; la comparaison multi-campus reste fournie pour garder le contexte de groupe. La date est bornée côté serveur et aucune donnée d’un autre tenant n’est lisible.

### Santé & infirmerie

| Méthode | Route                                                     | Permission sensible     |
| ------- | --------------------------------------------------------- | ----------------------- |
| `GET`   | `/api/v1/health/dashboard`                                | `health.read`           |
| `GET`   | `/api/v1/health/students/{studentId}`                     | `health.sensitive.read` |
| `PUT`   | `/api/v1/health/students/{studentId}/profile`             | `health.write`          |
| `POST`  | `/api/v1/health/students/{studentId}/allergies`           | `health.write`          |
| `POST`  | `/api/v1/health/students/{studentId}/medications`         | `health.write`          |
| `POST`  | `/api/v1/health/students/{studentId}/incidents`           | `health.write`          |
| `POST`  | `/api/v1/health/students/{studentId}/visits`              | `health.write`          |
| `POST`  | `/api/v1/health/incidents/{incidentId}/emergency-actions` | `health.write`          |
| `POST`  | `/api/v1/health/students/{studentId}/vaccinations`        | `health.vaccination`    |
| `PUT`   | `/api/v1/health/policy`                                   | `health.configure`      |

Le suivi des vaccinations refuse toute écriture tant que la politique établissement n’est pas activée. Les réponses et formulaires sont factuels et ne contiennent aucun champ de diagnostic. L’ouverture d’un dossier individuel ainsi que toutes les mutations sont journalisées sans recopier les notes sensibles dans les métadonnées d’audit.

### Ressources humaines

| Méthode | Route                                                  | Permission             |
| ------- | ------------------------------------------------------ | ---------------------- |
| `GET`   | `/api/v1/hr/dashboard?date=`                           | `hr.read`              |
| `GET`   | `/api/v1/hr/employees/{employeeId}`                    | `hr.read`              |
| `POST`  | `/api/v1/hr/departments`                               | `hr.write`             |
| `POST`  | `/api/v1/hr/positions`                                 | `hr.write`             |
| `POST`  | `/api/v1/hr/employees`                                 | `hr.write`             |
| `POST`  | `/api/v1/hr/employees/{employeeId}/contracts`          | `hr.write`             |
| `POST`  | `/api/v1/hr/employees/{employeeId}/schedules`          | `hr.write`             |
| `POST`  | `/api/v1/hr/leave-requests`                            | `hr.write`             |
| `POST`  | `/api/v1/hr/leave-requests/{requestId}/manager-review` | `hr.leave.review`      |
| `POST`  | `/api/v1/hr/leave-requests/{requestId}/decision`       | `hr.leave.review`      |
| `POST`  | `/api/v1/hr/attendance`                                | `hr.attendance.manage` |
| `POST`  | `/api/v1/hr/employees/{employeeId}/documents`          | `hr.write`             |
| `POST`  | `/api/v1/hr/employees/{employeeId}/qualifications`     | `hr.write`             |

Une décision de congé est refusée tant que la demande n’a pas franchi l’étape `MANAGER_REVIEW`. La création d’un employé exige les identifiants d’un `Person` et d’un `StaffProfile` existants du même tenant : l’API ne crée jamais une seconde personne physique. Les mutations de présence sont idempotentes par employé et journée, et les opérations RH sensibles sont auditées.

### Paie configurable

| Méthode | Route                                                    | Permission          |
| ------- | -------------------------------------------------------- | ------------------- |
| `GET`   | `/api/v1/payroll/dashboard`                              | `payroll.read`      |
| `POST`  | `/api/v1/payroll/periods`                                | `payroll.configure` |
| `POST`  | `/api/v1/payroll/components`                             | `payroll.configure` |
| `POST`  | `/api/v1/payroll/rules`                                  | `payroll.configure` |
| `POST`  | `/api/v1/payroll/profiles`                               | `payroll.configure` |
| `POST`  | `/api/v1/payroll/adjustments`                            | `payroll.run`       |
| `POST`  | `/api/v1/payroll/runs`                                   | `payroll.run`       |
| `POST`  | `/api/v1/payroll/runs/{runId}/calculate`                 | `payroll.run`       |
| `POST`  | `/api/v1/payroll/runs/{runId}/finalize`                  | `payroll.finalize`  |
| `GET`   | `/api/v1/payroll/runs/{runId}/payslips`                  | `payroll.read`      |
| `GET`   | `/api/v1/payroll/payslips/{payslipId}`                   | `payroll.read`      |
| `POST`  | `/api/v1/payroll/runs/{runId}/accounting-export?format=` | `payroll.export`    |

Le calcul supprime puis reconstruit les lignes non finalisées afin de rester reproductible. Une finalisation fige les bulletins et interdit toute modification ultérieure. Les exports `CSV` et `JSON` sont équilibrés, dédupliqués et associés à une empreinte SHA-256. Aucun taux fiscal ou social marocain n’est codé en dur : les règles restent configurables, et toute règle marquée comme juridiquement validée doit référencer sa source. L’avertissement de non-conformité de la configuration de démonstration est renvoyé par le dashboard et affiché dans l’interface.

### Comptabilité en partie double

| Méthode | Route                                                       | Permission             |
| ------- | ----------------------------------------------------------- | ---------------------- |
| `GET`   | `/api/v1/accounting/dashboard`                              | `accounting.read`      |
| `PUT`   | `/api/v1/accounting/configuration`                          | `accounting.configure` |
| `POST`  | `/api/v1/accounting/accounts`                               | `accounting.configure` |
| `POST`  | `/api/v1/accounting/journals`                               | `accounting.configure` |
| `POST`  | `/api/v1/accounting/fiscal-periods`                         | `accounting.configure` |
| `POST`  | `/api/v1/accounting/fiscal-periods/{periodId}/close`        | `accounting.configure` |
| `POST`  | `/api/v1/accounting/cost-centers`                           | `accounting.configure` |
| `POST`  | `/api/v1/accounting/suppliers`                              | `accounting.post`      |
| `POST`  | `/api/v1/accounting/supplier-invoices`                      | `accounting.post`      |
| `POST`  | `/api/v1/accounting/supplier-invoices/{invoiceId}/post`     | `accounting.post`      |
| `POST`  | `/api/v1/accounting/expenses`                               | `accounting.post`      |
| `POST`  | `/api/v1/accounting/expenses/{expenseId}/post`              | `accounting.post`      |
| `POST`  | `/api/v1/accounting/entries/{entryId}/reverse`              | `accounting.post`      |
| `POST`  | `/api/v1/accounting/bank-accounts`                          | `accounting.configure` |
| `POST`  | `/api/v1/accounting/reconciliations`                        | `accounting.reconcile` |
| `POST`  | `/api/v1/accounting/reconciliations/{id}/entries/{entryId}` | `accounting.reconcile` |
| `POST`  | `/api/v1/accounting/reconciliations/{id}/complete`          | `accounting.reconcile` |
| `POST`  | `/api/v1/accounting/family-ledger/sync`                     | `accounting.post`      |

Les écritures sont créées par les opérations métier guidées et non par une route générique. Chaque écriture postée possède au moins deux lignes, un seul côté positif par ligne et un total débit égal au total crédit. Une extourne crée une écriture inverse sans supprimer l’original. La synchronisation du Family Ledger est idempotente grâce à l’unicité tenant/origine, et un rapprochement terminé est immuable.

### Achats et fournisseurs

| Méthode | Route                                               | Permission              |
| ------- | --------------------------------------------------- | ----------------------- |
| `GET`   | `/api/v1/procurement/dashboard`                     | `procurement.read`      |
| `PUT`   | `/api/v1/procurement/configuration`                 | `procurement.configure` |
| `POST`  | `/api/v1/procurement/approval-thresholds`           | `procurement.configure` |
| `POST`  | `/api/v1/procurement/suppliers`                     | `procurement.configure` |
| `POST`  | `/api/v1/procurement/requests`                      | `procurement.request`   |
| `POST`  | `/api/v1/procurement/requests/{id}/submit`          | `procurement.request`   |
| `POST`  | `/api/v1/procurement/requests/{id}/decision`        | `procurement.approve`   |
| `POST`  | `/api/v1/procurement/requests/{id}/orders`          | `procurement.order`     |
| `POST`  | `/api/v1/procurement/orders/{id}/receipts`          | `procurement.receive`   |
| `POST`  | `/api/v1/procurement/orders/{id}/invoice`           | `procurement.invoice`   |
| `POST`  | `/api/v1/procurement/expenses`                      | `procurement.invoice`   |
| `POST`  | `/api/v1/procurement/invoice-matches/{id}/payments` | `procurement.pay`       |

Le serveur recalcule chaque total de demande, sélectionne un unique seuil non chevauchant et exige toutes les approbations prévues avant la commande. Une réception ne peut dépasser les quantités commandées. La facture exige une réception complète et un rapprochement exact commande–réception–facture ; elle est alors comptabilisée au travers du port public du module Comptabilité. Les paiements sont plafonnés au solde restant et créent automatiquement une écriture débit fournisseur/crédit trésorerie équilibrée. Les fournisseurs et dépenses restent les mêmes enregistrements de référence que dans M27.

### Inventaire

| Méthode | Route                                            | Permission            |
| ------- | ------------------------------------------------ | --------------------- |
| `GET`   | `/api/v1/inventory/dashboard`                    | `inventory.read`      |
| `POST`  | `/api/v1/inventory/locations`                    | `inventory.configure` |
| `POST`  | `/api/v1/inventory/items`                        | `inventory.write`     |
| `POST`  | `/api/v1/inventory/items/{itemId}/movements`     | `inventory.adjust`    |
| `POST`  | `/api/v1/inventory/assets`                       | `inventory.write`     |
| `POST`  | `/api/v1/inventory/assets/{assetId}/assignments` | `inventory.assign`    |
| `POST`  | `/api/v1/inventory/assignments/{id}/return`      | `inventory.assign`    |
| `PUT`   | `/api/v1/inventory/assets/{assetId}/status`      | `inventory.write`     |

Les soldes sont maintenus par article et emplacement, mais toute variation exige un `StockMovement` append-only. Une sortie est refusée si elle rendrait le solde négatif ; un transfert débite et crédite ses deux lieux dans la même transaction. Un actif identifié ne peut posséder qu’une affectation active selon les invariants du service. Sa restitution clôt l’affectation sans la supprimer, remet l’actif à disposition et ajoute un événement. Les changements vers maintenance, perdu ou retiré sont également historisés et sont refusés tant que l’actif reste affecté.

### Bibliothèque

| Méthode | Route                                      | Permission                   |
| ------- | ------------------------------------------ | ---------------------------- |
| `GET`   | `/api/v1/library/dashboard`                | `library.read`               |
| `PUT`   | `/api/v1/library/configuration`            | `library.configure`          |
| `POST`  | `/api/v1/library/authors`                  | `library.catalog.manage`     |
| `POST`  | `/api/v1/library/categories`               | `library.catalog.manage`     |
| `POST`  | `/api/v1/library/books`                    | `library.catalog.manage`     |
| `POST`  | `/api/v1/library/books/{bookId}/copies`    | `library.catalog.manage`     |
| `GET`   | `/api/v1/library/copies/scan?code=`        | `library.read`               |
| `POST`  | `/api/v1/library/loans`                    | `library.circulation.manage` |
| `POST`  | `/api/v1/library/loans/{loanId}/return`    | `library.circulation.manage` |
| `POST`  | `/api/v1/library/reservations`             | `library.circulation.manage` |
| `POST`  | `/api/v1/library/reservations/{id}/cancel` | `library.circulation.manage` |
| `POST`  | `/api/v1/library/fines/{fineId}/resolve`   | `library.fine.manage`        |
| `GET`   | `/api/v1/student-portal/library`           | `student-portal.read`        |
| `GET`   | `/api/v1/public/library?studentId=`        | cookie parent                |

Un exemplaire exige un code-barres ou un QR unique. Le serveur décide de la disponibilité, interdit le double prêt et maintient une file de réservation ordonnée : retour et annulation peuvent promouvoir automatiquement le prochain lecteur. Les pénalités sont optionnelles, calculées uniquement lors du retour en retard, plafonnées selon la configuration et jamais créées si la politique est désactivée. Le portail élève résout l’élève lié au JWT ; le portail famille limite toujours la lecture aux enfants associés au cookie parent.

Les endpoints publics fonctionnels exigent toujours le cookie opaque du portail famille, sauf le téléchargement documentaire qui exige son jeton signé limité. Les mutations internes revalident permission et tenant. Les charges de cantine et d'activité apparaissent dans l'API Finance existante.

Un échéancier accepte `MONTHLY`, `QUARTERLY`, `ANNUAL` ou `CUSTOM`. Pour les trois premières fréquences, le serveur répartit exactement le montant total sur le nombre de dates ; `CUSTOM` exige une liste de dates et montants dont la somme correspond au total. Le dashboard de recouvrement calcule, à `asOf`, l'attendu, le reçu, le taux, l'impayé et les tranches strictement supérieures à 7, 30 et 60 jours. Les rappels automatiques sont idempotents ; tous les canaux du profil local retournent `PREVIEW` tant qu'aucun fournisseur externe n'est branché.

L'initialisation d'un paiement en ligne exige un en-tête `Idempotency-Key`. Le webhook exige `X-SchoolZ-Event-Id` et `X-SchoolZ-Signature`, puis le serveur vérifie la signature, déduplique l'événement et appelle `verifyPayment` avant toute écriture ledger. Une double livraison renvoie son état dupliqué sans créer un second paiement. Le remboursement administratif est complet dans ce jalon, exige aussi une clé d'idempotence et crée une écriture compensatoire. Le seul `providerKey` livré est `sandbox` ; il n'effectue aucun débit réel.

| Méthode | Route                                                          | Usage parent                              |
| ------- | -------------------------------------------------------------- | ----------------------------------------- |
| `GET`   | `/api/v1/public/finance`                                       | ledger complet de son compte famille      |
| `GET`   | `/api/v1/public/finance/receipts/{receiptId}`                  | consulter un reçu appartenant à ce compte |
| `GET`   | `/api/v1/public/finance/installment-plans?asOf=`               | consulter ses échéanciers et statuts      |
| `GET`   | `/api/v1/public/finance/online-payments`                       | consulter ses intentions de paiement      |
| `POST`  | `/api/v1/public/finance/online-payments`                       | initialiser une intention idempotente     |
| `GET`   | `/api/v1/public/finance/online-payments/{id}`                  | consulter une intention liée à son compte |
| `POST`  | `/api/v1/public/finance/online-payments/{id}/sandbox-complete` | confirmer la démonstration sandbox        |

Ces routes exigent le cookie parent Admissions. Le tenant et le compte financier sont résolus côté serveur ; un reçu absent, d'un autre compte ou d'un autre tenant renvoie `404`.

La confirmation `sandbox-complete` est réservée au fournisseur local et simule la réception d'un événement correctement signé. Elle est exposée uniquement pour la démonstration et ne représente ni une autorisation bancaire ni un débit réel.

## API M32 — Alertes et early warning

| Méthode | Route                                           | Permission             |
| ------- | ----------------------------------------------- | ---------------------- |
| `GET`   | `/api/v1/early-warning/dashboard?asOf=`         | `early-warning.read`   |
| `POST`  | `/api/v1/early-warning/evaluate?asOf=`          | `early-warning.manage` |
| `POST`  | `/api/v1/early-warning/rules`                   | `early-warning.manage` |
| `POST`  | `/api/v1/early-warning/alerts/{id}/acknowledge` | `early-warning.manage` |
| `POST`  | `/api/v1/early-warning/alerts/{id}/resolve`     | `early-warning.manage` |

Le dashboard restitue les règles lisibles, les dernières mesures par élève, les alertes avec leurs preuves, les statistiques et l’historique d’évaluation. Une nouvelle évaluation rafraîchit l’alerte active au lieu de la dupliquer. Les actions exigent une note factuelle et ne suppriment ni la règle, ni les preuves. Les libellés de règle à caractère psychologique ou médical sont refusés côté serveur.

## API M33 — Workflow engine

| Méthode | Route                               | Permission        |
| ------- | ----------------------------------- | ----------------- |
| `GET`   | `/api/v1/workflows/dashboard`       | `workflow.read`   |
| `POST`  | `/api/v1/workflows`                 | `workflow.manage` |
| `PUT`   | `/api/v1/workflows/{id}/activation` | `workflow.manage` |
| `POST`  | `/api/v1/workflows/events`          | `workflow.manage` |

Un événement possède un UUID d’idempotence, un type pointé, un module source et un payload. Chaque workflow actif du tenant correspondant au trigger est évalué une seule fois pour cet UUID. Les conditions et actions sont exécutées dans leur ordre persistant. Une émission secondaire conserve l’événement racine et l’exécution parente ; l’ascendance, la profondeur maximale et le plafond global interrompent toute chaîne récursive avant la boucle infinie. Chaque étape demeure consultable dans `workflow_execution_log`.

## API M34 — Massar et intégrations

| Méthode | Route                                       | Permission           |
| ------- | ------------------------------------------- | -------------------- |
| `GET`   | `/api/v1/integrations/dashboard`            | `integration.read`   |
| `POST`  | `/api/v1/integrations/{adapterKey}/exports` | `integration.manage` |
| `POST`  | `/api/v1/integrations/{adapterKey}/imports` | `integration.manage` |
| `GET`   | `/api/v1/integrations/jobs/{jobId}/file`    | `integration.read`   |

L’export accepte `{ dataset, format }` avec `format` égal à `CSV` ou `XLSX`. Le téléchargement restitue le binaire généré avec son nom de fichier et l’en-tête `X-Content-SHA256`. L’import est multipart (`file`) et reçoit le `dataset` en paramètre ; il valide le schéma, les valeurs et la limite de 10 000 lignes, conserve le diagnostic ligne par ligne puis s’arrête avant toute écriture métier.

La clé `massar-file` est volontairement limitée au transport `FILE_ONLY`. Les schémas fournis sont des modèles d’échange SchoolZ : ils ne sont pas présentés comme des formats officiels Massar. Le serveur n’effectue aucun scraping et n’annonce aucune API officielle tant qu’une documentation et un accès légitimes ne sont pas disponibles.

## API M35 — Champs personnalisés

| Méthode | Route                                                        | Permission                        |
| ------- | ------------------------------------------------------------ | --------------------------------- |
| `GET`   | `/api/v1/custom-fields/dashboard?entityType=&entityId=`      | `custom-field.read`               |
| `POST`  | `/api/v1/custom-fields/definitions`                          | `custom-field.configure`          |
| `PUT`   | `/api/v1/custom-fields/definitions/{definitionId}`           | `custom-field.configure`          |
| `PUT`   | `/api/v1/custom-fields/definitions/{id}/entities/{entityId}` | `custom-field.value.write` + champ |
| `DELETE`| `/api/v1/custom-fields/definitions/{id}/entities/{entityId}` | `custom-field.value.write` + champ |

Le dashboard ne retourne que les définitions dont l’utilisateur possède la permission de lecture générale et, le cas échéant, la permission propre au champ. La cible métier est résolue dans le tenant courant. Le serveur normalise et range chaque valeur dans sa colonne typée, vérifie les options de sélection et refuse qu’une définition change de clé, de type ou d’entité après sa création.

## API M36 — Configuration métier

| Méthode | Route                                                   | Permission                      |
| ------- | ------------------------------------------------------- | ------------------------------- |
| `GET`   | `/api/v1/business-configuration/dashboard?category=`    | `business-configuration.read`   |
| `PUT`   | `/api/v1/business-configuration/items/{itemId}`         | `business-configuration.manage` |
| `POST`  | `/api/v1/business-configuration/items/{itemId}/reset`   | `business-configuration.manage` |

Une mise à jour reçoit `{ value, expectedVersion, reason }`. Le serveur revalide le type, les choix et les bornes, puis refuse avec `409` toute version devenue obsolète. Le reset utilise la même protection et crée une nouvelle révision : il ne supprime jamais l’historique. Le paramètre `category` est borné aux dix domaines déclarés et toutes les lectures restent limitées au tenant du JWT.

## API M37 — API publique serveur

| Méthode | Route                                    | Authentification / scope  |
| ------- | ---------------------------------------- | ------------------------- |
| `GET`   | `/api/public/v1/students?page=&size=`    | clé API / `students:read` |
| `GET`   | `/api/public/v1/invoices?page=&size=`    | clé API / `invoices:read` |
| `GET`   | `/api/public/v1/classes?page=&size=`     | clé API / `classes:read`  |
| `GET`   | `/api/v1/api-access/dashboard`           | JWT / `public-api.read`   |
| `POST`  | `/api/v1/api-access/clients`             | JWT / `public-api.manage` |
| `POST`  | `/api/v1/api-access/clients/{id}/revoke` | JWT / `public-api.manage` |

Les routes externes exigent `X-SchoolZ-Api-Key`. La valeur complète d’une nouvelle clé n’est renvoyée qu’à sa création ; la base conserve seulement son empreinte SHA-256 et un préfixe d’identification. Chaque client dispose de scopes explicites, d’un quota par minute, d’une expiration facultative et d’une révocation définitive. Les réponses incluent `X-Request-Id`, `X-RateLimit-Limit` et `X-RateLimit-Remaining`.

Les appels authentifiés sont journalisés avec leur résultat (`SUCCESS`, `FORBIDDEN`, `RATE_LIMITED`), leur durée et leur statut, sans secret. La pagination est bornée à 100 éléments et les DTO publics ne reflètent jamais les tables internes. Le contrat OpenAPI déclare le schéma `schoolzApiKey`. La clé fixe documentée dans le README n’existe que dans le profil de développement.

## API M38 — Webhooks sortants

| Méthode | Route                                              | Permission       |
| ------- | -------------------------------------------------- | ---------------- |
| `GET`   | `/api/v1/webhooks/dashboard`                       | `webhook.read`   |
| `POST`  | `/api/v1/webhooks/subscriptions`                   | `webhook.manage` |
| `POST`  | `/api/v1/webhooks/subscriptions/{id}/revoke`       | `webhook.manage` |
| `POST`  | `/api/v1/webhooks/events`                          | `webhook.manage` |
| `POST`  | `/api/v1/webhooks/deliveries/process-due`          | `webhook.manage` |
| `POST`  | `/api/v1/webhooks/deliveries/{id}/retry`           | `webhook.manage` |

La publication reçoit `{ eventType, idempotencyKey, payload }`. Une même clé d’idempotence dans un tenant retourne le même événement et ne recrée aucune livraison. Le récepteur obtient `X-SchoolZ-Event-Id`, `X-SchoolZ-Delivery-Id`, `X-SchoolZ-Request-Id`, `X-SchoolZ-Timestamp` et `X-SchoolZ-Signature`. La signature vaut `v1=HMAC_SHA256(secret, timestamp + "." + eventId + "." + body)`.

Une réponse `2xx` termine la livraison. Les autres réponses ou erreurs de transport créent une tentative en échec puis une reprise exponentielle, jusqu’au maximum de l’abonnement ; la livraison rejoint alors la dead letter. Le rejeu exige un motif audité. Le secret d’un abonnement est renvoyé uniquement à sa création et demeure chiffré AES-GCM côté serveur.

## API M39 — Recherche globale

| Méthode | Route                                  | Permission             |
| ------- | -------------------------------------- | ---------------------- |
| `GET`   | `/api/v1/search?q=&type=&page=&size=`  | `global-search.read`   |
| `POST`  | `/api/v1/search/refresh`               | `global-search.manage` |

La recherche porte sur les élèves, parents, professeurs, factures, classes et documents. La requête est normalisée en latin et en arabe, la taille est bornée à 50 et les facettes ne comptent que les types que le compte courant a le droit de consulter. Une reconstruction d’index reste explicite et permissionnée.

## API M40 — Centre de notifications

| Méthode | Route                                                | Permission                  |
| ------- | ---------------------------------------------------- | --------------------------- |
| `GET`   | `/api/v1/notification-center?status=&page=&size=`    | `notification-center.read`  |
| `PATCH` | `/api/v1/notification-center/{id}/read`              | `notification-center.read`  |
| `PATCH` | `/api/v1/notification-center/{id}/archive`           | `notification-center.read`  |
| `PUT`   | `/api/v1/notification-center/preferences`            | utilisateur authentifié     |

Les statuts acceptés sont `ALL`, `UNREAD`, `READ` et `ARCHIVED`. Les préférences push, e-mail et SMS sont enregistrées par utilisateur et par politique ; un canal obligatoire pour une notification critique ne peut pas être désactivé par le client.

## API M41 — Tâches internes

| Méthode | Route                                  | Permission     |
| ------- | -------------------------------------- | -------------- |
| `GET`   | `/api/v1/tasks?status=&priority=`      | `task.read`    |
| `POST`  | `/api/v1/tasks`                        | `task.manage`  |
| `PUT`   | `/api/v1/tasks/{id}/status`            | `task.manage`  |
| `PUT`   | `/api/v1/tasks/{id}/assignee`          | `task.manage`  |

Une tâche manuelle ou automatisée possède une priorité, une échéance, un responsable et un lien métier facultatif. Les tâches issues de `CREATE_TASK` sont idempotentes par exécution et action de workflow. Le professeur ne reçoit que les tâches qui lui sont affectées.

## API M42 — Calendrier établissement

| Méthode | Route                                         | Permission        |
| ------- | --------------------------------------------- | ----------------- |
| `GET`   | `/api/v1/calendar?from=&to=&type=&campusId=` | `calendar.read`   |
| `POST`  | `/api/v1/calendar`                            | `calendar.manage` |
| `PUT`   | `/api/v1/calendar/{id}/cancel`                | `calendar.manage` |

La période demandée est obligatoire et limitée à 370 jours. Les événements portent une audience établissement, campus ou classe. L’annulation conserve l’événement dans l’historique ; les champs de synchronisation sont réservés à un adaptateur externe ultérieur et ne prouvent aucune intégration active.

## API M46 — Données personnelles

| Méthode | Route | Permission |
| ------- | ----- | ---------- |
| `GET` | `/api/v1/privacy/dashboard` | `privacy.read` |
| `POST` | `/api/v1/privacy/purposes` | `privacy.manage` |
| `PUT` | `/api/v1/privacy/consents/{id}` | `privacy.manage` |
| `POST` | `/api/v1/privacy/requests` | `privacy.manage` |
| `PUT` | `/api/v1/privacy/requests/{id}` | `privacy.manage` |
| `POST` | `/api/v1/privacy/exports` | `privacy.export` |
| `PUT` | `/api/v1/privacy/retention-policies/{id}` | `privacy.manage` |
| `PUT` | `/api/v1/privacy/hosting` | `privacy.manage` |

Toutes les ressources sont limitées au tenant du JWT et les changements sont audités. Un export crée une trace d’exécution et ne transforme pas l’interface en mécanisme d’effacement automatique. Les décisions de conservation, d’hébergement et de transfert doivent être validées par le responsable de traitement et son conseil juridique.

## API M50 — Pagination des élèves

| Méthode | Route | Permission |
| ------- | ----- | ---------- |
| `GET` | `/api/v1/students/page?page=&size=` | `student.read` |
| `GET` | `/api/v1/students` | `student.read` |

La route paginée accepte une taille de 1 à 100 et renvoie un curseur logique `hasNext`. La route historique reste disponible pour compatibilité mais ne retourne jamais plus de 100 dossiers.

## Exploitation M48

`/actuator/health` expose uniquement l’état nécessaire au contrôle de disponibilité. Les métriques détaillées sont publiées au format Prometheus selon la politique d’accès de l’environnement ; elles couvrent notamment les temps de réponse HTTP et les files opérationnelles, sans libellé contenant une identité d’élève ou de famille.
