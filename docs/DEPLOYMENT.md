# Déploiement de SchoolZ 1.0

SchoolZ fournit deux compositions distinctes :

- `compose.yaml` est la démonstration locale avec données et comptes de test ;
- `compose.prod.yaml` construit le frontend sans identifiants de démonstration, active le profil Spring `prod` et exige des secrets externes.

La composition de production est une base de déploiement mono-hôte. Une mise en ligne publique exige en plus un domaine, une terminaison TLS, une politique de sauvegarde et les services externes choisis par l'établissement.

## Préparer l'environnement

1. Copier `.env.production.example` vers `.env.production`.
2. Remplacer **toutes** les valeurs `CHANGE_ME` par des valeurs aléatoires stockées dans un gestionnaire de secrets.
3. Utiliser trois secrets indépendants d'au moins 64 caractères pour `AUTH_JWT_SECRET`, `AUTH_TOKEN_PEPPER` et `SCHOOLZ_PROVISIONING_KEY`.
4. Définir `SCHOOLZ_PUBLIC_BASE_URL` avec l'URL HTTPS finale ; elle sert à générer les invitations d'activation.
4. Renseigner l'URL HTTPS publique dans `ALLOWED_ORIGINS` et `AUTH_JWT_ISSUER`.
5. Créer au préalable un bucket S3 privé et renseigner son endpoint et ses identifiants. SchoolZ ne crée pas le bucket en production.
6. Placer le port HTTP interne derrière un load balancer ou reverse proxy TLS. Ne pas publier directement ce port sur Internet.

Le validateur de démarrage refuse notamment les secrets exemples, les origines HTTP, les cookies non sécurisés et les données de démonstration sous le profil `prod`.

## Valider et démarrer

```powershell
docker compose -f compose.prod.yaml --env-file .env.production config --quiet
docker compose -f compose.prod.yaml --env-file .env.production build
docker compose -f compose.prod.yaml --env-file .env.production up -d
docker compose -f compose.prod.yaml --env-file .env.production ps
```

Une fois la plateforme démarrée, créer la première école avec `scripts/provision-school.ps1`. Le parcours complet est décrit dans [PROVISIONING.md](PROVISIONING.md).

Le reverse proxy public ne publie ni Actuator, ni Swagger/OpenAPI. L'état du backend reste utilisé uniquement par le healthcheck interne Docker.

## Sauvegarde et restauration

- sauvegarder PostgreSQL et tester régulièrement une restauration isolée ;
- activer le versionnement, le chiffrement et la rétention sur le bucket S3 ;
- sauvegarder séparément les paramètres du gestionnaire de secrets ;
- considérer Redis comme une donnée opérationnelle reconstructible ;
- définir et tester les objectifs RPO/RTO avant ouverture commerciale.

Le script `scripts/restore-test.ps1` permet de rejouer le parcours local de restauration. Il ne remplace pas le test du service PostgreSQL managé retenu en production.

## Identifiant d'établissement mémorisé

Après une connexion réussie, le navigateur conserve uniquement le slug de l'établissement dans `localStorage` sous la clé `schoolz.lastTenant`. La prochaine visite sur `/login` préremplit ce champ. Aucun e-mail, mot de passe, jeton ou cookie d'authentification n'est enregistré par ce mécanisme. Si le stockage local est bloqué ou vidé, la connexion reste fonctionnelle et le champ redevient vide.

## Contrôles avant ouverture publique

- réaliser une revue de sécurité externe et un test d'intrusion ;
- valider les obligations CNDP, la localisation et les durées de conservation des données ;
- configurer SMTP/SMS/WhatsApp et un prestataire de paiement réel si ces canaux sont commercialisés ;
- configurer alertes, métriques, centralisation des logs et rotation des secrets ;
- faire un essai de charge sur l'infrastructure cible ;
- tester les sauvegardes, la restauration, le renouvellement TLS et le plan d'incident.

Sans ces décisions d'exploitation, le dépôt est une **release candidate déployable**, pas encore une production publique certifiée.
