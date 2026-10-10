# ADR-015 — Catégories de communication et adaptateurs de canal

Accepté — Milestone 18.

Annonces, messages directs et notifications automatiques ont des historiques distincts. Toute livraison passe par un adaptateur `IN_APP`, push, e-mail, SMS ou WhatsApp. Le profil local livre uniquement l'in-app et marque les canaux externes `PREVIEW` ou `SKIPPED` ; il ne reconstruit pas WhatsApp et ne prétend pas envoyer réellement.

Les audiences sont développées côté serveur après contrôle du tenant et du rôle : direction vers école/campus, professeur vers ses classes ou familles autorisées, administration vers famille. Les préférences sont propres au destinataire, à la catégorie et au canal.
