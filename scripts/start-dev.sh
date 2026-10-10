#!/usr/bin/env sh
set -eu

project_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

if [ ! -f "$project_root/.env" ]; then
  cp "$project_root/.env.example" "$project_root/.env"
  echo "Created .env from the development template."
fi

docker compose --project-directory "$project_root" --env-file "$project_root/.env" up --build

