# Stratégie de sécurité

## Authentification et mots de passe

Les comptes appartiennent à un tenant et l'identifiant de connexion est l'e-mail normalisé dans ce tenant. Les mots de passe sont hachés avec BCrypt (coût 12) et doivent contenir au moins 12 caractères, majuscule, minuscule, chiffre et symbole, sans reprendre la partie locale de l'e-mail.

Après cinq échecs, le compte est verrouillé progressivement. Les réponses de connexion et de récupération restent génériques pour ne pas révéler l'existence d'un tenant ou d'un compte. Vérification d'e-mail et reset utilisent des jetons aléatoires à usage unique ; seule leur empreinte HMAC est persistée.

## Sessions

- JWT HMAC-SHA256 court (10 minutes par défaut), signé avec un secret externe ;
- access token conservé uniquement en mémoire par Angular ;
- refresh token opaque de forte entropie, haché en base et transporté en cookie `HttpOnly`, `SameSite=Strict`, `Secure` en production ;
- rotation à chaque refresh, détection de rejeu et révocation de toute la famille compromise ;
- logout et reset de mot de passe révoquent les sessions concernées ;
- compte, tenant, rôles et permissions sont rechargés en base à chaque requête protégée.

L'API bearer est stateless. Le cookie long terme n'est accepté que sur les routes d'authentification ; `SameSite=Strict`, CORS allowlisté et l'absence de mutation métier via ce cookie réduisent le risque CSRF. Aucun token long terme n'est stocké dans `localStorage`.

## Autorisation et isolation tenant

Les 18 rôles de base attribuent des permissions fines telles que `user.read`, `student.write`, `invoice.read/create/adjust`, `payment.record/reverse`, `attendance.read/write/manage`, `discipline.write`, `timetable.read/write/optimize/publish`, `grade.read/write/configure`, `report-card.calculate` ou `homework.read/write/publish`. Les contrôles applicatifs utilisent les permissions, pas seulement les noms de rôles. Toute ressource est chargée à travers une requête incluant le tenant issu du principal ; un identifiant d'un autre tenant n'est jamais accepté depuis le client.

Pour les résultats scolaires, la consultation, la saisie, la configuration des formules et le calcul de bulletins sont quatre responsabilités séparées. L'API refuse une classe, période, matière, enseignant, élève, barème ou compétence d'un autre tenant. Les anciennes valeurs d'une note sont conservées dans un historique append-only et chaque correction requiert un motif ; l'audit global enregistre l'action sans exposer inutilement les commentaires pédagogiques.

Pour le cahier de texte, consultation, rédaction et publication sont séparées. L'API recalcule classe, matière et professeur depuis une affectation tenant-scoped, refuse toute pièce supérieure à 10 Mo ou hors liste MIME et interdit l'ajout après publication. Le portail parent réutilise la session opaque HttpOnly, puis revalide le lien parent-élève pour chaque calendrier, statut et téléchargement ; un identifiant non lié renvoie `404`.

Le portail professeur n'infère jamais un enseignant depuis un nom ou une adresse e-mail. `teacher_portal_profile` relie explicitement le compte authentifié à un `StaffProfile` du même tenant, avec unicité et clés étrangères composites. La lecture exige `teacher-portal.read`, ne retourne que les leçons de ce personnel dans un planning publié et refuse un compte non lié. Les raccourcis sont activés selon les permissions réelles, mais ne contournent jamais l'autorisation du module cible.

Le portail famille réutilise exclusivement la session parent opaque en cookie HttpOnly. L'identifiant d'enfant demandé est intersecté avec `enrollment_portal_student_link`; un enfant non lié produit `404`, y compris pour la préférence et le PDF d'un bulletin. L'agrégation ne retourne que les plannings, évaluations, devoirs et bulletins publiés. La PWA ne met aucune réponse `/api/**` en cache et ne persiste ni jeton ni tableau de bord dans `localStorage`.

Le ledger financier refuse toute suppression de paiement validé. `payment.reverse` est distinct de `payment.record` et produit une écriture compensatoire complète ; factures émises, paiements, allocations, reçus, avoirs, remboursements et ajustements restent traçables. Chaque allocation vérifie le tenant, le compte, le solde du paiement et celui de la facture dans la transaction. Les numéros documentaires sont générés après verrouillage de la séquence du tenant. Le portail financier parent ne reçoit ni `accountId` ni `tenantId` libre : le compte est résolu depuis la session opaque et chaque reçu est revalidé contre ce compte.

Le recouvrement sépare `collections.read` de `collections.manage`. La création d'un échéancier, le changement d'une règle, une relance manuelle ou une préparation automatique revalident tenant, compte et échéance, puis écrivent une trace d'audit. Une exécution automatique est idempotente sur le triplet règle–échéance–date de déclenchement. Les canaux externes passent uniquement par `ReminderChannelAdapter`; l'adaptateur de développement journalise `PREVIEW` et ne simule jamais un e-mail, SMS, push ou message WhatsApp effectivement livré.

Le paiement en ligne sépare `online-payment.read`, `online-payment.manage` et `online-payment.refund`. L'intention est idempotente par commande et le webhook par identifiant d'événement fournisseur. La signature HMAC-SHA256 est comparée en temps constant avant tout effet financier ; un webhook valide doit encore correspondre à la vérification retournée par le fournisseur. Le journal conserve statut, empreinte SHA-256, compteurs de livraison et erreur contrôlée, jamais le corps brut ni un secret. Un remboursement complet produit une écriture compensatoire. Le secret du sandbox vient de l'environnement et sa valeur de `.env.example` est strictement locale.

M18–M23 ajoutent des permissions dédiées `communication.*`, `document.*`, `transport.*`, `cafeteria.*` et `activity.*`. Les documents famille nécessitent un grant et un jeton temporaire haché ; la signature simple fige la version et conserve IP/horodatage sans prétention qualifiée. La position GPS n'est exposée qu'aux familles liées au trajet de leur enfant. Les allergies et consignes d'urgence exigent `cafeteria.health` côté interne. Toute inscription parent revalide le lien parent–enfant côté serveur.

Les routes sont refusées par défaut. Une session absente/invalide produit `401`, une permission manquante `403`. Des tests négatifs couvrent ces décisions et la lecture cross-tenant.

Le module organisations ajoute une défense en profondeur en base : les références organisation, campus, année, niveau, salle et classe utilisent des clés étrangères composites incluant `tenant_id`. Même une erreur applicative ne peut donc pas relier une classe d'un tenant à un campus d'un autre tenant.

## Audit

Les opérations sensibles écrivent dans `identity_audit_log`, sans endpoint de mutation : acteur, tenant, action, ressource, identifiant, timestamp UTC, IP, métadonnées et instantanés avant/après. Mots de passe et tokens ne sont jamais inclus dans l'audit.

## Navigateur et secrets

CORS est limité aux origines configurées. Les alias `localhost` et `127.0.0.1` sur les ports de développement sont autorisés uniquement par la configuration locale de démonstration ; aucune origine générique n'est acceptée. Backend et reverse proxy appliquent `nosniff`, interdiction d'iframe, politique de referrer, permissions policy, isolation COOP/CORP et CSP. Les réponses API portent `Cache-Control: no-store`. HSTS est émis par le backend lorsque la requête est sécurisée et doit aussi être configuré au point de terminaison TLS de production.

`AUTH_JWT_SECRET`, `AUTH_TOKEN_PEPPER` et `SCHOOLZ_PROVISIONING_KEY` sont obligatoires, indépendants et externes à l'application. Les valeurs de `.env.example`, le compte `admin@schoolz.ma` et l'exposition des jetons sont réservés au profil local `dev`. Le profil `prod` force les cookies sécurisés, masque les détails d'erreur, ne seed aucun compte et désactive OpenAPI. Le provisionnement est en plus bloqué au proxy public et n'est invoqué que depuis le conteneur backend.

## MFA/TOTP

Le modèle réserve `mfa_secret_ciphertext` et `mfa_enabled`. L'activation est volontairement différée jusqu'à l'intégration d'un chiffrement de clés géré, de codes de récupération et d'un parcours d'assistance audité. Le jalon 1 satisfait ainsi le niveau « préparé » demandé, sans stocker de secret TOTP en clair.

## Revue avancée M45

La matrice de menaces, les preuves automatisées et les risques résiduels sont consignés dans [SECURITY_REVIEW_M45.md](SECURITY_REVIEW_M45.md). Tous les uploads métier passent maintenant par une vérification commune du nom, de la taille, du MIME, de la signature binaire et par un port anti-malware. L’implémentation locale reconnaît EICAR pour rendre les tests reproductibles ; elle ne prétend pas remplacer un moteur antivirus de production.

Le proxy local limite le login à 10 requêtes par minute et par IP, et l’API à 30 requêtes par seconde. Le compte est également verrouillé progressivement après cinq échecs, ce qui apporte une seconde barrière indépendante de l’IP.

## À compléter avant production

- rate limiting distribué et ciblé aussi sur reset/refresh et les endpoints publics coûteux ;
- fournisseur e-mail transactionnel et gabarits de sécurité ;
- activation MFA/TOTP et récupération ;
- KMS/secret manager, rotation des clés et HSTS au terminateur TLS ;
- test d’intrusion indépendant, DAST et analyse de dépendances continue, en complément de la campagne interne M45 ;
- moteur antivirus de production relié au port `MalwareScanAdapter`, quarantaine et politique de panne fermée ;
- validation juridique documentée au regard de la loi marocaine 09-08 et des exigences CNDP.
