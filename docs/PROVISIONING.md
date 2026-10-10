# Provisionnement des écoles et des comptes

SchoolZ utilise un parcours volontairement simple à exploiter pour une première mise en production : un opérateur SchoolZ crée l'école depuis le serveur, puis l'établissement administre lui-même ses utilisateurs depuis l'interface.

## Ce qui est automatisé

Une seule commande crée, dans une transaction :

- l'identifiant isolé de l'établissement ;
- tous les rôles SchoolZ et leurs autorisations ;
- le premier compte propriétaire de l'école ;
- la fiche de l'établissement, ses réglages Maroc et son campus principal ;
- un lien d'activation à usage unique pour que le propriétaire choisisse son mot de passe.

La commande est idempotente : la relancer avec la même clé ne duplique aucune école. Si le premier lien a expiré et que le compte n'est pas encore actif, elle génère un nouveau lien.

## Préparation du serveur

Définir dans `.env.production` :

```dotenv
SCHOOLZ_PROVISIONING_KEY=<secret aléatoire indépendant de 64 caractères minimum>
SCHOOLZ_PUBLIC_BASE_URL=https://votre-domaine.ma
```

Le secret doit être différent de `AUTH_JWT_SECRET` et `AUTH_TOKEN_PEPPER`. Le profil `prod` refuse de démarrer si cette règle n'est pas respectée.

L'URL de provisionnement est bloquée par le proxy public. Le script l'appelle depuis le conteneur backend ; aucun port d'administration supplémentaire n'est ouvert.

## Créer une école

Depuis PowerShell, à la racine du projet déployé :

```powershell
.\scripts\provision-school.ps1 `
  -TenantSlug ecole-atlas `
  -SchoolName "École Atlas" `
  -OwnerName "Nadia El Amrani" `
  -OwnerEmail "direction@atlas.ma" `
  -CampusName "Campus Casablanca" `
  -City Casablanca
```

Le script affiche :

- l'identifiant établissement à utiliser sur `/login` ;
- le compte administrateur ;
- le lien d'activation à transmettre à cette personne.

Le lien expire après la durée `AUTH_EMAIL_VERIFICATION_TTL` (24 heures par défaut). Relancer exactement la même commande produit un nouveau lien tant que le compte n'est pas activé.

## Créer les comptes de l'école

Le propriétaire se connecte puis ouvre **Utilisateurs & accès** :

1. créer ou importer d'abord les dossiers élèves et les fiches du personnel ;
2. créer un compte et lui attribuer ses rôles ;
3. pour un professeur, sélectionner obligatoirement sa fiche personnel ;
4. pour un élève, sélectionner obligatoirement son dossier élève ;
5. copier le lien d'activation généré et le transmettre à l'utilisateur.

Le rattachement explicite empêche un compte professeur ou élève d'accéder à la mauvaise fiche, y compris si deux personnes portent le même nom. Une fiche déjà liée n'est plus proposée.

## Exploitation à plus grande échelle

Ce mécanisme constitue le socle d'une future console commerciale ou d'un raccordement CRM : l'appel est déjà idempotent et tout le traitement métier réside côté serveur. Une interface publique d'inscription ne doit pas appeler directement cet endpoint ; elle devra passer par un service commercial approuvé, avec paiement et validation contractuelle.
