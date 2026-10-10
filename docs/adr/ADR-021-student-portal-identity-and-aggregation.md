# ADR-021 — Identité et agrégation du portail élève

## Statut

Accepté — 10 août 2026.

## Contexte

Le portail famille M14 repose volontairement sur une session parent et peut agréger plusieurs enfants. Il ne constitue donc pas une identité élève indépendante et ne doit pas être réutilisé en changeant simplement son libellé. Un élève doit retrouver uniquement son dossier pédagogique, sans pouvoir choisir un autre élève ni entrer dans le backoffice.

## Décision

- un compte interne portant le rôle `STUDENT` est relié par `student_portal_profile` à exactement un `StudentProfile` du même tenant ;
- les contraintes tenant-scoped et les deux unicités empêchent qu'un compte représente plusieurs élèves ou qu'un élève soit associé à plusieurs comptes ;
- les permissions `student-portal.read/write` protègent la façade et la mutation du statut personnel d'un devoir ;
- `studentportal` agrège les données publiées de `timetable`, `homework`, `grading` et `attendance` sans les recopier ;
- l'interface `/student` est séparée du shell administratif et les gardes de navigation redirigent les rôles vers leur espace, sans remplacer les contrôles serveur ;
- lorsqu'une date sans cours est demandée, la façade recherche la prochaine journée scolaire disponible pour éviter un accueil vide trompeur.

## Conséquences

- l'identité scolaire ne dépend jamais d'une correspondance fragile sur l'e-mail ou le nom ;
- les parents conservent leur vue multi-enfants et l'élève dispose d'un espace personnel plus simple ;
- toute nouvelle donnée visible par l'élève doit rester la propriété de son module métier et être publiée avant agrégation ;
- une future messagerie ou remise de copie nécessitera ses propres permissions et invariants, sans élargir implicitement `student-portal.write`.
