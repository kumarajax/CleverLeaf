#!/usr/bin/env sh
docker compose down --remove-orphans
docker compose up --build
