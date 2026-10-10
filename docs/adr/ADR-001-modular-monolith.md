# ADR-001 — Monolithe modulaire

- Statut : accepté
- Date : 2026-08-07

## Contexte

SchoolZ couvre de nombreux domaines fortement transactionnels et une petite équipe doit pouvoir livrer rapidement un produit cohérent. Des microservices imposeraient prématurément orchestration distribuée, cohérence éventuelle, contrats réseau, observabilité et opérations multiples.

## Décision

Le backend démarre comme un monolithe modulaire Spring Boot. Chaque domaine occupe un module Spring Modulith avec API explicite et implémentation interne. Les modules communiquent par services exposés et événements de domaine, jamais par accès direct aux tables ou repositories voisins.

Les tests Spring Modulith détectent les cycles et violations. PostgreSQL reste une base unique au départ ; les migrations et tables conservent une propriété claire par module.

## Conséquences

Positives : transactions locales simples, déploiement unique, refactoring plus sûr, coût opérationnel réduit et développement de bout en bout rapide.

Négatives : discipline stricte nécessaire pour éviter le monolithe enchevêtré, déploiement global et scaling moins granulaire.

## Critères de réévaluation

Un module ne sera extrait que si des preuves montrent un besoin indépendant de disponibilité, de cadence de déploiement, de contraintes réglementaires ou de charge, et si ses contrats et données sont déjà suffisamment isolés.

