# Deploying Course Finder (free, no credit card)

This guide puts the whole app online using only free plans that **don't ask for a card or UPI**.
When a free limit is reached, the service slows down or pauses; it never bills you.

| Part | Service | Free plan notes |
|---|---|---|
| Backend (Spring Boot) | **Render** - Free web service | 512 MB RAM. **Sleeps after ~15 min without visitors**; the next visit wakes it in ~30-60 s. |
| Database (PostgreSQL) | **Neon** - Free | 0.5 GB storage (plenty: the full catalog is far smaller). Pauses itself when idle, wakes in about a second. |
| Frontend (React) | **Vercel** - Hobby | Static site, global CDN. |
| Domain | **Namecheap** `.me` via GitHub Student Pack | Free for 1 year. Without a saved card it simply expires after that. |
| Analytics | **Cloudflare Web Analytics** | Free, cookie-free. |
| Nightly sync | **GitHub Actions** | Free (a few minutes per day). |

> Free plans change. Before signing up, glance at each service's pricing page. If any sign-up
> step asks for a card or a payment, **stop** - that's not the free path described here.

Total time: about 1-2 hours the first time.

---

## Contents

- [0. Before you start](#0-before-you-start)
- [A. Put the code on GitHub](#a-put-the-code-on-github)
- [B. Create the database on Neon](#b-create-the-database-on-neon)
- [C. Deploy the backend on Render](#c-deploy-the-backend-on-render)
- [D. Run the first Coursera sync](#d-run-the-first-coursera-sync)
- [E. Deploy the frontend on Vercel](#e-deploy-the-frontend-on-vercel)
- [F. Claim the .me domain and point it to Vercel](#f-claim-the-me-domain-and-point-it-to-vercel)
- [G. Update CORS and the site URL for the new domain](#g-update-cors-and-the-site-url-for-the-new-domain)
- [H. Set up Cloudflare Web Analytics](#h-set-up-cloudflare-web-analytics)
- [I. Turn on the nightly sync (GitHub Actions)](#i-turn-on-the-nightly-sync-github-actions)
- [J. Add the site to Google Search Console](#j-add-the-site-to-google-search-console)
- [K. Checking stats](#k-checking-stats)
- [L. Troubleshooting](#l-troubleshooting)
- [M. Every environment variable](#m-every-environment-variable)
- [N. Updating the live site later](#n-updating-the-live-site-later)

Throughout this guide, replace:

| Placeholder | Example |
|---|---|
| `YOUR-GITHUB-NAME` | `pranitbhatt` |
| `course-finder-api.onrender.com` | the URL Render gives your backend |
| `course-finder.vercel.app` | the URL Vercel gives your frontend |
| `yourname.me` | your domain |

Commands are shown for **PowerShell** (Windows). In PowerShell, always type `curl.exe`, not `curl`
(plain `curl` is a different PowerShell command).

---

## 0. Before you start

You need:

- A **GitHub account** with the **GitHub Student Developer Pack** approved: <https://education.github.com/pack>
- **Git** installed (you have it)
- The project working locally (README.md) - at least `npm run build` in `frontend` and the backend tests passing

Generate an **admin token** now and keep it in a password manager or a private note.
It protects the admin endpoints (sync and stats). In PowerShell:

```powershell
-join ((48..57) + (97..122) | Get-Random -Count 40 | ForEach-Object { [char]$_ })
```

That prints something like `k3x9...` (40 characters). This is your `ADMIN_TOKEN`.
**Never** commit it or paste it into a code file.

---

## A. Put the code on GitHub

Render and Vercel deploy straight from a GitHub repository, and redeploy every time you push.

1. Go to <https://github.com/new>
   - Repository name: `course-finder`
   - **Public** is recommended (unlimited free GitHub Actions minutes). Private also works.
   - Do **not** add a README, .gitignore or license (the project already has them).
   - Click **Create repository**.

2. In a terminal in `A:\Course Finder`:

   ```powershell
   git remote add origin https://github.com/YOUR-GITHUB-NAME/course-finder.git
   git branch -M main
   git push -u origin main
   ```

   Refresh the GitHub page: you should see `backend/`, `frontend/`, `DEPLOY.md`, etc.
   Check that **no `.env` file** is listed.

---

## B. Create the database on Neon

1. Go to <https://neon.tech> -> **Sign up** -> **Continue with GitHub**. No card is asked for.
2. Create a project:
   - Project name: `course-finder`
   - Postgres version: **16**
   - Region: **AWS Asia Pacific (Singapore)** (closest to India, and matches the Render region below)
   - Click **Create project**.
3. On the project dashboard click **Connect** (or **Connection Details**):
   - Database: `neondb`
   - **Turn "Connection pooling" OFF** (use the *direct* connection - the pooled one doesn't suit Flyway migrations).
   - Copy the connection string. It looks like:

     ```
     postgresql://neondb_owner:AbC123xyz@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
     ```

   This whole string is your `DATABASE_URL`. It contains the password: keep it private.

You don't need to create tables. The backend's Flyway migrations create everything
(including the `pg_trgm` extension for typo-tolerant search) the first time it starts.

---

## C. Deploy the backend on Render

The backend is built from `backend/Dockerfile` (Render has no native Java runtime, so it runs
in a container - Render builds it for you).

1. Go to <https://render.com> -> **Get Started** -> **GitHub**. No card is asked for.
2. Click **New +** -> **Web Service** -> connect your GitHub account if asked -> pick `course-finder`.
3. Fill in the form:

   | Field | Value |
   |---|---|
   | Name | `course-finder-api` (becomes `course-finder-api.onrender.com`) |
   | Region | **Singapore** |
   | Branch | `main` |
   | Root Directory | `backend` |
   | Language / Runtime | **Docker** |
   | Dockerfile Path | `./Dockerfile` (default) |
   | Instance Type | **Free** |

4. Under **Environment Variables**, add (see [section M](#m-every-environment-variable) for details):

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DATABASE_URL` | the Neon connection string from step B |
   | `ADMIN_TOKEN` | the token from step 0 |
   | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` for now (you'll add the real site in step E) |
   | `YOUTUBE_API_KEY` | your YouTube key, or leave it out to run with Coursera only |

5. Open **Advanced** and set **Health Check Path** to `/actuator/health`. Keep **Auto-Deploy** on.
6. Click **Create Web Service**.

The first build takes 5-10 minutes (Maven downloads everything once). Watch the **Logs** tab.
It's live when you see:

```
Started CourseFinderApplication in ... seconds
```

and the top of the page says **Live**. Test it (replace the URL with yours):

```powershell
curl.exe https://course-finder-api.onrender.com/actuator/health
```

Expected: `{"status":"UP"}`

> **What the free plan means in practice:** after ~15 minutes without requests Render stops the
> instance. The next request starts it again, which takes about 30-60 seconds for this app.
> The frontend shows a "Waking up" message during that wait. After that, it's fast.

---

## D. Run the first Coursera sync

On its very first start, the backend notices the database is empty and **starts a Coursera sync
by itself**. You can watch it, or trigger one manually.

**Watch progress** (`courseCount` grows to roughly 25,000 over 5-15 minutes):

```powershell
curl.exe https://course-finder-api.onrender.com/api/sources
```

Look for `"lastSyncStatus":"SUCCESS"` on the COURSERA entry when it's done.

**Trigger a sync manually** (any time later, e.g. if one failed):

```powershell
curl.exe -X POST https://course-finder-api.onrender.com/api/admin/sync/COURSERA -H "X-Admin-Token: YOUR_ADMIN_TOKEN"
```

| Response | Meaning |
|---|---|
| `202` + `"status":"ACCEPTED"` | Sync started in the background |
| `409 Sync already running` | One is already in progress - just wait |
| `401 Unauthorized` | Token missing or wrong |

Running the sync again is always safe: courses are updated in place, never duplicated.

> Keep the browser tab or a `/api/sources` check going while the first sync runs. A free Render
> instance that receives **no** requests for 15 minutes goes to sleep and the sync would stop
> (it resumes cleanly next time). The nightly GitHub Action (step I) does this polling for you.

---

## E. Deploy the frontend on Vercel

1. Go to <https://vercel.com/signup> -> choose **Hobby** -> **Continue with GitHub**. No card is asked for.
2. **Add New...** -> **Project** -> **Import** `course-finder`.
3. Configure:

   | Field | Value |
   |---|---|
   | Framework Preset | **Vite** (detected automatically) |
   | Root Directory | click **Edit** -> choose `frontend` |
   | Build / Output | leave the defaults (`npm run build`, `dist`) |

4. Under **Environment Variables** add:

   | Key | Value |
   |---|---|
   | `VITE_API_URL` | `https://course-finder-api.onrender.com` (no trailing slash) |
   | `VITE_SITE_URL` | `https://course-finder.vercel.app` (you'll change it to your domain in step G) |

5. Click **Deploy**. After ~1 minute you get a URL like `https://course-finder.vercel.app`.

6. **Allow the frontend to call the backend.** On Render -> your service -> **Environment** ->
   edit `CORS_ALLOWED_ORIGINS`:

   ```
   https://course-finder.vercel.app,http://localhost:5173
   ```

   Click **Save Changes**. Render restarts the backend automatically (1-2 minutes).

Open the Vercel URL and search for "python". The first search may show "Waking up" for up to a
minute if the backend was asleep.

`frontend/vercel.json` sends every path to `index.html`, so refreshing any page (or opening a
shared link like `/?q=python`) never shows a 404.

---

## F. Claim the .me domain and point it to Vercel

### F1. Claim the free domain

1. Go to <https://education.github.com/pack> -> find **Namecheap** -> click the offer link
   (it opens Namecheap's education page, e.g. <https://nc.me>).
2. Sign in with GitHub when asked, so Namecheap can verify your Student Pack.
3. Search for a name, e.g. `coursefinder-pranit.me`, pick an available one with the free offer.
4. At checkout the total must be **$0.00**. If it asks for payment, go back - that name isn't covered.
5. Finish creating your Namecheap account. Turn **auto-renew off** (Domain List -> your domain).
   The domain is yours for 1 year.

### F2. Add the domain in Vercel

1. Vercel -> your project -> **Settings** -> **Domains**.
2. Add `yourname.me`. Vercel offers to also add `www.yourname.me` with a redirect - accept.
3. Vercel now shows the **DNS records to create**. They usually look like this, but
   **copy the exact values Vercel shows you**:

   | Type | Host | Value |
   |---|---|---|
   | A | `@` | `76.76.21.21` |
   | CNAME | `www` | `cname.vercel-dns.com` |

### F3. Create the records in Namecheap

1. Namecheap -> **Domain List** -> **Manage** next to your domain -> **Advanced DNS** tab.
2. Under **Host Records**, **delete** the default parking records
   (usually a `CNAME www -> parkingpage.namecheap.com` and a `URL Redirect @`).
3. **Add New Record** for each row Vercel showed you (TTL: Automatic).
4. Save (the green tick).

DNS takes 5 minutes to a few hours. Vercel's Domains page turns green ("Valid Configuration")
when it works, and HTTPS is set up for you automatically.

### F4. Optional: `api.yourname.me` for the backend

Nicer than `onrender.com` in your frontend's requests. Not required.

1. Render -> your service -> **Settings** -> **Custom Domains** -> **Add** -> `api.yourname.me`.
2. Render shows a CNAME target (your `course-finder-api.onrender.com`).
3. Namecheap -> Advanced DNS -> **Add New Record**: Type `CNAME`, Host `api`, Value `course-finder-api.onrender.com`.
4. Wait for Render to show the domain as verified (certificate issued).
5. Vercel -> **Settings** -> **Environment Variables** -> set `VITE_API_URL` to
   `https://api.yourname.me` -> **Deployments** -> latest -> **...** -> **Redeploy**.

If you skip this, keep `VITE_API_URL` pointing to the `onrender.com` address. Both work.

---

## G. Update CORS and the site URL for the new domain

Once `https://yourname.me` opens your site:

1. **Render** -> service -> **Environment** -> `CORS_ALLOWED_ORIGINS`:

   ```
   https://yourname.me,https://www.yourname.me,https://course-finder.vercel.app,http://localhost:5173
   ```

   Exact origins only: `https://`, no path, **no trailing slash**. Save (Render restarts).

2. **Vercel** -> **Settings** -> **Environment Variables** -> `VITE_SITE_URL` = `https://yourname.me`
   -> redeploy. This updates the canonical/Open Graph URLs, `robots.txt` and `sitemap.xml`.

Check: open <https://yourname.me/sitemap.xml> - the URLs inside should use your domain.

---

## H. Set up Cloudflare Web Analytics

Free, no cookies (so no cookie banner needed), and works without moving your DNS to Cloudflare.

1. <https://dash.cloudflare.com/sign-up> - create a free account (no card).
2. Left menu -> **Analytics & Logs** -> **Web Analytics** -> **Add a site**.
3. Enter `yourname.me`. When asked how to install, choose the **JS snippet** (manual) option,
   not "automatic setup" (that one needs your DNS on Cloudflare).
4. Cloudflare shows a snippet like:

   ```html
   <script defer src='https://static.cloudflareinsights.com/beacon.min.js'
           data-cf-beacon='{"token": "0123456789abcdef0123456789abcdef"}'></script>
   ```

   Copy **only the token** (the value after `"token":`).

5. Vercel -> **Settings** -> **Environment Variables** -> add
   `VITE_CF_ANALYTICS_TOKEN` = that token -> redeploy.

The build inserts the script where `frontend/index.html` has the marked
`CLOUDFLARE WEB ANALYTICS` placeholder. Visits appear in the Cloudflare dashboard within minutes.
(You can instead paste the snippet over the `<!-- CLOUDFLARE_WEB_ANALYTICS -->` line yourself;
the token isn't secret, but the environment variable keeps it out of the code.)

---

## I. Turn on the nightly sync (GitHub Actions)

`.github/workflows/nightly-sync.yml` runs every night at **02:00 IST** (20:30 UTC). It wakes the
backend, starts the Coursera sync, and waits until it finishes (which also keeps the free instance
awake meanwhile). If the sync fails, the run fails and GitHub emails you.

1. GitHub -> your repo -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**:

   | Name | Value |
   |---|---|
   | `BACKEND_URL` | `https://course-finder-api.onrender.com` (or `https://api.yourname.me`) |
   | `ADMIN_TOKEN` | the same token you set on Render |

2. Test it now: **Actions** tab -> **Nightly Coursera sync** -> **Run workflow** -> **Run workflow**.
   Click the run to watch the log; it ends with `Sync finished.` (5-20 minutes).

> GitHub pauses scheduled workflows in repositories with **no commits for 60 days**. If that
> happens you'll get an email; open the Actions tab and click **Enable workflow**.

---

## J. Add the site to Google Search Console

1. Go to <https://search.google.com/search-console> -> **Add property**.
2. Choose **Domain** and enter `yourname.me` (covers `www` and `https` too).
3. Google shows a **TXT record** like `google-site-verification=abc123...`.
4. Namecheap -> Advanced DNS -> **Add New Record**: Type `TXT Record`, Host `@`, Value = the whole
   `google-site-verification=...` text -> save.
5. Back in Search Console click **Verify** (if it fails, wait 15-30 minutes and retry).
6. Left menu -> **Sitemaps** -> enter `sitemap.xml` -> **Submit**. Status becomes "Success".

Indexing takes days to weeks. You can request indexing of the home page under **URL Inspection**.

---

## K. Checking stats

**Visitors** - Cloudflare dashboard -> **Web Analytics** -> your site: visits, page views,
countries, devices, referrers.

**What people search for** - the backend's admin stats endpoint:

```powershell
curl.exe https://course-finder-api.onrender.com/api/admin/stats -H "X-Admin-Token: YOUR_ADMIN_TOKEN"
```

Prettier output in PowerShell:

```powershell
Invoke-RestMethod https://course-finder-api.onrender.com/api/admin/stats -Headers @{ "X-Admin-Token" = "YOUR_ADMIN_TOKEN" } | ConvertTo-Json -Depth 5
```

You get:

| Field | Meaning |
|---|---|
| `totalSearches` | All searches ever (first page of results only, so paging isn't double-counted) |
| `searchesPerDay` | Last 30 days, oldest first, in Indian time (`timeZone`) |
| `topQueries` | The 20 most searched topics |
| `topZeroResultQueries` | The 20 most searched topics that found **nothing** - content you're missing |

Database contents: Neon dashboard -> **SQL Editor**, e.g.

```sql
SELECT source, COUNT(*) FROM course GROUP BY source;
SELECT * FROM sync_run ORDER BY started_at DESC LIMIT 5;
SELECT * FROM search_log ORDER BY created_at DESC LIMIT 20;
```

---

## L. Troubleshooting

### Reading logs

- **Backend:** Render -> your service -> **Logs**. Live, searchable; one line per event.
  Use the search box for `ERROR` or `WARN`.
- **Deploy problems:** Render -> **Events** -> click a deploy to see its build log.
- **Frontend build:** Vercel -> **Deployments** -> click a deployment -> **Build Logs**.
- **Frontend in the browser:** press F12 -> **Console** and **Network** tabs.
- **Nightly sync:** GitHub -> **Actions** -> click a run.

### The backend crashes on startup

Find the first `ERROR` or `Caused by:` line in the Render logs.

| Log says | Fix |
|---|---|
| `DATABASE_URL is not set` | Add `DATABASE_URL` on Render (step C) |
| `DATABASE_URL must start with postgres://...` | Paste the full Neon string, starting with `postgresql://` |
| `Validate failed: Migrations have failed validation` | A migration file was edited after it ran. Never change applied files in `db/migration`; add a new `V5__...sql` instead |
| `No open ports detected` / deploy timed out | Usually a crash just before start - scroll up for the real error |
| `Exited with status 137` or `Out of memory` | See "out of memory" below |

### Database connection errors

| Symptom | Fix |
|---|---|
| `password authentication failed` | Copy the connection string again from Neon (or reset the password there) and update `DATABASE_URL` |
| `Connection attempt timed out` right after a quiet period | Neon was waking up; it retries on the next request. If constant, check the Neon project isn't suspended for exceeding free limits (Neon dashboard -> Billing/Usage) |
| `prepared statement "S_1" already exists` | You used the **pooled** Neon URL (host contains `-pooler`). Use the direct one (step B) |
| `SSL` errors | The URL must reach `sslmode=require` - the app adds it automatically; don't remove it |

### CORS errors

In the browser console: *"blocked by CORS policy: No 'Access-Control-Allow-Origin' header"*.

1. The origin in the error message (e.g. `https://yourname.me`) must be in `CORS_ALLOWED_ORIGINS`
   **exactly**: `https://`, no trailing `/`, no path. `www` and non-`www` are different origins.
2. After saving on Render, wait for the restart to finish.
3. If the request URL in the error is wrong (e.g. `undefined/api/...`), `VITE_API_URL` is missing
   on Vercel - set it and **redeploy** (Vite bakes it in at build time).

### Out of memory on the backend

Render log: `Exited with status 137`, `Out of memory`, or `java.lang.OutOfMemoryError`.

1. Render -> **Environment** -> add `JAVA_TOOL_OPTIONS` with a smaller heap:

   ```
   -XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k
   ```

2. Save; Render restarts. (The default, set in the Dockerfile, uses 75%.)

### The site shows "Can't reach the server" or keeps "Waking up"

- First visit after a quiet period: wait a minute and press **Try again** - normal on the free plan.
- Still failing: open `https://course-finder-api.onrender.com/actuator/health`. If that doesn't say
  `UP`, check the Render logs. If it does, check `VITE_API_URL` and CORS above.

### "Slow down a little" / HTTP 429

Each visitor (IP address) can search 60 times per minute. Change `app.rate-limit.search-requests-per-minute`
in `application.yml` if needed.

### The nightly sync workflow failed

Open the run in GitHub Actions:
- `Set the BACKEND_URL and ADMIN_TOKEN repository secrets` -> add them (step I).
- `HTTP 401` -> `ADMIN_TOKEN` on GitHub differs from Render.
- `Sync failed` -> Render logs around that time; Coursera's unofficial API may have hiccupped.
  The next night retries automatically, and existing courses are kept.

---

## M. Every environment variable

Secrets are never in the repository. Each value lives only in the service that needs it.

### Backend (Render -> service -> Environment)

| Name | Required | Example | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `prod` | Loads `application-prod.yml` (already the default in the Dockerfile) |
| `DATABASE_URL` | yes | `postgresql://neondb_owner:...@ep-...neon.tech/neondb?sslmode=require` | Neon connection string, **secret** |
| `ADMIN_TOKEN` | yes | `k3x9...` (40 random chars) | Protects `/api/admin/**`, **secret** |
| `CORS_ALLOWED_ORIGINS` | yes | `https://yourname.me,https://www.yourname.me` | Sites allowed to call the API, comma-separated |
| `YOUTUBE_API_KEY` | no | `AIza...` | Enables YouTube results, **secret** |
| `JAVA_TOOL_OPTIONS` | no | `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC` | Only to override the Dockerfile's memory settings |
| `PORT` | - | `10000` | Set by Render automatically; don't add it |

### Frontend (Vercel -> project -> Settings -> Environment Variables)

Baked in at **build time**: after changing one, redeploy.

| Name | Required | Example | Purpose |
|---|---|---|---|
| `VITE_API_URL` | yes | `https://course-finder-api.onrender.com` | Backend address |
| `VITE_SITE_URL` | yes | `https://yourname.me` | Canonical/OG URLs, `robots.txt`, `sitemap.xml` |
| `VITE_CF_ANALYTICS_TOKEN` | no | `0123456789abcdef...` | Cloudflare Web Analytics (not secret) |

### GitHub (repo -> Settings -> Secrets and variables -> Actions)

| Name | Example | Purpose |
|---|---|---|
| `BACKEND_URL` | `https://course-finder-api.onrender.com` | Where the nightly sync calls |
| `ADMIN_TOKEN` | same as on Render | Authorizes the sync call |

### Local development (IntelliJ run configuration / terminal)

| Name | Default | Notes |
|---|---|---|
| `YOUTUBE_API_KEY` | empty | Optional |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `localhost` / `5432` / `coursefinder` / `coursefinder` / `coursefinder` | Match `docker-compose.yml` and the local PostgreSQL setup |
| `ADMIN_TOKEN` | `dev-admin-token` (dev profile) | Local admin calls: `-H "X-Admin-Token: dev-admin-token"` |

---

## N. Updating the live site later

Just push to GitHub:

```powershell
git add -A
git commit -m "Describe the change"
git push
```

- Changes in `backend/` -> Render rebuilds and redeploys the backend (~5 min).
- Changes in `frontend/` -> Vercel rebuilds the frontend (~1 min).
- New database changes go in a **new** migration file (`V5__...sql`); it runs automatically on the next deploy.
