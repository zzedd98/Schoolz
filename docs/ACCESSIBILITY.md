# Accessibilité M51

SchoolZ vise WCAG 2.2 niveau AA. Le jalon M51 renforce le socle partagé et les quatre parcours de démonstration ; il ne constitue pas une certification. Un audit manuel par des personnes handicapées et un spécialiste indépendant reste nécessaire avant production.

## Socle livré

- langue `fr-MA` / `ar-MA` et direction `ltr` / `rtl` mises à jour sur l’élément `html` ;
- navigation par repères, lien d’évitement et `aria-current` dans le shell ;
- annonce du changement de page SPA, titre document mis à jour et focus déplacé vers le `h1` principal ;
- focus visible bleu foncé avec halo blanc, compatible contrastes clairs/foncés ;
- taille de base 17 px, petit texte au minimum partagé de 14 px et couleurs secondaires renforcées ;
- cibles de formulaire et boutons d’au moins 44 px, y compris à la souris ;
- respect de `prefers-reduced-motion`, `prefers-contrast` et `forced-colors` ;
- boutons de langue avec état `aria-pressed`, icônes décoratives masquées et navigation mobile nommée ;
- pagination réelle de l’annuaire avec libellé de navigation ;
- formulaires M46 structurés par labels, messages `role=alert` / `role=status` et aucune couleur seule pour transmettre un état.

## Recette clavier

Pour chaque profil :

1. recharger la page, appuyer sur `Tab` et utiliser le lien « Aller au contenu » ;
2. parcourir navigation, filtres, actions et formulaires sans souris ;
3. vérifier qu’aucun focus n’est masqué par le header ou un tiroir ;
4. activer les boutons avec `Entrée` et `Espace`, fermer les modales avec leur bouton explicite ;
5. vérifier l’ordre logique en français puis en arabe ;
6. zoomer à 200 % et contrôler l’absence de perte d’information à 320 CSS px.

## Recette lecteur d’écran

- une seule hiérarchie de titre principal compréhensible par vue ;
- régions navigation/main identifiables ;
- nom accessible pour chaque contrôle et état annoncé après une action ;
- tableaux avec en-têtes lorsque la relation ligne/colonne est essentielle ;
- erreurs reliées au champ concerné dans les formulaires critiques ;
- contenu dynamique annoncé sans répétition excessive.

## Contrastes

Les couleurs partagées de texte secondaire ont été assombries pour la cible 4,5:1 sur fonds clairs. Le focus ne dépend pas de la seule couleur. Les écrans comportant des couleurs métier configurables (matières, graphiques, états) doivent conserver texte, motif ou libellé comme second canal et être retestés après chaque personnalisation d’école.

## Limites à traiter avant certification

- audit exhaustif des nombreux tiroirs historiques : piège de focus, retour au déclencheur et touche `Échap` ;
- vérification manuelle de chaque PDF généré et de sa structure de balises ;
- tests NVDA/Firefox, JAWS/Chrome, VoiceOver/Safari et TalkBack ;
- tests de contrastes sur les couleurs personnalisées par établissement ;
- relecture arabe par un locuteur et validation de l’ordre vocal des contenus mixtes ;
- audit automatisé axe/Lighthouse dans la CI de recette, complété par un audit humain.

Les défauts détectés doivent être consignés avec page, profil, technologie d’assistance, impact, sévérité et preuve de correction.
