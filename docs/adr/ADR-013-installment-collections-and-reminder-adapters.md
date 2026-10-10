# ADR-013 — Échéanciers, recouvrement et adaptateurs de rappel

- Statut : accepté
- Date : 2026-08-09

## Contexte

Le ledger du jalon 15 explique les montants réellement facturés et encaissés, mais il ne suffit pas à représenter les dates auxquelles une famille s'est engagée à payer. Le recouvrement doit comparer attendu et reçu à une date donnée, distinguer un retard récent d'un impayé ancien et préparer des actions cohérentes sans transformer un appel à un fournisseur externe en partie de la transaction financière.

Un même établissement peut choisir e-mail, SMS, push ou WhatsApp. Ces canaux n'ont ni les mêmes fournisseurs, ni les mêmes garanties, ni les mêmes coûts. La démonstration locale ne doit jamais laisser croire qu'un message a réellement été livré.

## Décision

Conserver échéanciers et recouvrement dans le module `billing`, avec les règles suivantes :

- un plan possède une fréquence `MONTHLY`, `QUARTERLY`, `ANNUAL` ou `CUSTOM` et une somme exacte en devise ;
- chaque échéance conserve sa position, sa date, son montant, le montant payé et son état calculé ;
- `billing_schedule_payment_allocation` pointe une écriture de paiement existante sur une date attendue, sans créer de nouvelle valeur monétaire ;
- le dashboard est une projection recalculée à une date `asOf`, et non un compteur mutable séparé du ledger ;
- les règles de rappel conservent décalage en jours, canal, template, mode automatique et activation ;
- une action automatique est unique par règle, échéance et date de déclenchement ;
- `ReminderChannelAdapter` est le port unique vers push, e-mail, SMS ou WhatsApp ;
- le profil local utilise un adaptateur de prévisualisation qui renvoie `PREVIEW` et son identifiant, sans appel réseau ;
- actions manuelles, exécutions automatiques et changements de règle sont audités.

## Conséquences

- une facture, un paiement et un échéancier restent des concepts distincts mais réconciliables ;
- l'annulation d'un paiement restaure aussi les montants payés des échéances par allocations négatives ;
- les indicateurs historiques peuvent être reproduits pour une date de référence explicite ;
- l'ajout futur d'un fournisseur ne modifie ni les règles de recouvrement ni le ledger ;
- les statuts `PREVIEW`, `SENT`, `DELIVERED` ou `FAILED` peuvent être distingués honnêtement ;
- le déclenchement périodique pourra être confié à un scheduler ou une outbox sans rendre l'endpoint actuel non idempotent.
