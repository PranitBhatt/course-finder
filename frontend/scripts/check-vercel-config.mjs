// vercel.json proxies /api to Render, but its destination is a placeholder until the real
// Render host is filled in. Deploying it like that would break every API call, search
// included. So on Vercel (which sets VERCEL=1 during builds) we stop the build instead:
// a failed build leaves the previous deployment live. Local builds are not affected.
import { readFileSync } from 'node:fs';

if (process.env.VERCEL && readFileSync('vercel.json', 'utf8').includes('RENDER_BACKEND_URL_HERE')) {
  console.error('vercel.json: replace RENDER_BACKEND_URL_HERE with the real Render host before deploying.');
  process.exit(1);
}
