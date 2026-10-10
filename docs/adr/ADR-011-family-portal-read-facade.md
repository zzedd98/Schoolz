# ADR-011 — Façade de lecture du portail famille

- Statut : accepté
- Date : 2026-08-09

## Contexte

Une famille doit utiliser un seul compte et changer d'enfant sans multiplier les connexions. L'accueil doit réunir scolarité, emploi du temps, devoirs, notes, bulletins et absences déjà possédés par des modules distincts. Dupliquer ces données dans un domaine « portail » créerait une seconde source de vérité, des synchronisations fragiles et un risque d'exposer un enfant non lié.

Les services finance, communications, documents, transport et activités ne sont pas encore tous livrés au jalon 14. Les simuler dans l'interface rendrait la démonstration trompeuse.

## Décision

Créer `familyportal` comme façade de lecture tenant-scoped au-dessus de la session parent Admissions et du contrat public `PortalStudentAccess`.

La façade :

- recalcule la liste des enfants liés depuis le compte authentifié ;
- choisit en priorité l'enfant scolarisé, puis conserve une préférence explicite dans `family_portal_preference` ;
- agrège uniquement les plannings, évaluations, notes, devoirs et bulletins publiés ainsi que les faits d'assiduité existants ;
- reteste le lien compte–enfant pour chaque sélection et téléchargement de bulletin ;
- expose la disponibilité réelle des modules, avec leur jalon futur lorsqu'ils ne sont pas encore livrés ;
- ne possède aucune mutation pédagogique, financière ou documentaire.

La PWA met en cache uniquement l'enveloppe Angular et les ressources statiques. Les routes `/api/**` sont exclues du service worker afin qu'aucun tableau de bord familial ne soit conservé hors ligne.

## Conséquences

- un compte parent ne peut pas élargir son périmètre en modifiant `studentId` ;
- les corrections de notes, absences, devoirs ou bulletins deviennent visibles sans synchronisation dédiée ;
- la préférence d'enfant est la seule donnée métier propre au portail ;
- chaque futur module remplace sa carte indisponible par un accès réel sans changer l'identité familiale ;
- un mode hors ligne de données sensibles nécessiterait une décision de sécurité séparée et n'est pas implicite dans l'installation PWA.
