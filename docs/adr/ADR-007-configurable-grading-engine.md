# ADR-007 — Modèles d’évaluation et moteur de calcul configurables

- Statut : accepté
- Date : 2026-08-08

## Contexte

SchoolZ doit couvrir des pratiques très différentes : aucune note en crèche, observations et compétences en maternelle, modèle hybride au primaire, puis notes, coefficients, examens, moyennes et classement optionnel au collège et au lycée. Une formule unique codée dans l’interface rendrait ces usages incompatibles et empêcherait chaque établissement d’appliquer son règlement.

## Décision

Le module `grading` possède les évaluations, notes, compétences, commentaires, brouillons de bulletins et leur configuration. Une `GradingPolicy` tenant-scoped est attachée à un cycle scolaire ou à la verticale crèche. Elle choisit le mode `NONE`, `COMPETENCY_ONLY`, `HYBRID` ou `NUMERIC`, une échelle, l’activation des bulletins et classements, ainsi qu’un modèle de calcul sérialisé et validé.

Le moteur Java pur reçoit uniquement des éléments normalisés et un modèle immuable. Il supporte au moins la moyenne pondérée et la moyenne pondérée par catégories, avec poids de catégories, gestion des catégories manquantes, suppression éventuelle des plus faibles résultats et précision d’arrondi. Les catégories viennent des `AssessmentType`; le moteur ne connaît ni « examen », ni cycle scolaire, ni écran Angular.

Le service applicatif charge les données tenant-scoped, applique la politique, normalise les scores selon la `GradeScale`, appelle le moteur, puis persiste un instantané explicite dans le brouillon de `ReportCard`. Angular affiche les résultats et envoie des commandes ; aucune formule n’y est reproduite.

Chaque création ou modification de `Grade` ajoute une ligne immuable dans `GradeHistory`, avec valeurs avant/après, motif et acteur. Une note n’est jamais supprimée physiquement. Les PDF et la publication/version finale des bulletins restent au jalon 11.

## Conséquences

- un même tenant peut configurer chaque cycle indépendamment ;
- un élève rattaché à la crèche ne peut recevoir de note numérique, même s’il possède aussi une inscription académique ;
- changer un modèle ne réécrit pas silencieusement les anciens brouillons calculés, car leur configuration est instantanée ;
- ajouter une nouvelle stratégie exige une implémentation moteur et des tests, sans modifier les écrans de saisie ;
- les classements restent désactivés par défaut et ne sont calculés que si la politique l’autorise.
