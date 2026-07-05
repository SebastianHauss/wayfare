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

    // OAuth callback URLs are registered for the apex domain only (GitHub in
    // particular allows just one), so www requests must not reach the backend
    // with a different host or the provider rejects the redirect_uri.
    if (url.hostname.startsWith('www.')) {
      url.hostname = url.hostname.slice('www.'.length);
      return Response.redirect(url.toString(), 301);
    }

    // The SPA's client-side routes aren't files in dist/, so without this they
    // would fall through and be proxied to the backend (where e.g. a bare
    // /verify-email is misread as a short-link code). Serve the SPA shell for
    // them instead so client-side routing can take over.
    const SPA_ROUTES = new Set(['/auth/callback', '/verify-email', '/reset-password']);
    if (SPA_ROUTES.has(url.pathname)) {
      return env.ASSETS.fetch(new Request(new URL('/', request.url), request));
    }

    const backendUrl = new URL(url.pathname + url.search, env.BACKEND_URL);
    const proxied = new Request(backendUrl, request);
    proxied.headers.set('host', backendUrl.host);
    proxied.headers.set('x-forwarded-host', url.host);
    proxied.headers.set('x-forwarded-proto', url.protocol.replace(':', ''));
    proxied.headers.set('x-forwarded-port', url.protocol === 'https:' ? '443' : '80');
    return fetch(proxied, { redirect: 'manual' });
  },

  // Render's free tier spins the backend container down after ~15 min idle, so
  // the next visitor eats a 15s+ cold start (and the page waits on it). This
  // cron keeps it warm. We hit a short-code lookup for a code that won't exist:
  // it walks Redis (Upstash) then Postgres (Neon) before returning 404, so a
  // single cheap request warms the app tier and both backing stores at once.
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(
      fetch(new URL('/_keepalive', env.BACKEND_URL), {
        headers: { 'user-agent': 'wayfare-keepalive' },
      }).catch(() => {}),
    );
  },
};
