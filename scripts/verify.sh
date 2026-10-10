#!/usr/bin/env sh
set -eu

project_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

"$project_root/backend/mvnw" -f "$project_root/backend/pom.xml" verify
npm --prefix "$project_root/frontend" run check
docker compose --project-directory "$project_root" --env-file "$project_root/.env.example" config --quiet

echo "SchoolZ verification passed through Milestone 51."
