# ADR-010 — Identité et agrégation du portail professeur

- Statut : accepté
- Date : 2026-08-09

## Contexte

Le portail professeur doit présenter une journée personnelle et ouvrir les écrans d'appel, de notes et de devoirs avec le bon contexte. Le compte d'identité et le personnel académique sont toutefois deux concepts distincts. Une correspondance implicite par nom, adresse e-mail ou matricule serait ambiguë, fragile et vulnérable aux erreurs de tenant.

Dupliquer les cours, appels ou devoirs dans un domaine « portail » créerait également une seconde source de vérité et des divergences de statut.

## Décision

Créer `teacher_portal_profile`, une relation explicite et tenant-scoped entre `identity_user_account` et `staff_profile`, unique dans les deux directions et désactivable.

Le module `teacherportal` reste une façade de lecture. Il :

- résout le compte authentifié vers son personnel sans accepter d'identifiant professeur du client ;
- lit uniquement les leçons de plannings publiés appartenant à ce personnel ;
- calcule chronologie, compteurs, prochaine journée et leçon de contexte ;
- expose des raccourcis uniquement si la permission correspondante existe ;
- transmet des paramètres de contexte aux routes existantes, sans effectuer leurs mutations.

`attendance`, `grading` et `homework` restent propriétaires de leurs écritures et revalident l'autorisation. Le message est volontairement indisponible avant le module communications.

## Conséquences

- aucune usurpation d'un enseignant par paramètre d'URL ;
- aucune copie de données pédagogiques ni synchronisation supplémentaire ;
- un compte professeur doit être explicitement lié avant d'utiliser le portail ;
- les raccourcis restent des accélérateurs d'interface, jamais des capacités supplémentaires ;
- une future délégation ou suppléance devra être modélisée explicitement au lieu de modifier cette relation silencieusement.
