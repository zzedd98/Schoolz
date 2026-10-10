# Conventions d'ingénierie

## Branches et commits

- Branches de travail : `codex/<sujet>` ou `<type>/<sujet>` selon l'outil utilisé.
- Commits : Conventional Commits, par exemple `feat(identity): add tenant-aware login`.
- Un commit doit rester cohérent, testable et ne pas mélanger refactoring sans rapport et fonctionnalité.

## Backend

- Java 21, UTF-8, format Google Java Format via Spotless.
- Package racine : `ma.schoolz.erp`.
- Une racine de package directement sous le package principal représente un module Spring Modulith.
- Structure interne recommandée : `api`, `application`, `domain`, `infrastructure` ; seuls les contrats explicitement exposés peuvent être utilisés par un autre module.
- Les contrôleurs manipulent des DTO, jamais des entités JPA.
- Les règles métier vivent dans le domaine ou les services applicatifs, jamais dans les contrôleurs.
- Identifiants UUID ; montants `BigDecimal` + code devise ; dates métier `LocalDate`/`LocalTime`, événements techniques `Instant`.
- Une migration Flyway est immuable après partage. Toute correction passe par une nouvelle migration.

## Frontend

- Angular standalone, TypeScript strict, formulaires réactifs.
- Signals pour l'état local synchrone ; RxJS pour les flux asynchrones et HTTP.
- Aucune chaîne visible dans un composant : les textes passent par les catalogues `fr-MA` et `ar-MA`.
- Les composants métier ne connaissent pas les URL brutes ; ils utilisent les clients d'API.
- Chaque écran gère explicitement chargement, vide, erreur et succès.
- Navigation clavier, focus visible, contrastes WCAG AA et mise en page RTL obligatoires.

## API et données

- Base path : `/api/v1`.
- Erreurs au format Problem Details (`application/problem+json`).
- Pagination obligatoire pour les collections non bornées.
- Les commandes externes ou financières reçoivent une clé d'idempotence.
- Les données métier portent un `tenant_id`; `organization_id` et `campus_id` sont ajoutés selon leur portée.
- Les exports sensibles et mutations importantes génèrent un audit append-only.

## Definition of Done

Une capacité est terminée lorsque modèle, migration, validation, règles, API, autorisation, audit requis, UI, i18n, états UX, tests et documentation sont présents et que toutes les suites passent.

