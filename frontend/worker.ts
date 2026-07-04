interface Env {
  ASSETS: Fetcher;
  BACKEND_URL: string;
}

// Cloudflare serves any matching file in dist/ before this runs, so we only
// ever see requests for paths that aren't static assets: the SPA's own
// client-side routes, or short-link codes/QR codes that belong to the backend.
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === '/auth/callback') {
      return env.ASSETS.fetch(new Request(new URL('/', request.url), request));
    }

    const backendUrl = new URL(url.pathname + url.search, env.BACKEND_URL);
    const proxied = new Request(backendUrl, request);
    proxied.headers.set('host', backendUrl.host);
    return fetch(proxied, { redirect: 'manual' });
  },
};
