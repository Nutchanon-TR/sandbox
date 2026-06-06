# JOBJAB - Current Code Spec

This document summarizes the current JOBJAB implementation from the source tree. JOBJAB is a job discovery module that collects jobs from adapter-based sources, matches them against a user's profile, calculates commute routes only on demand, and tracks application progress.

---

## Overview

JOBJAB is implemented as a separate Spring Boot backend service plus a route-based module inside the existing Next.js frontend.

Main path:

```text
Frontend -> Nginx gateway -> /v1/api/jobjab/* -> jobjab-service
```

Auth:

- Gateway protects `/v1/api/jobjab/*` with `auth_request /oauth2/auth`.
- `JwtAuthFilter` from `common-auth` runs on `/v1/api/jobjab/*`.
- Controllers resolve the internal user id through `CurrentUser.requireUserId()`.
- User-owned database rows reference `chat_app.users(id)`.

---

## Tech Stack

| Area | Technology |
|---|---|
| Backend | Spring Boot 3.2.5 |
| Java compile | Java 17 |
| Runtime image | Eclipse Temurin JRE |
| Database | Supabase PostgreSQL |
| ORM/migration | Spring Data JPA + Flyway |
| Auth helper | `common-auth` |
| AI matching | Groq-compatible OpenAI endpoint, disabled by default |
| Routing | Google Routes API, called only on route button click |
| Frontend | Next.js App Router, Ant Design, Tailwind |

---

## Environment Variables

JOBJAB has cost-generating integrations, so local and deploy defaults are intentionally off.

| Variable | Default | Purpose |
|---|---:|---|
| `JOBJAB_AI_MATCHING_ENABLED` | `false` | Enables paid/external AI matching calls. |
| `JOBJAB_AI_AUTO_ANALYZE_LIMIT` | `0` | Caps auto analysis during search runs. `0` means no automatic paid analysis. |
| `JOBJAB_WEEKLY_DIGEST_ENABLED` | `false` | Enables weekly digest scheduler. |
| `JOBJAB_WEEKLY_DIGEST_CRON` | `0 0 8 ? * MON` | Weekly digest schedule, Asia/Bangkok in code. |
| `JOBJAB_SEARCH_ACTIVE_RUN_TTL_MINUTES` | `30` | Marks stale active search runs failed before creating new work. `0` disables the stale guard. |
| `JOBJAB_SEARCH_CORE_POOL_SIZE` | `2` | Core threads for on-demand search workers. |
| `JOBJAB_SEARCH_MAX_POOL_SIZE` | `4` | Maximum on-demand search worker threads. |
| `JOBJAB_SEARCH_QUEUE_CAPACITY` | `100` | Queue size before caller-side backpressure. |
| `GOOGLE_MAPS_API_KEY` | empty | Enables Google route calculation. Empty key uses cached/fallback estimates only. |
| `BACKEND_JOBJAB_URL` | `http://localhost:8083` | Frontend local rewrite destination. |

Local backend port:

```text
http://localhost:8083
```

---

## Database Schema

Migration file:

```text
backend/jobjab/src/main/resources/db/migration/V1__jobjab_schema.sql
```

Schema:

```text
jobjab
```

Tables:

| Table | Purpose |
|---|---|
| `jobjab.user_job_profiles` | User job preferences, skills, desired titles, home location, commute settings. |
| `jobjab.job_sources` | Adapter source registry and governance flags. |
| `jobjab.search_runs` | On-demand or scheduled search run metadata. |
| `jobjab.search_run_events` | Ordered live log events for a search run. |
| `jobjab.raw_job_snapshots` | Raw source payload/text snapshots before normalization. |
| `jobjab.jobs` | Normalized job records with source dedupe. |
| `jobjab.job_matches` | Per-user AI/heuristic match analysis. |
| `jobjab.job_tracking` | Per-user Kanban tracking status. |
| `jobjab.route_cache` | Per-user/job commute calculation cache. |
| `jobjab.weekly_digests` | Weekly digest headers. |
| `jobjab.weekly_digest_items` | Ranked jobs in each digest. |

Important constraints:

- `jobs` dedupes by `(source_id, source_job_key)`.
- `raw_job_snapshots` dedupes by `(source_id, source_job_key, content_hash)`.
- `job_matches`, `job_tracking`, and `weekly_digests` are unique per user scope.
- User-owned tables reference `chat_app.users(id)`, not `auth.users`.

Supabase exposure model:

- Current design is backend-owned. The frontend does not query the `jobjab` schema directly.
- RLS is enabled on every JOBJAB table as defense in depth.
- RLS policies are not defined yet because direct Supabase Data API access is not part of v1.
- If the `jobjab` schema is later added to Supabase Exposed Schemas, add explicit grants and table-specific RLS policies before exposing user data.

---

## Source Adapters

JOBJAB uses adapter/plugin style ingestion. Each adapter implements:

```text
sourceKey
fetchMode
search(criteria)
fetchDetail(ref)
normalize(rawSnapshot)
```

Current source registry:

| Source key | Status | Fetch mode | Notes |
|---|---|---|---|
| `manual_jd` | enabled | `MANUAL` | User pastes a job description. |
| `user_url` | enabled | `USER_URL` | User provides a job URL. |
| `greenhouse` | enabled | `OFFICIAL_API` | Uses Greenhouse board-style public JSON endpoints when configured. |
| `lever` | enabled | `OFFICIAL_API` | Uses Lever public postings endpoints when configured. |
| `ashby` | enabled | `OFFICIAL_API` | Uses Ashby board-style endpoints when configured. |
| `structured_data` | enabled | `STRUCTURED_DATA` | Extracts schema.org JobPosting-style pages from allowed URLs. |
| `sitemap` | enabled | `SITEMAP` | Reads configured sitemap URLs and job-like links. |
| `jobsdb` | disabled | `PARTNER_API` | Requires partner/API approval before enabling. |
| `linkedin` | disabled | `PARTNER_API` | Requires partner/API approval before enabling. |
| `jobthai` | disabled | `HTML` | Future adapter; browser scraping is not enabled in v1. |

Source governance:

- Disabled sources are skipped by the worker.
- Partner-required sources are skipped until explicitly approved and configured.
- Browser scraping is avoided in v1.
- URL-based adapters canonicalize URLs and strip common tracking params before upsert.
- When a user pastes a Greenhouse, Lever, or Ashby job URL, JOBJAB can infer the board/company target for that official API adapter and pass it into the search run automatically.
- If a search request omits `sourceKeys`, JOBJAB first derives sources from manual jobs, pasted URLs, and source targets. Query-only searches still fall back to the broader source set so database-configured official sources can run.

---

## API Surface

Base path:

```text
/v1/api/jobjab
```

Main areas:

| Area | Purpose |
|---|---|
| `/profile` | Create/update user job profile and preferences. |
| `/sources` | List source registry and source readiness. |
| `/search-runs` | Start on-demand search and poll run status/events. |
| `/jobs` | List matched jobs and fetch job detail. |
| `/jobs/{jobId}/analyze` | Analyze one job against the current user's profile. `/jobs/{jobId}/match` remains supported as an alias. |
| `/jobs/{jobId}/route` | Calculate or read cached commute route. |
| `/tracking` | Create/update Kanban tracking state. |
| `/digests` | List in-app weekly digests. |
| `/digests/weekly` | Generate the current week's in-app digest. |

---

## Weekly Digest Matching

- Weekly digest ranks up to 10 jobs by match score, commute cache, and analysis recency.
- If the user has fewer than 10 analyzed jobs, digest generation backfills from recent visible jobs using heuristic-only matching.
- Heuristic backfill does not call the external AI client, even when paid AI matching is enabled.
- Jobs already moved to `APPLIED`, `INTERVIEW`, `OFFER`, `REJECTED`, or `ARCHIVED` are excluded.
- When cached commute data exists, jobs over the user's max commute are excluded.

---

## Nearby Recommendations

- `/jobjab/jobs?nearbyFirst=true` sorts by cached route duration when route cache exists.
- If no route cache exists yet, JOBJAB can sort by in-memory coordinate estimates when the profile home and job locations have latitude/longitude.
- Coordinate estimates use haversine distance and do not call Google Maps. Google route calls still only happen from the job detail route action.

---

## On-Demand Search Runtime

- Search workers run through a bounded `jobjabSearchExecutor`.
- If a user already has a `QUEUED` or `RUNNING` search, a new start request returns the active run instead of creating duplicate work.
- If that active run is older than `JOBJAB_SEARCH_ACTIVE_RUN_TTL_MINUTES`, it is marked `FAILED` and a new search can start.
- Frontend polling resumes against that active run and keeps reading `search_run_events` for the live log.

---

## Frontend Integration

Routes:

| Path | Purpose |
|---|---|
| `/jobjab/search` | On-demand search, result limit, source picker, live log. |
| `/jobjab/jobs` | Matched job list with nearby filter. |
| `/jobjab/jobs/[jobId]` | Job detail, AI analysis, route button. |
| `/jobjab/tracking` | Minimal Kanban board. |
| `/jobjab/profile` | Job profile/preferences. |
| `/jobjab/digest` | Weekly digest list/generate. |

Frontend constants:

- `TITLE.JOBJAB = "JOBJAB"`
- API prefix is `/v1/api/jobjab`.
- Local Next.js rewrite uses `BACKEND_JOBJAB_URL`.

Kanban states:

```text
INTERESTED -> APPLIED -> INTERVIEW -> OFFER -> REJECTED -> ARCHIVED
```

---

## Runtime Verification Checklist

Backend unit tests:

```powershell
& 'C:\Users\USER HP\.m2\wrapper\dists\apache-maven-3.9.10\a38810a491b03367137adfdfbe7d14c4\bin\mvn.cmd' -q -f backend/jobjab/pom.xml test
```

Frontend build:

```powershell
cd frontend
npm run build
```

Compose validation:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml config --quiet
```

Runtime smoke test, once Docker Desktop is running:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

Manual checks after startup:

- Gateway loads the frontend at `http://localhost:8088`.
- Sidebar shows `JOBJAB`.
- `/jobjab/search` can start a search and shows live events.
- Disabled/partner sources cannot be selected or executed.
- `/jobjab/jobs/[jobId]` only calls Google Maps when the route button is pressed.
- `/jobjab/tracking` updates one row per `(user_id, job_id)`.
- User A cannot read or update User B's profile, matches, tracking, route cache, or digest data through backend APIs.

Supabase/live database verification, when `psql`, Supabase CLI, or MCP access is available:

- Flyway applies `V1__jobjab_schema.sql` successfully.
- `select schema_name from information_schema.schemata where schema_name = 'jobjab';`
- `select tablename, rowsecurity from pg_tables where schemaname = 'jobjab';`
- Duplicate `(source_id, source_job_key)` cannot create duplicate normalized jobs.
- `jobjab` is not added to Exposed Schemas unless grants and RLS policies are intentionally added.

---

## Logging Model

JOBJAB does not create runtime log files in v1. Search progress for the frontend live log is stored in:

```text
jobjab.search_run_events
```

If a future adapter or scheduler writes file logs, the file name must follow the platform date-format convention required by the system before that adapter is enabled.

---

## Current Known Gaps

| Item | Status |
|---|---|
| Docker/browser runtime proof | Pending; Docker Desktop engine is not currently reachable on this machine. |
| Live Supabase migration proof | Pending; no `psql`, Supabase CLI, or authenticated Supabase MCP tool is currently available. |
| Paid AI matching | Implemented but disabled by default. Enable intentionally only with budget controls. |
| Google Maps route calls | Implemented but disabled by blank API key. Called only on detail-page route action. |
| Email delivery for weekly digest | Not implemented in v1; current digest is in-app. |
| Browser scraping | Not enabled in v1; future adapter only. |
