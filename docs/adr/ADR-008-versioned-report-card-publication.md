# ADR-008 — Publication immuable des bulletins

## Statut

Accepté — jalon 11.

## Contexte

SchoolZ doit produire des bulletins PDF en français, en arabe et en version bilingue. Un
bulletin transmis à une famille constitue un document scolaire : son contenu ne doit pas
changer silencieusement après publication, même lorsqu'une note, une absence ou un commentaire
est corrigé.

## Décision

Le calcul et la publication restent deux opérations distinctes.

- Un calcul produit un bulletin au statut `CALCULATED` et ses résultats par matière.
- Une prévisualisation génère un PDF à la volée avec un filigrane, sans persistance.
- Une publication fige le PDF, sa langue, son modèle, son signataire, son instantané JSON, sa
  taille et son empreinte SHA-256 dans la même transaction.
- Le PDF publié est immuable. Si les données pédagogiques évoluent, le prochain calcul crée une
  nouvelle ligne de version `n + 1` liée à la précédente par `supersedes_report_card_id`.
- Les versions antérieures restent téléchargeables et l'action de publication est inscrite dans
  le journal d'audit tenant-aware.

Les documents sont générés avec PDFBox. ICU4J assure le façonnage et l'ordre visuel des textes
arabes. Les polices Noto sont installées dans l'image Docker et une résolution multi-plateforme
permet les tests locaux Windows et Linux.

## Conséquences

- La preuve documentaire est autonome et vérifiable par empreinte.
- Une correction après publication est explicite et traçable dans la chaîne des versions.
- Le stockage en base est adapté au monolithe et à la démonstration. Une migration ultérieure
  pourra déplacer les octets vers un stockage objet sans modifier le contrat d'API ni le modèle
  de versionnement.
