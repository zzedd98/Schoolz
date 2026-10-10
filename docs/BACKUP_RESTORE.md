# Sauvegarde, restauration et reprise

Le jalon M47 considère une sauvegarde comme valide uniquement après une restauration réelle. Redis reste éphémère ; PostgreSQL, le stockage objet et les configurations/secrets versionnés hors dépôt constituent le périmètre durable.

## Objectifs à faire approuver

| Niveau | RPO cible | RTO cible | Usage |
|---|---:|---:|---|
| Démonstration locale | 24 h | 4 h | reprise manuelle acceptable |
| Production standard proposée | 15 min | 2 h | PITR PostgreSQL managé + objets versionnés |
| Critique | 5 min | 30 min | réplication multi-zone et automatisation fournisseur |

Ces valeurs sont des propositions techniques, pas des engagements contractuels. L’établissement doit choisir son niveau après analyse d’impact.

## Sauvegarde locale reproductible

Depuis la racine du projet :

```powershell
.\scripts\backup.ps1 -EnvFile .env
```

Le script produit dans `backups/` : un dump PostgreSQL au format custom, une archive du volume MinIO et un manifeste JSON avec empreintes SHA-256. Les fichiers temporaires portent un nom précis sous `/tmp` dans chaque conteneur puis sont supprimés. Aucun secret n’est copié dans le manifeste.

Pour PostgreSQL seul :

```powershell
.\scripts\backup.ps1 -EnvFile .env -SkipObjects
```

Le dossier `backups/` doit être chiffré, répliqué hors site et soumis à une politique d’accès/restauration. Un dump local sur le même disque ne constitue pas une stratégie de production.

## Test de restauration réel

```powershell
.\scripts\restore-test.ps1 -EnvFile .env
```

Le script crée un dump courant, restaure dans une base isolée nommée exclusivement `schoolz_restore_test_<horodatage>`, vérifie la dernière migration Flyway et les tables d’identité, puis supprime cette base de test dans un bloc `finally`. La base `school_erp` n’est jamais écrasée.

La preuve attendue ressemble à :

```text
RESTORE_OK database=schoolz_restore_test_... migration=46 tenants=1 users=...
```

Dernière preuve locale M51, exécutée le 10 août 2026 : migration 46, 1 tenant et 4 utilisateurs restaurés puis base de contrôle supprimée.

## Runbook de sinistre

1. déclarer l’incident, figer les écritures et désigner le responsable de reprise ;
2. identifier le dernier point cohérent avant incident et préserver les journaux ;
3. provisionner une infrastructure propre, avec secrets et clés restaurés depuis le gestionnaire dédié ;
4. restaurer PostgreSQL par PITR ou dump, puis vérifier Flyway, tenants, comptes et totaux financiers ;
5. restaurer les objets versionnés et contrôler un échantillon d’empreintes/références ;
6. démarrer l’API sans trafic, exécuter smoke tests, contrôles d’isolation et rapprochements ;
7. rouvrir progressivement, superviser erreurs/latence et consigner l’heure effective de reprise ;
8. notifier selon le plan d’incident et mener le retour d’expérience.

## Production

- sauvegardes automatiques PostgreSQL chiffrées, PITR et copie dans un compte/projet séparé ;
- versioning, verrouillage et réplication du bucket objet ;
- sauvegarde des configurations sans secrets en clair, et procédure séparée de récupération KMS ;
- test mensuel automatisé dans un environnement isolé, test complet trimestriel avec preuve signée ;
- alerte sur ancienneté, échec, taille anormale et restauration non testée ;
- politique de rétention alignée avec le registre M46 et suppression vérifiable à échéance ;
- contrôle explicite de la localisation de chaque copie et validation juridique des transferts.

## Limites locales

L’archive MinIO locale est cohérente au niveau fichier pour une démonstration arrêtée ou peu active. En production, utiliser snapshots/versioning fournisseur et coordination applicative. Les volumes Docker, mots de passe de `.env.example` et Redis local ne sont pas des mécanismes de reprise de production.
