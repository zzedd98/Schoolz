# ADR-004 — Authentification et sessions rotatives

- Statut : accepté
- Date : 2026-08-08

## Contexte

SchoolZ manipule des données scolaires sensibles dans une application web first-party multi-tenant. Une session doit pouvoir être révoquée rapidement, limiter l'impact d'une fuite navigateur et conserver l'identité tenant côté serveur.

## Décision

Le frontend conserve un JWT d'accès de courte durée uniquement en mémoire. Le backend émet en parallèle un refresh token opaque, aléatoire et rotatif dans un cookie `HttpOnly`, `SameSite=Strict` et `Secure` en production. Seule l'empreinte HMAC du refresh token est stockée. Chaque rotation lie l'ancien et le nouveau token dans une famille ; la réutilisation d'un token déjà consommé révoque toute la famille.

Le JWT contient l'identifiant de compte et du tenant, mais le filtre de sécurité recharge le compte, son statut, ses rôles et ses permissions à chaque requête. Le tenant effectif provient donc toujours d'un principal validé par le serveur. Les changements de mot de passe et de statut invalident les sessions actives.

## Conséquences

- aucun secret de session long terme n'est accessible au JavaScript ou stocké dans `localStorage` ;
- les permissions révoquées prennent effet sans attendre l'expiration du JWT ;
- le backend effectue une lecture d'identité par requête, coût accepté au jalon 1 et optimisable plus tard par cache tenant-aware à invalidation stricte ;
- le cookie de refresh ne peut pas autoriser une opération métier et reste limité au chemin `/api/v1/auth` ;
- la protection dépend de secrets externes suffisamment aléatoires et d'un transport HTTPS en production.

## Alternatives rejetées

- JWT longue durée sans état serveur : révocation trop lente ;
- refresh token dans `localStorage` : exposition accrue en cas de XSS ;
- session serveur classique unique : moins adaptée aux futurs portails/mobile et à la rotation par famille.
