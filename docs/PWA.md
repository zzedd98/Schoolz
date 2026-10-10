# PWA SchoolZ

Le jalon 44 rend les espaces **Famille** et **Professeur** installables comme applications web progressives. Les deux espaces utilisent le même frontend signé et le même service worker, mais chacun active un manifeste dédié avec son propre nom, sa route de démarrage et ses raccourcis.

## Ce qui est disponible

- manifeste Famille : `/manifest-family.webmanifest`, démarrage sur `/family` ;
- manifeste Professeur : `/manifest-teacher.webmanifest`, démarrage sur `/app/teacher` ;
- bouton d’installation dans chacun des deux portails et aide lorsque le navigateur ne fournit pas l’invite native ;
- service worker Angular enregistré uniquement sur un build de production ;
- shell applicatif, fichiers JavaScript/CSS, favicon et icônes mis en cache ;
- navigation applicative disponible pour afficher le shell en cas de réseau instable.

## Politique de cache

`ngsw-config.json` ne définit volontairement **aucun `dataGroup`**. Les routes `/api/**`, Actuator et Swagger sont exclues de la navigation PWA, et aucune réponse contenant notes, absences, données familiales, finance, santé, identité ou jeton n’est mise en cache par le service worker.

Le jeton d’accès du backoffice reste uniquement en mémoire. Les cookies de session portail sont `HttpOnly` et ne sont pas lisibles par le JavaScript de l’application.

## Installation

1. servir le build de production en HTTPS ou sur `localhost` ;
2. ouvrir `/family` ou `/app/teacher` ;
3. utiliser le bouton « Installer l’application » ou « Installer mon espace » ;
4. si l’invite n’est pas disponible, choisir l’action d’installation dans le menu du navigateur.

## Notifications push

L’infrastructure locale ne fournit pas encore de serveur VAPID ni de registre de souscriptions Web Push. SchoolZ n’invente donc aucune souscription distante au jalon 44. Le centre de notifications M40 et les canaux de prévisualisation restent disponibles. L’activation future devra ajouter consentement explicite, révocation, expiration et rotation de clés avant toute livraison push réelle.
