# ADR-009 — Un cahier de texte ciblé, pas un LMS

## Statut

Accepté pour le jalon 12.

## Décision

Le cahier de texte repose sur quatre concepts tenant-scoped : `LessonContent`, `Homework`, `Attachment` et un statut élève `TO_DO`/`DONE`. Le contenu de cours et le devoir réutilisent une affectation professeur–matière–classe du planning ainsi qu'une période académique existante.

Un devoir peut référencer un contenu de cours, mais les deux restent publiables séparément. Un brouillon et ses fichiers restent modifiables ; la publication les fige. La clôture d'un devoir ne modifie que son état opérationnel. Le parent voit uniquement les éléments publiés de ses enfants liés et peut marquer un devoir comme fait.

Les pièces sont limitées à 10 Mo et à une liste de types MIME explicite. Pour cette verticale locale, le binaire et son empreinte SHA-256 sont conservés en base. Le futur module documentaire pourra externaliser le binaire dans un stockage objet sans modifier l'identité ni les contrôles d'accès.

`HomeworkSubmission` n'est pas créé au jalon 12. Le dépôt de réponses, la notation en ligne, les quiz, forums, parcours, commentaires et corrections constitueraient un LMS. Ils seront évalués séparément seulement si un besoin produit explicite apparaît.

## Conséquences

- une affectation existante empêche d'inventer un professeur, une matière ou une classe incohérents ;
- la consultation famille reste un calendrier de travail simple, multi-enfants et en lecture contrôlée ;
- le statut `DONE` est un suivi d'organisation, pas une preuve de remise ni une note ;
- les publications et téléchargements sont audités et isolés par tenant ;
- le périmètre reste compatible avec le futur portail professeur sans le construire prématurément.
