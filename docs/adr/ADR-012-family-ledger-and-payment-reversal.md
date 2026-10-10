# ADR-012 — Family ledger et annulation compensatoire

- Statut : accepté
- Date : 2026-08-09

## Contexte

La situation financière d'une famille ne peut pas être déduite d'un simple champ « solde ». Une facture peut recevoir plusieurs règlements, un règlement peut couvrir plusieurs factures, et des avoirs, remboursements ou corrections doivent rester explicables plusieurs années après leur saisie. Supprimer ou réécrire un paiement validé détruirait la piste comptable et rendrait un reçu déjà transmis incohérent.

Le produit doit aussi isoler strictement chaque établissement et attribuer des numéros de facture, paiement et reçu uniques même lorsque plusieurs caisses enregistrent des opérations simultanément.

## Décision

Créer `billing` comme propriétaire du compte financier familial et de ses écritures, avec les règles suivantes :

- le solde affiché est reconstruit depuis un ledger append-only et non maintenu comme vérité isolée ;
- une facture est modifiable en brouillon, puis figée lors de son émission ;
- chaque ligne conserve service, bénéficiaire, quantité, prix, remise et montant net ;
- `payment_allocation` matérialise la relation plusieurs-à-plusieurs entre paiements et factures ;
- tout paiement validé produit un reçu numéroté unique ;
- une annulation crée un paiement inverse, des allocations négatives et un avis d'annulation, sans effacer l'original ;
- avoirs, remboursements et ajustements sont des documents dédiés, liés au même compte ;
- les séquences documentaires sont verrouillées par tenant et année avant attribution, avec unicité PostgreSQL en défense supplémentaire ;
- chaque mutation opérationnelle écrit un événement dans l'audit global.

Le portail parent réutilise la session opaque Admissions. Le serveur en déduit le tenant et le compte famille ; aucun identifiant financier fourni par le navigateur ne suffit à obtenir un ledger ou un reçu.

## Conséquences

- la somme des allocations d'un paiement ne peut pas dépasser son montant et celle d'une facture ne peut pas dépasser son reste à payer ;
- un paiement peut couvrir plusieurs factures et chaque facture accepter plusieurs paiements sans structure spéciale ;
- les reçus déjà transmis restent vérifiables après correction grâce au statut et à l'avis inverse ;
- les tableaux de bord peuvent expliquer chaque total en descendant jusqu'aux écritures sources ;
- les échéanciers et le recouvrement du jalon 16 s'appuieront sur les factures et soldes existants sans dupliquer la vérité financière ;
- une exportation comptable future traduira ces écritures, mais ne remplacera pas le ledger opérationnel.
