import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setAccessToken } from './accessToken';
import { endSession, fetchMe } from './session';

const user = { id: 1, email: 'ada@example.com', displayName: 'Ada', createdAt: '2026-01-01T00:00:00Z' };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('refresh single-flight', () => {
  let refreshCalls: number;
  let meCalls: number;
  let lockRequests: number;

  beforeEach(() => {
    refreshCalls = 0;
    meCalls = 0;
    lockRequests = 0;

    // A fake backend: /me only accepts the NEW token, /refresh hands out the new token.
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url === '/api/auth/refresh') {
          refreshCalls++;
          // Answer a little later, so all five 401s pile up while the refresh is still running.
          await new Promise((resolve) => setTimeout(resolve, 20));
          return json(200, { accessToken: 'new-token', expiresIn: 900, user });
        }
        if (url === '/api/me') {
          meCalls++;
          const auth = (init.headers as Record<string, string>).Authorization;
          return auth === 'Bearer new-token' ? json(200, user) : json(401, { status: 401, title: 'Unauthorized' });
        }
        throw new Error(`unexpected request: ${url}`);
      }),
    );

    // Node has no Web Locks API; this stand-in just runs the callback, and counts how often it's asked.
    vi.stubGlobal('navigator', {
      locks: {
        request: (_name: string, callback: () => Promise<unknown>) => {
          lockRequests++;
          return callback();
        },
      },
    });

    // Start with an access token the fake backend considers expired.
    setAccessToken('expired-token');
  });

  afterEach(() => {
    endSession(); // also cancels the proactive-refresh timer
    vi.unstubAllGlobals();
  });

  it('shares one /refresh call between 5 parallel 401s, then each request retries once', async () => {
    const results = await Promise.all([fetchMe(), fetchMe(), fetchMe(), fetchMe(), fetchMe()]);

    expect(refreshCalls).toBe(1);
    expect(lockRequests).toBe(1);
    // 5 first attempts (all 401) + 5 retries (all 200).
    expect(meCalls).toBe(10);
    expect(results).toEqual([user, user, user, user, user]);
  });
});
