# Commandes pour lancer SchoolZ

Toutes les commandes ci-dessous sont prévues pour **PowerShell sous Windows** et doivent être lancées depuis :

```powershell
Set-Location C:\Users\User\Desktop\SchoolZ
```

## 1. Prérequis

Installer et démarrer **Docker Desktop**. Vérifier ensuite :

```powershell
docker --version
docker compose version
docker info
```

Le chemin Docker est recommandé : PostgreSQL, Redis, MinIO, le backend et le frontend sont alors démarrés automatiquement, sans installation locale de base de données.

## 2. Lancer la démonstration complète

Première exécution ou reconstruction après une modification :

```powershell
docker compose --env-file .env.example up -d --build
```

Ouvrir ensuite :

```text
http://localhost:8088/login
```

Vérifier l'état des services :

```powershell
docker compose --env-file .env.example ps
```

Suivre les journaux si un service ne démarre pas :

```powershell
docker compose --env-file .env.example logs -f --tail 200
```

Quitter les journaux avec `Ctrl+C` ne coupe pas l'application.

### Comptes de démonstration

L'identifiant de l'établissement est toujours `ecole-demo`.

| Profil | Adresse e-mail | Mot de passe |
|---|---|---|
| Administrateur | `admin@schoolz.ma` | `SchoolZDemo2026!` |
| Professeur | `prof.math@schoolz.ma` | `SchoolZTeacher2026!` |
| Élève | `yasmine.eleve@schoolz.ma` | `SchoolZStudent2026!` |
| Parent | `parent.demo@schoolz.ma` | `SchoolZParent2026!` |
| Infirmière | `infirmiere.demo@schoolz.ma` | `SchoolZNurse2026!` |

## 3. Arrêter ou redémarrer

Arrêter l'application en conservant les données :

```powershell
docker compose --env-file .env.example down
```

Redémarrer sans reconstruire les images :

```powershell
docker compose --env-file .env.example up -d
```

Redémarrer un service particulier :

```powershell
docker compose --env-file .env.example restart backend
docker compose --env-file .env.example restart frontend
```

Repartir de zéro, y compris les données locales de démonstration :

```powershell
docker compose --env-file .env.example down --volumes
docker compose --env-file .env.example up -d --build
```

La commande `down --volumes` efface les bases et fichiers locaux Docker de SchoolZ. Ne pas l'utiliser sur une installation contenant de vraies données.

## 4. Prévisualiser le frontend livrable

Cette commande construit le frontend de production, sans les encarts ni boutons de développement, tout en conservant les comptes locaux de démonstration :

```powershell
.\scripts\start-production-preview.ps1 -Port 8089
```

Ouvrir :

```text
http://localhost:8089/login
```

Les identifiants de démonstration restent les mêmes, mais doivent être saisis manuellement.

## 5. Tester le projet

Vérification complète automatisée :

```powershell
.\scripts\verify.ps1
```

Tests backend uniquement :

```powershell
Set-Location .\backend
.\mvnw.cmd verify
Set-Location ..
```

Tests et build frontend uniquement :

```powershell
Set-Location .\frontend
npm ci
npm run check
Set-Location ..
```

Parcours fonctionnels critiques sur la démonstration déjà démarrée :

```powershell
.\scripts\e2e-smoke.ps1
```

## 6. Développement frontend hors Docker

Il faut Node.js 24 et npm 11. Installer les dépendances puis lancer Angular :

```powershell
Set-Location .\frontend
npm ci
npm start
```

Le frontend de développement est disponible sur :

```text
http://localhost:4200
```

Pour ce mode, garder au minimum le backend et son infrastructure Docker démarrés dans un autre terminal :

```powershell
Set-Location C:\Users\User\Desktop\SchoolZ
docker compose --env-file .env.example up -d postgres redis minio backend
```

## 7. Déploiement réel

Créer d'abord votre configuration privée :

```powershell
Copy-Item .env.production.example .env.production
notepad .env.production
```

Remplacer toutes les valeurs `CHANGE_ME`, configurer le domaine HTTPS et le stockage S3, puis valider et démarrer :

```powershell
docker compose -f compose.prod.yaml --env-file .env.production config --quiet
docker compose -f compose.prod.yaml --env-file .env.production build
docker compose -f compose.prod.yaml --env-file .env.production up -d
docker compose -f compose.prod.yaml --env-file .env.production ps
```

La vraie configuration `prod` exige un domaine HTTPS, des secrets robustes et un stockage S3 privé. Pour un simple test local visuel, utiliser la prévisualisation du point 4.

Créer ensuite la première école :

```powershell
.\scripts\provision-school.ps1 `
  -TenantSlug ecole-atlas `
  -SchoolName "École Atlas" `
  -OwnerName "Nadia El Amrani" `
  -OwnerEmail "direction@atlas.ma" `
  -CampusName "Campus principal" `
  -City Casablanca
```

Le script affiche l'identifiant de l'établissement et le lien d'activation du premier administrateur.

## 8. Diagnostic rapide

État du backend via le site :

```text
http://localhost:8088/api/v1/system/status
```

Santé interne du backend local :

```text
http://localhost:18080/actuator/health
```

Afficher uniquement les journaux backend :

```powershell
docker compose --env-file .env.example logs -f --tail 200 backend
```

Reconstruire uniquement le frontend et le backend :

```powershell
docker compose --env-file .env.example up -d --build frontend backend reverse-proxy
```
