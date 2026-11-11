import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setAccessToken } from './accessToken';
import { lookupProgress, saveProgress } from './progress';
import { endSession } from './session';

const user = { id: 'u1', email: 'ada@example.com', displayName: 'Ada', createdAt: '2026-01-01T00:00:00Z' };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('progress api', () => {
  let calls: { url: string; init: RequestInit }[];

  beforeEach(() => {
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        calls.push({ url, init });
        if (url === '/api/auth/refresh') return json(200, { accessToken: 'new-token', expiresIn: 900, user });
        const auth = (init.headers as Record<string, string>).Authorization;
        if (auth !== 'Bearer new-token') return json(401, { status: 401, title: 'Unauthorized' });
        if (url.startsWith('/api/me/progress/lookup')) {
          return json(200, { '7': { status: 'IN_PROGRESS', progressPercent: 40 } });
        }
        if (url === '/api/me/progress/7' && init.method === 'PUT') {
          return json(200, { courseId: 7, status: 'COMPLETED', progressPercent: 100 });
        }
        throw new Error(`unexpected request: ${url}`);
      }),
    );
    vi.stubGlobal('navigator', {});
    setAccessToken('new-token');
  });

  afterEach(() => {
    endSession();
    vi.unstubAllGlobals();
  });

  it('asks for a whole results page in ONE request and returns numeric ids', async () => {
    const result = await lookupProgress([7, 8, 9]);

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('/api/me/progress/lookup?courseIds=7,8,9');
    expect(calls[0].init.credentials).toBe('omit');
    expect(result).toEqual({ 7: { status: 'IN_PROGRESS', progressPercent: 40 } });
  });

  it('refreshes once on a 401 and retries the PUT with the new token', async () => {
    setAccessToken('expired-token');

    const item = await saveProgress(7, { status: 'COMPLETED' });

    expect(item.progressPercent).toBe(100);
    expect(calls.map((c) => c.url)).toEqual(['/api/me/progress/7', '/api/auth/refresh', '/api/me/progress/7']);
    expect(calls[2].init.body).toBe(JSON.stringify({ status: 'COMPLETED' }));
  });
});
