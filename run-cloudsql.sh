#!/usr/bin/env bash
set -e

PROJECT_ID="${PROJECT_ID:-dukkan-509314}"
REGION="${REGION:-asia-south1}"
SQL_INSTANCE="${SQL_INSTANCE:-dukkan-pg}"
DB_NAME="${DB_NAME:-dukkan}"

INSTANCE_CONN="${PROJECT_ID}:${REGION}:${SQL_INSTANCE}"

echo "=========================================================="
echo " Starting Dukkan API with Google Cloud SQL (${INSTANCE_CONN})"
echo " Single source of truth: PostgreSQL database '${DB_NAME}'"
echo "=========================================================="

if [ -z "$DUKKAN_DB_USER" ] && command -v gcloud >/dev/null 2>&1; then
  export DUKKAN_DB_USER=$(gcloud secrets versions access latest --secret=dukkan-db-user 2>/dev/null || echo "dukkan")
fi

if [ -z "$DUKKAN_DB_PASSWORD" ] && command -v gcloud >/dev/null 2>&1; then
  export DUKKAN_DB_PASSWORD=$(gcloud secrets versions access latest --secret=dukkan-db-password 2>/dev/null || echo "")
fi

if [ -z "$DUKKAN_JWT_SECRET" ] && command -v gcloud >/dev/null 2>&1; then
  export DUKKAN_JWT_SECRET=$(gcloud secrets versions access latest --secret=dukkan-jwt-secret 2>/dev/null || echo "dukkan-dev-secret-change-before-production-32b")
fi

export SPRING_PROFILES_ACTIVE=prod
export DUKKAN_DB_URL="jdbc:postgresql:///dukkan?cloudSqlInstance=${INSTANCE_CONN}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"

mvn spring-boot:run
