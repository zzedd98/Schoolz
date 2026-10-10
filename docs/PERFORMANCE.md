# Performance et capacité M50

## Budget

- listes administratives obligatoirement paginées, taille maximale serveur 100 ;
- p95 lecture interactive cible < 1 s, critique > 3 s ;
- aucun export massif dans une réponse JSON interactive ;
- pool JDBC borné et métriques Hikari supervisées ;
- traitements de fichier bornés à 10 000 lignes par défaut ;
- métriques sans label tenant ou identifiant à forte cardinalité.

## Annuaire élèves

L’ancien endpoint liste reste compatible mais est plafonné à 100 lignes. Le frontend utilise `/api/v1/students/page?page=…&size=40`, récupère `size + 1` lignes pour déterminer `hasNext` et ne lance aucun `count(*)` global à chaque frappe.

La migration V46 ajoute des index composites pour le tri annuaire, l’identifiant principal, l’inscription active, les demandes de droits à échéance et la file webhook. La requête annuaire joint les données nécessaires en une seule fois ; la fiche 360° conserve plusieurs requêtes bornées à un élève, ce qui n’est pas un N+1 de liste.

## Dataset 20 000 élèves

`SchoolScalePerformanceIT` démarre PostgreSQL 18 isolé, applique les 46 migrations, génère 20 000 personnes, profils et matricules via `generate_series`, analyse les tables, exécute une page de 41 lignes et vérifie la présence des quatre index d’annuaire. La limite temporelle de 5 secondes sert uniquement de garde-fou CI très large ; les objectifs réels sont mesurés par environnement.

## Charge HTTP

Après déploiement local :

```powershell
.\scripts\load-test.ps1 -BaseUrl http://localhost:8088 -Requests 240 -Concurrency 12
```

Le script se connecte comme administrateur, envoie des lots concurrents sur la page de 40 élèves, compte les statuts et publie durée, requêtes/seconde et p95 des lots. Le débit par défaut est limité à 24 requêtes/s, sous la limite locale Nginx de 30 requêtes/s ; `TargetRps` ne peut pas la dépasser. Il ne modifie aucune donnée. Pour une campagne de production, utiliser un outil dédié depuis un réseau séparé, plusieurs scénarios/tenants, une montée progressive et des identifiants de test propres.

Preuve locale M51 du 10 août 2026 : 240 requêtes, concurrence 12, débit cible 24 req/s, 240 réponses HTTP 200, aucun échec, débit observé 23,4 req/s et p95 de lot 55 ms. Cette mesure Docker locale n'est pas un engagement de capacité de production.

## Revue des risques

- les recherches contenant `%terme%` restent coûteuses sur de grands volumes ; la recherche globale M39 doit être privilégiée pour les recherches transverses et un index spécialisé PostgreSQL devra être validé si le besoin de sous-chaîne est confirmé ;
- les pages à offset élevé peuvent évoluer vers un curseur `(lastName, firstName, id)` si la navigation profonde est réellement utilisée ;
- PDF, imports et exports doivent être placés en traitement asynchrone à mesure que les volumes réels dépassent les plafonds interactifs ;
- la mesure Docker locale ne prédit pas les performances de l’hébergeur, du réseau, du chiffrement ou des fournisseurs externes.
