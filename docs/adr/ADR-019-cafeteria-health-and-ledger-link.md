# ADR-019 — Santé cantine et lien ledger

Accepté — Milestone 22.

Les restrictions alimentaires sont séparées des présences et abonnements. Leur lecture interne exige `cafeteria.health`; le portail parent est limité aux enfants liés au compte.

Tout abonnement mensuel ou repas ponctuel payant produit une `billing_charge` `CAFETERIA`, référencée par `cafeteria_meal_charge`. La cantine ne maintient pas un second solde.
