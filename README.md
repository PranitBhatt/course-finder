# Course Finder

Search courses from multiple platforms in one list. Type a topic ("machine learning") and get
ranked results from Coursera and YouTube.

```
Course Finder/
├── backend/             Spring Boot 3 API (Java 21)
├── frontend/            React 19 + TypeScript + Vite
├── docker-compose.yml   PostgreSQL 16
└── .env.example         Environment variables template
```

## How it works

Platforms are **not** called on every search. Their data is ingested into PostgreSQL:

| Source   | How it's ingested                                                                       |
|----------|-----------------------------------------------------------------------------------------|
| Coursera | Full catalog pulled nightly (03:00) from the public `courses.v1` endpoint, upserted in batches. Also runs once at startup if the table is empty. ~25k courses, ~5 min. |
| YouTube  | Fetched on demand: when someone searches a topic not fetched in the last 7 days, the app searches playlists for "&lt;topic&gt; full course" and stores them. |

Search uses PostgreSQL full-text search (`tsvector` + GIN index, ranked with `ts_rank`, title
weighted above description). If nothing matches, it falls back to typo-tolerant title matching
with `pg_trgm` (so "machne lerning" still finds "Machine Learning"); the response then has
`"fuzzyMatch": true`.

### Backend layout (`backend/src/main/java/com/coursefinder`)

| Package       | Contents                                                                    |
|---------------|-----------------------------------------------------------------------------|
| `controller`  | REST endpoints                                                              |
| `service`     | Search, sync and caching logic; `mapper` converts entities to DTOs          |
| `repository`  | Spring Data JPA + custom SQL for upsert and search                          |
| `connector`   | One sub-package per platform behind `CatalogConnector` / `TopicSearchConnector` |
| `entity`      | JPA entities and enums                                                      |
| `dto`         | Request / response records - entities never leave the service layer         |
| `config`      | CORS, HTTP clients, scheduling, typed `app.*` properties                    |
| `scheduler`   | Nightly Coursera sync                                                       |
| `exception`   | Custom exceptions + global handler (RFC 7807 `ProblemDetail` responses)     |

Database schema lives in `backend/src/main/resources/db/migration` (Flyway).

## Prerequisites

- **JDK 21+** (IntelliJ: *File > Project Structure > SDK*, or download from <https://adoptium.net>)
- **Docker Desktop** - for PostgreSQL and for the Testcontainers integration test
- Maven is **not** required - use the included wrapper (`mvnw` / `mvnw.cmd`)
- Optional: a **YouTube Data API v3 key** (free). Without it the app runs with Coursera only.

### Getting a YouTube API key

1. Open <https://console.cloud.google.com/>, create a project
2. *APIs & Services > Library* - enable **YouTube Data API v3**
3. *APIs & Services > Credentials > Create credentials > API key*
4. Recommended: restrict the key to the YouTube Data API v3

The free quota is 10,000 units/day. One new topic costs 101 units (search + playlist details),
so ~90 new topics per day. The app stops itself at 9,000 units (`app.youtube.daily-quota-units`)
and keeps serving cached results.

## Setup

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and set `YOUTUBE_API_KEY` (optional). `.env` is git-ignored.

### 2. Start PostgreSQL

```bash
docker compose up -d
```

### 3. Run the backend

**From IntelliJ**

1. *File > Open* and select the `backend` folder (or the root, then add `backend/pom.xml` as a Maven project)
2. Open `CourseFinderApplication` > *Modify Run Configuration*:
   - *Active profiles*: `dev` (enables the admin sync endpoint + debug logs)
   - *Environment variables*: `YOUTUBE_API_KEY=your-key` (or use the EnvFile plugin with `.env`)
3. Run it. Flyway creates the schema and the first Coursera sync starts in the background.

**From a terminal** (from the `backend` folder)

```bash
YOUTUBE_API_KEY=your-key ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows PowerShell:

```powershell
$env:YOUTUBE_API_KEY="your-key"; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The API runs on <http://localhost:8080>.

### 4. Run the frontend

Needs Node 20.19+ (or 22.12+). From the `frontend` folder:

```bash
npm install
npm run dev
```

Open <http://localhost:5173>. Vite forwards `/api/*` to the backend on port 8080, so start the
backend first (without it the page shows a "Can't reach the server" state).

- `npm run build` - typecheck + production build into `frontend/dist`
- Dark mode is the default; the toggle top-right switches it and is remembered per browser
- The search lives in the URL (`/?q=python&source=youtube&free=1&page=2`), so any results page can be shared

Frontend layout (`frontend/src`):

| Folder        | Contents                                                        |
|---------------|-----------------------------------------------------------------|
| `api`         | Typed fetch client and the backend's response types             |
| `hooks`       | Search state in the URL, debounce, data fetching, theme         |
| `pages`       | `SearchPage`, `NotFoundPage`                                    |
| `components`  | `course/` cards + skeletons, `search/` bar + filters, `pagination/`, `feedback/` empty/error states, `layout/`, `ui/` |
| `styles`      | `tokens.css` (colors, fonts, spacing for both themes), `global.css` |

The visual style (colors, Inter Tight + JetBrains Mono, soft white cards, the blue "pill"
headline, dot-grid background) is taken from the Clean Tech reel design.

### 5. Run the tests

```bash
./mvnw test
```

- Connector tests mock HTTP with `MockRestServiceServer` (no network needed)
- `CourseSearchIntegrationTest` runs against a real PostgreSQL 16 via Testcontainers.
  **It is skipped automatically if Docker isn't running.**

## API

All errors use the RFC 7807 problem format:

```json
{ "status": 400, "title": "Validation failed", "detail": "...", "errors": { "size": "must be at most 100" } }
```

### `GET /api/courses/search`

| Param    | Required | Description                                             |
|----------|----------|---------------------------------------------------------|
| `q`      | yes      | Search text, max 200 chars. Supports `"exact phrase"`, `-exclude`, `or` |
| `source` | no       | `coursera` or `youtube` (case-insensitive)              |
| `free`   | no       | `true` = only free. Coursera courses have `free: null` (unknown - audit is usually free, certificate is paid) so they match neither `true` nor `false` |
| `page`   | no       | Zero-based, default `0`                                 |
| `size`   | no       | 1-100, default `20`                                     |

```bash
curl "http://localhost:8080/api/courses/search?q=machine%20learning&size=5"
```

```json
{
  "query": "machine learning",
  "fuzzyMatch": false,
  "content": [
    {
      "id": 113,
      "source": "COURSERA",
      "sourceName": "Coursera",
      "title": "Machine Learning",
      "description": "...",
      "url": "https://www.coursera.org/learn/machine-learning",
      "thumbnailUrl": "https://...",
      "provider": "Stanford University, DeepLearning.AI",
      "language": "en",
      "level": null,
      "durationText": "60 hours",
      "free": null,
      "rating": null,
      "lastSyncedAt": "2026-11-01T03:00:12.123Z",
      "score": 0.9119
    }
  ],
  "page": 0,
  "size": 5,
  "totalElements": 412,
  "totalPages": 83
}
```

### `GET /api/courses/{id}`

One course, same fields as above without `score`. `404` if not found.

### `GET /api/sources`

```json
[
  { "source": "COURSERA", "displayName": "Coursera", "syncMode": "FULL_CATALOG", "enabled": true,
    "courseCount": 25438, "lastSyncedAt": "...", "lastSyncStatus": "SUCCESS", "lastSyncFinishedAt": "..." },
  { "source": "YOUTUBE", "displayName": "YouTube", "syncMode": "ON_DEMAND", "enabled": true,
    "courseCount": 340, "lastSyncedAt": "...", "lastSyncStatus": null, "lastSyncFinishedAt": null }
]
```

### `POST /api/admin/sync/{source}` (dev profile only)

Starts a background catalog sync and returns `202 Accepted`. Watch progress via `/api/sources`.

- `409` if a sync for that source is already running
- `400` for `youtube` (on-demand only, nothing to sync)
- `404` when not running with the `dev` profile (the endpoint has no auth, so it's off by default)

```bash
curl -X POST http://localhost:8080/api/admin/sync/coursera
```

## Configuration

All settings are in `backend/src/main/resources/application.yml` under `app.*`. Common ones:

| Property                             | Default        | Meaning                                 |
|--------------------------------------|----------------|-----------------------------------------|
| `app.cors.allowed-origins`           | `http://localhost:5173` | Vite dev server                |
| `app.coursera.enabled`               | `true`         | Turn the Coursera connector/scheduler off |
| `app.coursera.sync-cron`             | `0 0 3 * * *`  | Nightly sync time (server time zone)    |
| `app.youtube.cache-ttl`              | `7d`           | How long a fetched topic stays fresh    |
| `app.youtube.daily-quota-units`      | `9000`         | Our own daily budget (YouTube allows 10,000) |
| `app.search.fuzzy-threshold`         | `0.4`          | Typo tolerance (0-1, lower = more forgiving) |

Database connection uses `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` (defaults match `docker-compose.yml`).

## Adding a new platform

1. Add a constant to `CourseSource`
2. Add a Flyway migration updating the `ck_course_source` CHECK constraint
3. Create `connector/<platform>/<Platform>Connector` implementing `CatalogConnector`
   (full catalog) or `TopicSearchConnector` (search by topic), annotated `@Component`
4. Add a unit test with `MockRestServiceServer`

Services pick up new connectors automatically.

## Notes

- The Coursera endpoint is unofficial and may change. Transient failures are retried; if a sync
  fails, existing data is kept and the failure is visible in `/api/sources` (`lastSyncStatus`).
  Courses are only removed after a **complete** successful sync.
- The YouTube API key is sent in the `X-Goog-Api-Key` header, so it never shows up in URLs or logs.
- The YouTube quota counter is in memory and resets on restart; YouTube's own 403
  `quotaExceeded` is also detected and stops calls until the next quota day (midnight Pacific).
