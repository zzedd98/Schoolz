# Protection des données, loi 09-08 et CNDP

SchoolZ fournit au jalon M46 des mécanismes techniques de gouvernance. Leur présence ne suffit jamais à déclarer un établissement ou le produit juridiquement conforme. Une validation par un conseil qualifié et, lorsque nécessaire, les formalités auprès de la CNDP restent obligatoires avant production.

## Capacités techniques disponibles

- registre des finalités par établissement avec base à valider, catégories, destinataires, sensibilité et durée ;
- journal append-only des consentements accordés ou retirés, avec source et référence de preuve ;
- demandes d’accès, export, rectification, effacement, limitation ou opposition, avec échéance, vérification humaine et action de résolution ;
- politiques de rétention configurables avec action `ANONYMIZE`, `DELETE` ou `REVIEW` et gel juridique ;
- traçabilité de chaque préparation d’export, de son périmètre, de son expiration et de son empreinte lorsque l’artefact est généré ;
- fiche d’hébergement par tenant : pays, région, fournisseur, chiffrement déclaré, transfert transfrontalier et statut de validation juridique ;
- permissions distinctes `privacy.read`, `privacy.manage` et `privacy.export`, isolation tenant et audit sans inclure la donnée personnelle brute dans les métadonnées.

La résolution `ANONYMIZED` ou `DELETED` atteste une décision et son exécution contrôlée ; elle ne lance pas une purge générique et irréversible dans tous les modules. Chaque famille de données devra être reliée à un adaptateur d’anonymisation validé, tenant compte des obligations comptables, scolaires, de santé et des gels juridiques.

## Décisions à faire valider

Pour chaque établissement, documenter et dater au minimum :

1. identité du responsable de traitement, sous-traitants et responsabilités contractuelles ;
2. finalités, bases applicables, caractère obligatoire ou facultatif et information des personnes ;
3. consentements réellement requis, capacité des mineurs et modalités de retrait ;
4. catégories sensibles, personnes habilitées et mesures renforcées ;
5. durées par catégorie et événements déclenchant archivage, anonymisation ou suppression ;
6. hébergement, sauvegardes, accès administrateur et tout transfert hors Maroc ;
7. formalités CNDP applicables, autorisations éventuelles et preuves à conserver ;
8. procédure d’exercice des droits, vérification d’identité, délais légaux et cas de refus ;
9. réponse aux violations, notification, communication et journal d’incident ;
10. politique de chiffrement, gestion des clés, suppression des sauvegardes et fin de contrat.

## Configuration de démonstration

La démonstration déclare `MA`, « Docker local », chiffrement au repos déclaré et statut juridique `PENDING`. Cette fiche décrit seulement l’environnement local ; elle n’atteste aucun hébergement réel ni aucune autorisation CNDP.

## Avant production

Le registre doit être revu avec le délégué ou référent désigné, les finalités de démonstration remplacées par celles de l’établissement, l’hébergeur et les sous-traitants contractualisés, les durées approuvées, puis les preuves juridiques référencées. Le statut `APPROVED` dans SchoolZ signifie uniquement qu’une validation externe a été enregistrée ; il ne constitue pas une certification émise par SchoolZ.
