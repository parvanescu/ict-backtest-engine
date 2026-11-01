import { afterEach, describe, expect, it, vi } from 'vitest';
import { HTTP_BASE } from './config';
import { resetData } from './history';

afterEach(() => vi.unstubAllGlobals());

describe('resetData', () => {
  it('posts an empty JSON body to /api/reset', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetchMock);

    await resetData();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${HTTP_BASE}/api/reset`);
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{}');
  });

  it('rejects with the HTTP status when the service refuses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));

    await expect(resetData()).rejects.toThrow('POST /api/reset: HTTP 500');
  });
});
