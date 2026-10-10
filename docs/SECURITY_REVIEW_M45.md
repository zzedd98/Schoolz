# Revue de sécurité M45

Date de revue : 10 août 2026. Cette revue est une vérification technique interne ; elle ne remplace ni un test d’intrusion indépendant, ni une certification.

## Contrôles vérifiés

| Risque | Protection vérifiée | Preuve automatisée |
|---|---|---|
| IDOR et fuite inter-tenant | Le tenant et les relations parent–élève, professeur–personnel et élève–profil sont recalculés depuis le principal. Les accès croisés renvoient `404` ou `403`. | Suites d’intégration identité, élèves, portails, finance et modules métier ; test `tenant escape` identité. |
| Autorisation cassée | Permissions fines contrôlées côté service avec refus par défaut. Les DTO d’entrée ne portent pas de `tenantId` autoritaire. | Tests `401`, `403`, permission et rôles de l’identité. |
| Injection SQL | Valeurs métier passées comme paramètres JDBC/JPA. Les fragments de table ou d’état encore dynamiques sont issus d’énumérations internes fermées, jamais de la requête HTTP. Les compteurs M41 ont été convertis en requête paramétrée fixe. | Tests API avec filtres invalides et revue statique M45. |
| XSS | Interpolation Angular échappée, absence d’usage applicatif de `innerHTML`, `eval` ou contournement du sanitizer, CSP restrictive et `nosniff`. | ESLint, recherche statique M45 et tests frontend. |
| CSRF | L’API métier est bearer stateless. Le refresh opaque est limité aux routes d’authentification, en cookie `HttpOnly`, `SameSite=Strict` et `Secure` en production ; CORS est sur liste blanche. | Tests de session/rotation/rejeu et configuration Spring Security. |
| JWT | Signature HMAC, algorithme imposé, `jti`, bornes `iat`/`exp`, durée maximale, taille maximale 8 Kio et rejet des jetons altérés ou non signés. | `JwtServiceTest`. |
| Upload hostile | Nom normalisé sans traversée, taille et MIME autorisés, signature réelle PDF/PNG/JPEG/OOXML, UTF-8 valide, empreinte et adaptateur anti-malware. | `FileSecurityScannerTest` et scénario API refusant un faux PNG et la signature EICAR sans persistance. |
| Brute force / abus | Verrouillage progressif après cinq échecs, réponse générique, limite proxy dédiée au login (10 requêtes/minute/IP) et limite API générale (30 requêtes/seconde/IP). | Tests identité et configuration Nginx. |
| Navigateur | CSP, interdiction d’iframe, referrer/permissions policy, isolation de contexte COOP/CORP, cache interdit pour l’API et HSTS lorsque TLS est actif. | Test MockMvc des en-têtes et configuration des deux proxies Nginx. |
| Secrets et traces | Secrets fournis par environnement, réponses API sans stacktrace en production, corps de webhook et jetons non journalisés, corrélation sans donnée métier. | Profils de configuration, tests webhook et revue M45. |

## Chaîne de contrôle des fichiers

Le contrôle local reconnaît la signature de test EICAR et constitue un garde-fou déterministe pour le développement et les tests. Ce n’est pas un antivirus de production. L’interface `MalwareScanAdapter` doit être reliée avant mise en production à un moteur maintenu (par exemple ClamAV isolé ou un service de scanning fournisseur), avec délai maximal, politique de panne fermée, quarantaine, métriques et mise à jour des signatures.

Les documents sont inspectés avant toute persistance métier. L’externalisation vers MinIO/S3 devra conserver ce passage obligatoire avant promotion depuis une zone de quarantaine vers le stockage privé définitif.

## Risques résiduels et prérequis de production

- test d’intrusion indépendant authentifié sur chaque rôle et campagne automatisée DAST ;
- limite distribuée pour un déploiement multi-instance et protections dédiées à `reset`, `refresh` et aux endpoints publics coûteux ;
- KMS/secret manager, rotation documentée et chiffrement du stockage/backup géré par l’hébergeur ;
- antivirus de production et procédure de quarantaine ;
- terminaison TLS durcie, HSTS long après validation du domaine et supervision des certificats ;
- revue de dépendances/SBOM continue et traitement des alertes de sécurité ;
- revue juridique et données personnelles décrite au jalon M46.

## Décision

Les contrôles applicatifs attendus au jalon M45 sont implémentés et couverts localement. Le produit ne doit pas être présenté comme « certifié » ou « invulnérable » : les risques résiduels ci-dessus restent des conditions explicites de mise en production.
