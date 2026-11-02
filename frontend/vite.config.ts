import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

// Topics listed in sitemap.xml so search engines find a few useful result pages.
const SITEMAP_TOPICS = ['machine learning', 'python', 'data science', 'web development', 'sql', 'ux design'];

/**
 * Fills in everything that depends on the deployed address, at build time:
 *  - __SITE_URL__ in index.html (canonical + Open Graph URLs)
 *  - the Cloudflare Web Analytics script, if VITE_CF_ANALYTICS_TOKEN is set
 *  - robots.txt and sitemap.xml in the build output
 */
function siteFiles(siteUrl: string, analyticsToken: string): Plugin {
  const escapeXml = (s: string) => s.replace(/&/g, '&amp;');

  return {
    name: 'course-finder-site-files',
    transformIndexHtml(html) {
      const beacon = analyticsToken
        ? `<script defer src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='${JSON.stringify({ token: analyticsToken })}'></script>`
        : '';
      return html.replaceAll('__SITE_URL__', siteUrl).replace('<!-- CLOUDFLARE_WEB_ANALYTICS -->', beacon);
    },
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'robots.txt',
        source: `User-agent: *\nAllow: /\n\nSitemap: ${siteUrl}/sitemap.xml\n`,
      });

      const today = new Date().toISOString().slice(0, 10);
      const urls = [`${siteUrl}/`, ...SITEMAP_TOPICS.map((t) => `${siteUrl}/?q=${encodeURIComponent(t)}`)];
      const entries = urls
        .map((loc) => `  <url>\n    <loc>${escapeXml(loc)}</loc>\n    <lastmod>${today}</lastmod>\n  </url>`)
        .join('\n');
      this.emitFile({
        type: 'asset',
        fileName: 'sitemap.xml',
        source: `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries}\n</urlset>\n`,
      });
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_');
  const siteUrl = (env.VITE_SITE_URL || 'http://localhost:5173').replace(/\/+$/, '');

  return {
    plugins: [react(), siteFiles(siteUrl, env.VITE_CF_ANALYTICS_TOKEN ?? '')],
    server: {
      port: 5173,
      // Local development: /api/* is forwarded to the Spring Boot backend, so the browser sees one origin.
      // In production VITE_API_URL points the app at the deployed backend instead.
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
