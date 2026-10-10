# Stratégie de tests M49

La qualité repose sur plusieurs couches complémentaires. Un test vert ne prouve pas l’absence de défaut ; il réduit un risque identifié et doit rester relié à un scénario métier.

## Matrice

| Couche | Outil | Risques couverts |
|---|---|---|
| unités Java | JUnit 5, AssertJ, Mockito | calculs, validation, JWT, signature, fichiers, métriques |
| API/intégration | Spring Boot + MockMvc + H2 PostgreSQL mode | permissions, transitions, audit, uploads, idempotence, erreurs |
| base réelle | Testcontainers PostgreSQL 18 | 45 migrations, contraintes, index et compatibilité SQL |
| architecture | Spring Modulith | cycles et accès illégitime aux internes d’un module |
| frontend | Vitest + Angular TestBed | services HTTP, auth, i18n, PWA et navigation shell |
| statique | Spotless, ESLint, Prettier, TypeScript | cohérence, erreurs de typage et pratiques interdites |
| E2E déployé | `scripts/e2e-smoke.ps1` | santé, connexion et données réelles admin/professeur/élève/famille, routes SPA |
| navigateur | navigateur intégré | rendu, responsive, clavier, RTL, console et débordements |
| sécurité | tests négatifs + revue M45 | `401/403/404`, tenant escape, JWT, upload hostile, headers |
| reprise | restauration isolée M47 | dump réellement restaurable et cohérence minimale |

## Exécution complète

```powershell
.\scripts\verify.ps1
```

Backend seul :

```powershell
cd backend
.\mvnw.cmd verify
```

Le rapport JaCoCo est généré dans `backend/target/site/jacoco/index.html`. Il sert à détecter les zones non exercées ; aucun pourcentage isolé n’est présenté comme une garantie de qualité.

Frontend seul :

```powershell
cd frontend
npm run check
```

Après déploiement Docker :

```powershell
.\scripts\e2e-smoke.ps1 -BaseUrl http://localhost:8088
```

## Scénarios de non-régression obligatoires

1. connexion, rotation/rejeu, verrouillage et permissions ;
2. lecture d’un identifiant d’un autre tenant impossible ;
3. parent limité à ses enfants, élève à lui-même, professeur à son affectation ;
4. publication immuable des notes, bulletins, devoirs et documents ;
5. paiement et annulation par écriture compensatoire ;
6. import/upload hostile refusé avant persistance ;
7. webhooks idempotents, retries et dead letter ;
8. demandes de droits et exports traçables ;
9. migration H2 et PostgreSQL réelle ;
10. quatre profils de démonstration capables d’ouvrir leur page principale.

## Données et isolation

Chaque suite qui écrit doit utiliser une base dédiée ou nettoyer son périmètre. Les tests d’isolation créent explicitement un second tenant et vérifient à la fois la réponse et l’absence de mutation. Les suites volumineuses qui partagent H2 sont exécutées avec une URL nommée lorsqu’un contexte indépendant est nécessaire.

## CI

La CI compile et vérifie le backend, exécute les tests PostgreSQL si Docker est disponible, contrôle le frontend, audite ses dépendances et construit les images. Le smoke E2E doit être ajouté à l’environnement de recette après démarrage Compose ; il ne doit jamais utiliser les identifiants de démonstration en production.
