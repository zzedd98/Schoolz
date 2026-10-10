# ADR-002 — Stratégie multi-tenant

- Statut : accepté pour le socle
- Date : 2026-08-07

## Contexte

La plateforme sert plusieurs groupes scolaires. Une fuite entre tenants serait critique, tandis qu'une base par client dès le départ augmenterait fortement le coût d'exploitation, de migration et d'analytics consolidés.

## Décision

Utiliser une base PostgreSQL et un schéma partagés, avec `tenant_id` obligatoire sur toutes les données métier. Les identifiants, contraintes uniques et index sont tenant-aware. Le tenant est dérivé de la session authentifiée et propagé par une valeur typée non nullable.

Les repositories exposent des méthodes avec portée tenant. Les caches, objets S3, événements, jobs, recherches et audits incluent également cette portée. Des tests négatifs tentent systématiquement des accès cross-tenant.

PostgreSQL Row Level Security sera prototypé avant les premiers modules contenant des données d'élèves afin d'ajouter une défense en profondeur sans remplacer les contrôles applicatifs.

## Conséquences

La solution simplifie l'exploitation et les évolutions de schéma. En contrepartie, chaque requête et index doit conserver la portée tenant, et toute API utilisant un identifiant nu est considérée comme une violation d'architecture.

Une abstraction de résolution de datasource permettra ultérieurement de déplacer un tenant important ou réglementé vers une base dédiée sans modifier le domaine.

