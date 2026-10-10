# ADR-017 — Preuve de signature électronique simple

Accepté — Milestone 20.

Un workflow fige l'identifiant exact de version puis progresse strictement de `GENERATED` à `SIGNED`. Chaque transition ajoute un événement horodaté avec acteur et IP. La signature conserve nom, fournisseur, référence et empreinte d'évidence.

`DocumentSignatureProvider` isole une future intégration certifiée. `local-simple` annonce `SIMPLE_ELECTRONIC_ACCEPTANCE`, `qualified=false` et n'est jamais présenté comme une signature qualifiée.
