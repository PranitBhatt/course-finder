/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin in production, e.g. https://course-finder-api.onrender.com. Empty locally. */
  readonly VITE_API_URL?: string;
  /** Public site address, e.g. https://coursefinder.me. Used for SEO tags, robots.txt and sitemap.xml. */
  readonly VITE_SITE_URL?: string;
  /** Cloudflare Web Analytics token; read by vite.config.ts at build time. */
  readonly VITE_CF_ANALYTICS_TOKEN?: string;
  /** "true" shows the login/register UI. Anything else (or unset) hides it everywhere. */
  readonly VITE_AUTH_ENABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
