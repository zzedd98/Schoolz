# ADR-018 — Fournisseur GPS et présence bus

Accepté — Milestone 21.

Trajet, montée, descente et position restent des faits séparés. La capacité est contrôlée au pointage. Une famille ne voit que les abonnements de ses enfants et l'ETA n'existe que si une position exploitable est disponible.

`GpsProvider` calcule l'ETA sans exposer un modèle fournisseur au domaine. `local-demo` fournit une estimation géométrique de démonstration et aucune promesse de temps réel externe.
