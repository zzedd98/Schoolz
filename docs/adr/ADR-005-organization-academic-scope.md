# ADR-005 — Portée organisation, campus et académique

- Statut : accepté
- Date : 2026-08-08

## Contexte

Un groupe scolaire doit administrer plusieurs établissements depuis un compte unique sans dupliquer ses années, niveaux ou paramètres communs, tout en empêchant les liens de données entre tenants.

## Décision

Le `Tenant` de sécurité représente le groupe isolé. Il possède exactement une `Organization`, un `SchoolSettings` et plusieurs `Campus`. Années, périodes, cycles et niveaux sont définis au niveau organisation. Les niveaux proposés sont associés explicitement à chaque campus. Salles et classes sont rattachées au campus ; une classe référence aussi l'année, le niveau et éventuellement une salle.

Toutes les tables portent `tenant_id`. Les relations structurantes utilisent des contraintes composites `(resource_id, tenant_id)` afin que PostgreSQL refuse un lien cross-tenant même si un contrôle applicatif était omis.

## Invariants

- une seule année `ACTIVE` par tenant ;
- début strictement antérieur à la fin ;
- périodes contenues dans l'année et sans chevauchement ;
- classe créée seulement pour un niveau offert par son campus ;
- salle éventuelle appartenant au même campus ;
- codes uniques dans leur portée métier.

## Conséquences

La direction dispose d'une vue consolidée multi-campus et les référentiels restent cohérents. Les futures inscriptions pourront référencer une classe sans recopier année, campus ou niveau. La suppression physique n'est pas exposée ; les changements d'activité utiliseront des statuts afin de préserver l'historique.
