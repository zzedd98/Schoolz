# Observabilité et exploitation

Le jalon M48 fournit des signaux techniques sans inclure de données scolaires, de santé, financières ou d’identité dans les métriques.

## Santé

- `/actuator/health/liveness` : le processus peut-il continuer à tourner ? Cette route reste interne à la plateforme.
- `/actuator/health/readiness` : l'instance peut-elle recevoir du trafic ? Cette route reste interne à la plateforme.
- indicateur `schoolzBacklog` : volume agrégé des dead letters webhook, workflows échoués, notifications échouées, demandes de droits en retard et tâches en retard.

Un backlog non nul produit l’état `DEGRADED`, pas une panne immédiate. L’orchestrateur ne doit pas redémarrer une instance uniquement pour un incident métier ; l’alerte doit déclencher une investigation humaine.

## Métriques

Le registre Prometheus est disponible via `/actuator/prometheus` selon les règles de sécurité de l’environnement. Les séries utiles sont :

- `http_server_requests_seconds_*` avec histogrammes et seuils 100 ms, 300 ms, 1 s et 3 s ;
- `jvm_*`, `process_*`, `system_*` et `hikaricp_*` ;
- `schoolz_backlog_webhook_dead_letters` ;
- `schoolz_backlog_workflow_failed` ;
- `schoolz_backlog_notification_failed` ;
- `schoolz_backlog_privacy_overdue` ;
- `schoolz_backlog_task_overdue`.

Les métriques n’utilisent ni `tenantId`, ni identifiant utilisateur, ni URL contenant une ressource comme label, afin d’éviter fuite et cardinalité incontrôlée.

## Journaux et corrélation

Chaque requête reçoit un `X-Correlation-Id`. Une valeur entrante n’est reprise que si elle contient 8 à 100 caractères sûrs ; sinon un UUID est généré. L’identifiant est placé dans le MDC et renvoyé au client. En profil `prod`, Spring Boot écrit les logs console au format ECS JSON.

Ne jamais journaliser corps de webhook, token, mot de passe, document, note, donnée de santé ou contenu de message. Les erreurs utilisateur restent dans des réponses contrôlées ; les erreurs 5xx sont corrélées par les métriques HTTP et l’identifiant de requête.

## Alertes proposées

| Signal | Avertissement | Critique |
|---|---:|---:|
| taux de 5xx sur 5 min | > 1 % | > 5 % |
| p95 API sur 10 min | > 1 s | > 3 s |
| dead letters webhook | > 0 pendant 15 min | croissance continue 30 min |
| workflows échoués | > 5 nouveaux/15 min | > 20 nouveaux/15 min |
| connexions Hikari utilisées | > 80 % | > 95 % |
| demandes de droits échues | > 0 | > 5 ou > 24 h |
| dernière sauvegarde validée | > 24 h | > 48 h |

Ces seuils doivent être recalibrés avec le trafic réel. Un fournisseur d’alerting ou d’erreur externe n’est pas simulé : il doit être configuré avec filtrage des données sensibles, région approuvée, DPA et rétention validée.

## Accès et production

Les endpoints Actuator autres que la santé ne doivent pas être exposés publiquement. En production, les placer sur un réseau d’administration ou un port de management séparé, avec authentification machine, TLS et allowlist. Les dashboards doivent afficher des agrégats et relier une alerte au `correlationId`, jamais copier les données métier dans les labels.
