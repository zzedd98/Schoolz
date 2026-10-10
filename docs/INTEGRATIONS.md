# Intégrations

Les fournisseurs externes seront placés derrière des ports : `PaymentProvider`, `MessagingAdapter`, `StorageProvider`, `AccountingAdapter`, `MassarAdapter` et `GpsProvider`. Le domaine ne dépendra d'aucun SDK fournisseur.

Les appels sortants critiques utilisent idempotence, retry borné, journal d'exécution et dead letter. Les secrets sont injectés par environnement. Aucun connecteur réel n'est activé au Milestone 0.

