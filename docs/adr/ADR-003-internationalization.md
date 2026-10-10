# ADR-003 — Internationalisation français/arabe

- Statut : accepté
- Date : 2026-08-07

## Décision

Les locales de référence sont `fr-MA` et `ar-MA`. Le frontend charge des catalogues structurés, applique `dir="rtl"` pour l'arabe et utilise les API de formatage par locale. Le backend conserve des codes stables et ne persiste pas de libellés traduits comme vérité métier.

Les documents générés utilisent des templates versionnés par langue avec polices couvrant l'arabe. Dates techniques et montants sont stockés indépendamment de leur rendu ; MAD est la devise par défaut mais pas une constante globale.

## Conséquences

Chaque capacité doit être testée en LTR et RTL. Les recherches prévoient normalisation arabe et latine sans supprimer l'orthographe originale. Une traduction manquante est une erreur de qualité, pas un fallback silencieux vers une chaîne codée en dur.

