# ADR-014 — Port fournisseur de paiement et webhooks signés

## Statut

Accepté — Milestone 17.

## Contexte

Le ledger SchoolZ doit recevoir des paiements en ligne sans dépendre des DTO, statuts ou garanties d'un prestataire particulier. Les confirmations externes peuvent être retardées, livrées plusieurs fois, falsifiées ou diverger de l'état réellement détenu par le fournisseur. Aucun prestataire bancaire marocain ne peut être inventé sans documentation officielle et contrat d'intégration.

## Décision

Le module `billing` définit le port `PaymentProvider` avec quatre opérations : `initializePayment`, `handleWebhook`, `verifyPayment` et `refund`. L'orchestrateur persiste d'abord une intention tenant-scoped et idempotente, puis délègue au fournisseur sélectionné.

Chaque webhook est traité selon la séquence suivante :

1. valider la signature HMAC avec comparaison en temps constant ;
2. conserver l'identifiant d'événement, l'empreinte SHA-256 et l'état de traitement, jamais le corps brut ;
3. dédupliquer toute livraison déjà connue ;
4. vérifier l'état et le montant auprès du fournisseur ;
5. créer une unique écriture `ONLINE_PAYMENT` et ses allocations dans le ledger ;
6. auditer l'effet ou l'échec contrôlé.

Le remboursement M17 est complet : le fournisseur est appelé de manière idempotente, puis le ledger crée une écriture compensatoire sans modifier ni supprimer le paiement original.

Le profil local branche uniquement `SandboxPaymentProvider`. Il génère et valide les mêmes événements signés que l'orchestrateur attend, mais n'effectue aucun débit bancaire réel. Une intégration réelle ne sera ajoutée qu'à partir de documentation officielle, de secrets externalisés et de tests contractuels dédiés.

## Conséquences

- le ledger reste la source de vérité financière et ne connaît aucun SDK fournisseur ;
- un second fournisseur implémente le port sans changer les contrôleurs famille ni les règles comptables ;
- signature, idempotence, vérification et compensation sont testables sans réseau ;
- les tentatives invalides restent observables sans persister de données de paiement brutes ;
- les remboursements partiels et la réconciliation asynchrone avancée restent des extensions futures explicites.
