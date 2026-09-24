#!/usr/bin/env bash
set -e

# Prints the official live dukkan-api URL deployed on Google Cloud Run
REGION="${REGION:-asia-south1}"
SERVICE_NAME="dukkan-api"
FALLBACK_URL="https://dukkan-api-189515841875.asia-south1.run.app"

if command -v gcloud >/dev/null 2>&1; then
  LIVE_URL=$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)' 2>/dev/null || true)
fi

if [ -n "$LIVE_URL" ]; then
  echo "$LIVE_URL"
else
  echo "$FALLBACK_URL"
fi
