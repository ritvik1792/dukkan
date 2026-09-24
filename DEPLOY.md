# Deploy Dukkan API to Google Cloud

This is the production layout. **Do not put user login passwords in Secret Manager or Cloud Run env.** Those passwords are chosen at signup and stored as BCrypt hashes in PostgreSQL (`app_users.password_hash`).

This file does **not** provision a project for you (billing is required). Create the project in the console, then run the commands below.

## What you create in GCP (the 4–5 pieces)

People often say “one for SQL, one for database.” In Google Cloud those are **not** two products:

| What you said | What it is in GCP |
| --- | --- |
| Secret Manager | **Secret Manager** — JWT signing secret, Cloud SQL username, Cloud SQL password, optional API keys. **Never** store a person’s login password here. |
| SQL | **Cloud SQL instance** — the managed PostgreSQL *server* (VM-like). Example name: `dukkan-pg`. |
| Database | **One database on that instance**, named `dukkan`. Tables come from Flyway when the API starts. |
| Backend | **Cloud Run** service `dukkan-api` (this repo). |
| UI | **Cloud Run** service `dukkan-ui` (the `dukkan-ui` repo). Firebase Hosting is optional if you later want a custom domain in front of the UI. |

So: **one Cloud SQL instance + one database `dukkan`**, plus Secret Manager, plus two Cloud Run services.

```
Browser
  → Cloud Run dukkan-ui   (Next.js, NEXT_PUBLIC_API_URL → API)
  → Cloud Run dukkan-api  (Spring Boot, Flyway on boot)
        → Unix socket /cloudsql/PROJECT:REGION:INSTANCE
        → Cloud SQL Postgres instance dukkan-pg
              → database dukkan  (app_users, shops, …)
  Secret Manager → injected as env on dukkan-api (DB user/password, JWT secret)
```

Connection: Cloud Run `--add-cloudsql-instances` mounts a Unix socket. JDBC uses the Cloud SQL socket factory (on the classpath). Private IP is an alternative if you later put Cloud Run on a VPC; the socket path is simpler to start.

## You must create in the console first

1. A **GCP project**
2. **Billing** enabled on that project
3. Pick a **region** (example below: `asia-south1`)

Then install `gcloud` and sign in: `gcloud auth login` and `gcloud auth application-default login`.

## 1. Project and APIs

```bash
export PROJECT_ID=your-project-id
export REGION=asia-south1
export SQL_INSTANCE=dukkan-pg
export DB_NAME=dukkan
export DB_USER=dukkan
export AR_REPO=dukkan

gcloud config set project "$PROJECT_ID"

gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  compute.googleapis.com
```

## 2. Artifact Registry (container images)

```bash
gcloud artifacts repositories create "$AR_REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Dukkan API and UI images"

export API_IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/dukkan-api:latest"
export UI_IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/dukkan-ui:latest"
```

## 3. Cloud SQL instance (the “SQL” piece)

Generate passwords locally (Postgres roles, **not** Dukkan logins). Do not commit them.

```bash
export ROOT_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
export DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
```

```bash
gcloud sql instances create "$SQL_INSTANCE" \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region="$REGION" \
  --storage-size=10 \
  --availability-type=zonal \
  --root-password="$ROOT_PASSWORD"
```

## 4. Database on that instance (the “database” piece)

```bash
gcloud sql databases create "$DB_NAME" --instance="$SQL_INSTANCE"
```

App user (the API uses this, not the `postgres` superuser):

```bash
gcloud sql users create "$DB_USER" \
  --instance="$SQL_INSTANCE" \
  --password="$DB_PASSWORD"
```

If you prefer the built-in `postgres` user, skip `users create` and store that password in Secret Manager as `dukkan-db-user=postgres` instead.

## 5. Secret Manager (not user logins)

```bash
echo -n "$DB_PASSWORD" | gcloud secrets create dukkan-db-password --data-file=-
echo -n "$DB_USER" | gcloud secrets create dukkan-db-user --data-file=-
openssl rand -base64 48 | tr -d '\n' | gcloud secrets create dukkan-jwt-secret --data-file=-
```

Add more secrets later (maps, SMS, …) the same way. **Do not** create secrets for `DUKKAN_SEED_*` or anyone’s account password.

## 6. IAM for the Cloud Run runtime service account

```bash
export PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
export RUN_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

for SECRET in dukkan-db-password dukkan-db-user dukkan-jwt-secret; do
  gcloud secrets add-iam-policy-binding "$SECRET" \
    --member="serviceAccount:${RUN_SA}" \
    --role="roles/secretmanager.secretAccessor"
done

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUN_SA}" \
  --role="roles/cloudsql.client"
```

## 7. Build and push the API image

From this repo (`dukkan`):

```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_IMAGE="$API_IMAGE"
```

## 8. Deploy the API (Cloud Run)

Leave seed off. CORS starts as a placeholder; you will set the real UI URL after step 10.

```bash
export INSTANCE_CONN="${PROJECT_ID}:${REGION}:${SQL_INSTANCE}"

gcloud run deploy dukkan-api \
  --image="$API_IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --cpu=1 \
  --memory=1Gi \
  --add-cloudsql-instances="$INSTANCE_CONN" \
  --set-secrets="DUKKAN_DB_USER=dukkan-db-user:latest,DUKKAN_DB_PASSWORD=dukkan-db-password:latest,DUKKAN_JWT_SECRET=dukkan-jwt-secret:latest" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,DUKKAN_DB_URL=jdbc:postgresql:///dukkan?cloudSqlInstance=${INSTANCE_CONN}&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DUKKAN_SEED_ENABLED=false,DUKKAN_OTP_DEV_CODE=false,DUKKAN_UPLOAD_DIR=/tmp/dukkan-uploads,DUKKAN_CORS_ORIGINS=http://localhost:3000"

export API_URL="$(gcloud run services describe dukkan-api --region="$REGION" --format='value(status.url)')"
echo "$API_URL"
curl -s "${API_URL}/api/health"
```

The API image defaults to Spring profile `prod` (Cloud SQL via `DUKKAN_DB_URL`, not localhost). Local laptops use `mvn spring-boot:run -Dspring-boot.run.profiles=local` — see README.

You want `"status":"ok"` and `"database":"up"`. Flyway has already created tables (and categories / neighborhoods from `V5__reference_data.sql`). There are **no** login rows yet.

`DUKKAN_SEED_ENABLED=false` is a no-op safety flag: this app no longer creates users from env passwords even if someone sets it to true.

## 9. Build and deploy the UI (`dukkan-ui` repo)

`NEXT_PUBLIC_API_URL` is compiled into the Next.js bundle. Build **after** you know `$API_URL`.

```bash
cd /path/to/dukkan-ui

gcloud builds submit --config=cloudbuild.yaml \
  --substitutions=_IMAGE="$UI_IMAGE",_API_URL="$API_URL"

gcloud run deploy dukkan-ui \
  --image="$UI_IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --allow-unauthenticated \
  --port=3000 \
  --cpu=1 \
  --memory=512Mi

export UI_URL="$(gcloud run services describe dukkan-ui --region="$REGION" --format='value(status.url)')"
echo "$UI_URL"
```

Point the API CORS at that UI origin, then redeploy env (secrets stay attached):

```bash
gcloud run services update dukkan-api \
  --region="$REGION" \
  --update-env-vars="DUKKAN_CORS_ORIGINS=${UI_URL}"
```

If you later add a custom domain, put both origins in one value. `gcloud --set-env-vars` splits on commas, so use:

```bash
gcloud run services update dukkan-api --region="$REGION" \
  --update-env-vars="^@^DUKKAN_CORS_ORIGINS=${UI_URL}@https://your.domain"
```

## 10. First real user (after deploy — no env seed)

1. Open `$UI_URL/signup` and create an account with **your** email and password.
2. The API hashes the password into `app_users.password_hash`. That value never belongs in Secret Manager or Cloud Run env.
3. Signup always creates a **BUYER**. To make yourself admin, change the row in Cloud SQL (Cloud SQL Studio in the console, or):

```bash
gcloud sql connect "$SQL_INSTANCE" --user="$DB_USER" --database="$DB_NAME"
```

```sql
UPDATE app_users SET role = 'ADMIN' WHERE email = 'you@yourdomain.com';
```

4. Sign **out**, then sign in at `$UI_URL/console/login`. The JWT is issued at login, so a role change only takes effect on the next sign-in.

Same SQL with `role = 'SELLER'` if you need a shop owner.

## JDBC vs Unix socket

Cloud Run with `--add-cloudsql-instances` mounts `/cloudsql/PROJECT:REGION:INSTANCE`. The JDBC URL used above is:

```text
jdbc:postgresql:///dukkan?cloudSqlInstance=PROJECT:REGION:INSTANCE&socketFactory=com.google.cloud.sql.postgres.SocketFactory
```

That is Unix-socket access through the Cloud SQL Java connector (no public IP required for the API). Equivalent ideas:

- **Private IP**: `jdbc:postgresql://10.x.x.x:5432/dukkan` plus a VPC connector on Cloud Run.
- **Public IP + auth proxy**: only for laptops / Cloud SQL Studio, not for the Cloud Run service.

## What not to set in production

- `DUKKAN_SEED_ADMIN_PASSWORD` / seller / buyer seed passwords — removed from the app.
- `DUKKAN_OTP_DEV_CODE=true` — would return OTP codes in the JSON response.
- A short `DUKKAN_JWT_SECRET` — use the Secret Manager value (32+ characters).

Local disk uploads (`DUKKAN_UPLOAD_DIR`) are **ephemeral** on Cloud Run. The image defaults to `/tmp/dukkan-uploads` because the process runs as a non-root user and cannot create `/app/data`. Add Cloud Storage later if you need durable photos.

## Template YAML

`deploy/cloud-run-api.yaml` is a placeholder (replace `PROJECT_ID` / `REGION` / `SQL_INSTANCE`). The `gcloud run deploy` flags in this file are the supported path for secrets + Cloud SQL.
