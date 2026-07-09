interface Env {
  ASSETS: Fetcher;
  BACKEND_URL: string;
  // Shared secret proving this request came through the Worker, not straight to
  // the origin. Set via `wrangler secret put ORIGIN_SHARED_SECRET`.
  ORIGIN_SHARED_SECRET?: string;
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

    // The backend rate limiter keys on X-Forwarded-For, so pin it to Cloudflare's
    // view of the real client IP and drop any client-supplied value — otherwise a
    // caller could rotate the header to defeat the limit.
    const clientIp = request.headers.get('cf-connecting-ip');
    if (clientIp) {
      proxied.headers.set('x-forwarded-for', clientIp);
    } else {
      proxied.headers.delete('x-forwarded-for');
    }

    // Prove to the origin that this request came through the Worker. The origin
    // rejects anything without it, so the trust headers below can't be forged by
    // hitting the backend directly. Client-supplied copies are overwritten here.
    proxied.headers.delete('x-origin-auth');
    if (env.ORIGIN_SHARED_SECRET) {
      proxied.headers.set('x-origin-auth', env.ORIGIN_SHARED_SECRET);
    }

    // Cloudflare exposes the visitor's country on request.cf (not as a header the
    // origin would otherwise see), so forward it explicitly for click analytics.
    // Drop any client-supplied copy first so a forged value can't survive when
    // cf.country is absent — the origin should only ever trust Cloudflare's geo-IP.
    proxied.headers.delete('x-client-country');
    const country = (request as { cf?: { country?: string } }).cf?.country;
    if (country) {
      proxied.headers.set('x-client-country', country);
    }

    return fetch(proxied, { redirect: 'manual' });
  },

  // Render's free tier spins the backend container down after ~15 min idle, so
  // the next visitor eats a 15s+ cold start (and the page waits on it). This
  // cron keeps the app tier warm by pinging the backend's liveness endpoint.
  // The origin rejects anything without the shared secret (see OriginAuthFilter),
  // so we must send it here just like the proxy path above does.
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    const headers: Record<string, string> = { 'user-agent': 'wayfare-keepalive' };
    if (env.ORIGIN_SHARED_SECRET) {
      headers['x-origin-auth'] = env.ORIGIN_SHARED_SECRET;
    }
    ctx.waitUntil(
      fetch(new URL('/_keepalive', env.BACKEND_URL), { headers }).catch(() => {}),
    );
  },
};
