# Design system SchoolZ

Ce document décrit le socle UX du jalon 43. Il s’applique au backoffice, au portail professeur, au portail famille et au portail élève. Les écrans métier peuvent conserver une personnalité propre, mais ils partagent les mêmes règles de lisibilité, d’interaction et de navigation.

## Principes

1. **L’action avant la donnée** : une page présente d’abord ce que le profil doit comprendre ou faire maintenant.
2. **Navigation par rôle** : le professeur commence par sa journée ; la famille par ses enfants ; l’élève par ses données publiées ; l’administration conserve une navigation métier structurée.
3. **Une hiérarchie visible** : titre de page, contexte, contenu principal puis actions secondaires. Les métriques ne remplacent pas le contenu.
4. **Des surfaces vivantes** : dégradés bleus, fonds nuancés et rayons asymétriques différencient SchoolZ d’un ancien ERP composé de rectangles blancs identiques.
5. **Lisibilité avant densité** : texte courant à 16 px, libellés utiles à 15 px minimum, interligne de 1,5 et vocabulaire direct.
6. **Clavier, tactile et mouvement maîtrisé** : focus visible, lien d’évitement, cibles tactiles de 44 px et animations supprimables avec `prefers-reduced-motion`.

## Fondations visuelles

- couleur primaire : gamme `--blue-500` à `--blue-900` ;
- fonds : `--gradient-soft`, `--gradient-playful` et surfaces bleu, violet, or ou menthe ;
- texte : `--ink-950` pour les titres, `--ink-700` pour le courant, `--ink-500` au minimum pour les informations secondaires ;
- tailles : `--font-xs`, `--font-sm`, `--font-md`, `--font-lg`, `--font-xl` ;
- formes : `--radius-soft` pour les contrôles et `--radius-panel` pour les grands ensembles ;
- interaction tactile : `--touch-target: 44px`.

Le vert n’est pas une couleur de marque. Il peut uniquement signaler un succès ou un état sain, conformément à sa fonction sémantique.

## Navigation

### Administration

La sidebar est regroupée en sept familles : Essentiel, Pilotage, Scolarité, Relations familles, Services, Gestion et Plateforme. Les entrées sont filtrées par permission côté interface et restent protégées côté serveur. La navigation défile indépendamment du contenu, tandis que l’identité de session reste visible.

### Professeur

« Ma journée » remplace le tableau de bord générique. Sur mobile, une barre fixe donne accès à la journée, aux tâches, à l’agenda et aux alertes. Les actions d’appel, de note et de devoir restent contextualisées par cours.

### Famille et élève

Les portails conservent leur navigation courte, leur sélecteur d’enfant ou leur identité personnelle. Ils n’exposent jamais la sidebar du backoffice. Les liens mènent à de vraies pages et non à des tuiles sans destination.

## Responsive

- administration : desktop-first, sidebar persistante au-dessus de 800 px, tiroir sous ce seuil ;
- professeur et famille : mobile-first, contenus en une colonne et actions prioritaires atteignables au pouce ;
- aucun écran ne doit provoquer de débordement horizontal de page à 320, 768, 1280 ou 1440 px ;
- les rivières de filtres peuvent défiler localement si leur ordre et leur sélection restent compréhensibles.

## Revue d’un nouvel écran

- le titre décrit-il la tâche plutôt que le nom technique du module ?
- le premier écran utile est-il lisible sans ouvrir un tiroir ?
- chaque bouton a-t-il un libellé ou un nom accessible ?
- l’état actif, l’erreur et le succès ne reposent-ils pas uniquement sur la couleur ?
- le zoom à 200 % et le clavier conservent-ils l’ordre de lecture ?
- les données sensibles sont-elles absentes des caches et des journaux frontend ?
