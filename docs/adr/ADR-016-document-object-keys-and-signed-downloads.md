# ADR-016 — Clés d'objet documentaires et téléchargements signés

Accepté — Milestone 19.

Le DMS sépare métadonnée, version et contenu. Une version possède une clé `tenants/{tenant}/documents/{document}/v{n}`, une empreinte SHA-256 et ne change plus. Le port `DocumentObjectStorage` permet un adaptateur S3 compatible ; la démo JDBC durable est explicitement identifiée comme non production.

Les familles reçoivent un grant explicite. Le téléchargement utilise un jeton haché, valable dix minutes et trois usages, sans URL permanente ni identifiant de tenant fourni par le client.
