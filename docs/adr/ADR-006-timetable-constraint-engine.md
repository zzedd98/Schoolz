# ADR-006 — Séparer le moteur d'emploi du temps de l'interface

## Statut

Accepté pour le jalon 9.

## Contexte

SchoolZ doit permettre de construire un emploi du temps hebdomadaire à la main, de déplacer un cours par glisser-déposer et de proposer une première optimisation automatique. Les mêmes invariants doivent s'appliquer aux trois parcours sans être recopiés dans Angular : indisponibilité, collision d'un enseignant, d'une salle ou d'une classe, capacité, équipements, maximum journalier, volume hebdomadaire et enchaînement de cours.

## Décision

Le module `timetable` possède le modèle persistant et expose deux ports Java purs :

- un détecteur reçoit un instantané immuable du planning et un placement candidat, puis produit une liste de conflits structurés `HARD` ou `SOFT` ;
- un optimiseur reçoit le même instantané, les besoins hebdomadaires et les contraintes, puis propose des placements sans écrire en base.

Le service applicatif charge les données tenant-scoped, appelle ces ports, décide si une mutation est autorisée et persiste dans une transaction. L'interface ne connaît aucun invariant métier : elle demande une prévisualisation au serveur avant le dépôt, affiche les conflits, puis appelle la mutation. L'optimiseur privilégie d'abord la faisabilité des contraintes dures, puis minimise le coût des préférences souples ; son résultat reste une proposition explicite que l'utilisateur applique.

Les conflits d'occupation enseignant, salle et classe, les indisponibilités, la capacité et les équipements obligatoires sont durs. Les préférences horaires et l'évitement de créneaux sont souples. Les limites journalières et consécutives sont configurables en dur ou en souple. Le volume d'une affectation est exprimé en nombre de créneaux par semaine.

## Conséquences

- création, déplacement et optimisation emploient une seule définition des règles ;
- le moteur est testable sans HTTP, Angular ni base de données ;
- une future stratégie plus avancée pourra remplacer l'heuristique sans modifier l'API ou l'interface ;
- aucune optimisation ne publie ni ne remplace silencieusement un planning existant ;
- la première stratégie privilégie une réponse déterministe et explicable, sans promettre un optimum mathématique global.
