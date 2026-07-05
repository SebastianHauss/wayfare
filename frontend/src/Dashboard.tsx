import { useEffect, useState } from 'react';
import * as api from './api';
import { ArrowClockwise, ChartBar, Check, Copy, QrCode, Trash, WarningCircle } from '@phosphor-icons/react';
import { LinkStatsModal } from './LinkStatsModal';
import type { LinkResponse, MeResponse } from './types';

const SHRINK_DISTANCE = 200;
const TITLE_MAX = 64;
const TITLE_MIN = 22;
const PAD_MAX = 32;
const PAD_MIN = 12;

const EXPIRY_PRESETS: { label: string; value: string; ms: number | null }[] = [
  { label: 'Never', value: '', ms: null },
  { label: '1 hour', value: '1h', ms: 60 * 60 * 1000 },
  { label: '1 day', value: '1d', ms: 24 * 60 * 60 * 1000 },
  { label: '1 week', value: '7d', ms: 7 * 24 * 60 * 60 * 1000 },
  { label: '1 month', value: '30d', ms: 30 * 24 * 60 * 60 * 1000 },
];

function isClickLimitedExpired(link: LinkResponse): boolean {
  return link.maxClicks !== null && (link.clickCount ?? 0) >= link.maxClicks;
}

function getExpiryBadge(link: LinkResponse): { label: string; expired: boolean } | null {
  if (isClickLimitedExpired(link)) return { label: 'Expired', expired: true };
  if (!link.expiresAt) return null;

  const expiry = new Date(link.expiresAt);
  if (Number.isNaN(expiry.getTime())) return null;

  const diffMs = expiry.getTime() - Date.now();
  if (diffMs <= 0) return { label: 'Expired', expired: true };

  const hourMs = 60 * 60 * 1000;
  const dayMs = 24 * hourMs;
  if (diffMs < hourMs) return { label: 'Expires soon', expired: false };
  if (diffMs < dayMs) return { label: `Expires ${Math.ceil(diffMs / hourMs)}h`, expired: false };
  if (diffMs < 7 * dayMs) return { label: `Expires ${Math.ceil(diffMs / dayMs)}d`, expired: false };

  return {
    label: `Expires ${expiry.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`,
    expired: false,
  };
}

export function Dashboard({
  user,
  onLogout,
  onAccountDeleted,
  onLoginClick,
}: {
  user: MeResponse | null;
  onLogout: () => void;
  onAccountDeleted: () => void;
  onLoginClick: () => void;
}) {
  const [links, setLinks] = useState<LinkResponse[]>([]);
  const [loadingLinks, setLoadingLinks] = useState(true);
  const [refreshingLinks, setRefreshingLinks] = useState(false);
  const [refreshStatus, setRefreshStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [newUrl, setNewUrl] = useState('');
  const [shortening, setShortening] = useState(false);
  const [shortenError, setShortenError] = useState('');
  const [showOptions, setShowOptions] = useState(false);
  const [alias, setAlias] = useState('');
  const [expiryPreset, setExpiryPreset] = useState('');
  const [maxClicks, setMaxClicks] = useState('');
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [qrLink, setQrLink] = useState<LinkResponse | null>(null);
  const [statsLink, setStatsLink] = useState<LinkResponse | null>(null);
  const [scrollY, setScrollY] = useState(0);
  const [showDeleteAccount, setShowDeleteAccount] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deletingAccount, setDeletingAccount] = useState(false);
  const [deleteAccountError, setDeleteAccountError] = useState('');

  useEffect(() => {
    setQrLink(null);
    setStatsLink(null);
    loadLinks();
  }, [user?.id]);

  useEffect(() => {
    const onScroll = () => setScrollY(window.scrollY);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const progress = Math.min(scrollY / SHRINK_DISTANCE, 1);
  const titleSize = TITLE_MAX - (TITLE_MAX - TITLE_MIN) * progress;
  const heroPad = PAD_MAX - (PAD_MAX - PAD_MIN) * progress;
  const subtleOpacity = Math.max(0, 1 - progress * 1.6);
  const totalClicks = links.reduce((sum, l) => sum + (l.clickCount ?? 0), 0);

  async function loadLinks(showLoading = true) {
    if (!user) {
      setLinks(api.getAnonymousLinks());
      setLoadingLinks(false);
      return;
    }
    if (showLoading) setLoadingLinks(true);
    try {
      setLinks(await api.getMyLinks());
    } finally {
      if (showLoading) setLoadingLinks(false);
    }
  }

  async function handleRefreshLinks() {
    const minimumSpin = new Promise((resolve) => setTimeout(resolve, 500));
    setRefreshingLinks(true);
    setRefreshStatus('idle');
    try {
      await Promise.all([loadLinks(false), minimumSpin]);
      setRefreshStatus('success');
    } catch {
      await minimumSpin;
      setRefreshStatus('error');
    } finally {
      setRefreshingLinks(false);
      setTimeout(() => setRefreshStatus('idle'), 900);
    }
  }

  async function handleShorten(e: React.FormEvent) {
    e.preventDefault();
    setShortening(true);
    setShortenError('');
    try {
      const preset = EXPIRY_PRESETS.find((p) => p.value === expiryPreset);
      const created = await api.shorten(newUrl, {
        alias: user ? alias.trim() || undefined : undefined,
        expiresAt: preset?.ms ? new Date(Date.now() + preset.ms).toISOString() : null,
        maxClicks: maxClicks.trim() ? Number(maxClicks) : null,
      });
      setNewUrl('');
      setAlias('');
      setExpiryPreset('');
      setMaxClicks('');
      if (user) {
        await loadLinks();
      } else {
        setLinks(api.saveAnonymousLink(created));
      }
    } catch (err) {
      setShortenError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setShortening(false);
    }
  }

  async function handleDelete(shortCode: string) {
    if (!confirm('Delete this link?')) return;
    await api.deleteLink(shortCode);
    setLinks((prev) => prev.filter((l) => l.shortCode !== shortCode));
  }

  function handleRemoveAnonymous(shortCode: string) {
    setLinks(api.removeAnonymousLink(shortCode));
  }

  function handleCopy(shortUrl: string, shortCode: string) {
    navigator.clipboard.writeText(shortUrl);
    setCopiedCode(shortCode);
    setTimeout(() => setCopiedCode((c) => (c === shortCode ? null : c)), 1500);
  }

  function updateLinkClickCount(shortCode: string, clickCount: number) {
    setLinks((prev) =>
      prev.map((link) => (link.shortCode === shortCode ? { ...link, clickCount } : link))
    );
    setStatsLink((current) =>
      current?.shortCode === shortCode ? { ...current, clickCount } : current
    );
  }

  function handleShortLinkOpen(link: LinkResponse) {
    if (!user) return;
    if (isClickLimitedExpired(link)) return;

    const nextClickCount = (link.clickCount ?? 0) + 1;
    updateLinkClickCount(
      link.shortCode,
      link.maxClicks === null ? nextClickCount : Math.min(nextClickCount, link.maxClicks)
    );
  }

  async function handleDeleteAccount(e: React.FormEvent) {
    e.preventDefault();
    setDeletingAccount(true);
    setDeleteAccountError('');
    try {
      await api.deleteAccount(deletePassword);
      onAccountDeleted();
    } catch (err) {
      setDeleteAccountError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setDeletingAccount(false);
    }
  }

  return (
    <div className="bg-radial-glow min-h-screen bg-cream">
      <div
        className="sticky top-0 z-10 bg-cream/95 backdrop-blur-sm"
        style={{ borderBottom: `1px solid rgba(43,32,24,${0.08 * progress})` }}
      >
        <div className="mx-auto flex max-w-2xl items-center justify-between px-4 pt-4 text-sm">
          <div className="flex items-center gap-2">
            {user && (
              <>
                <div className="flex h-6 w-6 items-center justify-center rounded-full bg-ink font-display text-[11px] font-bold text-cream">
                  {user.email[0]?.toUpperCase()}
                </div>
                <span className="text-ink-soft">{user.email}</span>
              </>
            )}
          </div>
          <button
            onClick={user ? onLogout : onLoginClick}
            className="rounded-full border border-ink/15 px-3 py-1 text-ink-soft transition hover:border-orange hover:text-orange"
          >
            {user ? 'Log out' : 'Log in'}
          </button>
        </div>
        <div
          className="mx-auto max-w-2xl px-4 text-center"
          style={{ paddingTop: heroPad, paddingBottom: heroPad }}
        >
          <p
            className="mb-1 text-xs font-semibold uppercase tracking-[0.2em] text-orange"
            style={{ opacity: subtleOpacity }}
          >
            Link Shortener
          </p>
          <h1
            className="font-display font-bold tracking-tight text-ink"
            style={{ fontSize: titleSize, lineHeight: 1.1 }}
          >
            Wayfare
          </h1>
          {links.length > 0 && (
            <div
              className="mt-3 flex items-center justify-center gap-2 text-xs text-ink-soft"
              style={{ opacity: subtleOpacity }}
            >
              <span>{links.length} link{links.length === 1 ? '' : 's'}</span>
              {user && (
                <>
                  <span className="h-0.5 w-0.5 rounded-full bg-ink-soft/50" />
                  <span>{totalClicks} click{totalClicks === 1 ? '' : 's'}</span>
                </>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="mx-auto max-w-2xl px-4 pb-16">
        <p className="mb-6 text-center text-sm text-ink-soft">
          {user ? 'Shorten a link and keep track of every click.' : 'Shorten a link — no account needed.'}
        </p>

        <form onSubmit={handleShorten}>
          <div className="flex gap-2 rounded-full bg-cream-card p-2 shadow-lg shadow-orange/5 ring-1 ring-ink/5 transition focus-within:ring-orange/25">
            <input
              type="url"
              required
              placeholder="https://example.com/very/long/path"
              value={newUrl}
              onChange={(e) => setNewUrl(e.target.value)}
              className="min-w-0 flex-1 rounded-full px-4 py-2 text-sm text-ink outline-none placeholder:text-ink-soft/60"
            />
            <button
              type="submit"
              disabled={shortening}
              className="shrink-0 rounded-full bg-orange px-5 py-2 text-sm font-medium text-white shadow-md shadow-orange/20 transition hover:bg-ink hover:shadow-lg disabled:opacity-50"
            >
              {shortening ? 'Shortening…' : 'Shorten'}
            </button>
          </div>
          <div className="mt-2 flex justify-end">
            <button
              type="button"
              onClick={() => setShowOptions((v) => !v)}
              className="text-xs font-medium text-ink-soft transition hover:text-orange"
            >
              {showOptions ? 'Hide options' : 'Customize'}
            </button>
          </div>

          {showOptions && (
            <div className="mt-1 grid animate-fade-in-up gap-4 rounded-2xl border border-ink/10 bg-cream-card p-4 shadow-lg shadow-orange/5 sm:grid-cols-2">
              {user ? (
                <div className="sm:col-span-2">
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                  Custom alias
                </label>
                <input
                  type="text"
                  value={alias}
                  onChange={(e) => setAlias(e.target.value)}
                  placeholder="my-brand"
                  pattern="[A-Za-z0-9_-]{3,32}"
                  title="3–32 characters: letters, numbers, hyphens or underscores"
                  className="w-full rounded-xl border border-ink/15 bg-cream px-3 py-2 text-sm text-ink outline-none transition focus:border-orange focus:ring-2 focus:ring-orange/20"
                />
                  <p className="mt-1 text-xs text-ink-soft/70">Leave blank for a random code.</p>
                </div>
              ) : (
                <div className="rounded-xl border border-orange/15 bg-orange-light/20 px-3 py-2 sm:col-span-2">
                  <p className="text-xs text-ink-soft">Custom aliases are available after logging in.</p>
                </div>
              )}
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                  Expires
                </label>
                <select
                  value={expiryPreset}
                  onChange={(e) => setExpiryPreset(e.target.value)}
                  className="w-full rounded-xl border border-ink/15 bg-cream px-3 py-2 text-sm text-ink outline-none transition focus:border-orange focus:ring-2 focus:ring-orange/20"
                >
                  {EXPIRY_PRESETS.map((p) => (
                    <option key={p.value} value={p.value}>
                      {p.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                  Max clicks
                </label>
                <input
                  type="number"
                  min={1}
                  value={maxClicks}
                  onChange={(e) => setMaxClicks(e.target.value)}
                  placeholder="Unlimited"
                  className="w-full rounded-xl border border-ink/15 bg-cream px-3 py-2 text-sm text-ink outline-none transition focus:border-orange focus:ring-2 focus:ring-orange/20"
                />
              </div>
            </div>
          )}

          {shortenError && <p className="mt-2 text-sm text-red-600">{shortenError}</p>}
        </form>

        {!user && (
          <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-orange/20 bg-orange-light/20 px-4 py-3 text-sm text-ink sm:flex-row sm:items-center sm:justify-between">
            <div className="text-center sm:text-left">
              <p className="font-medium">Sign up to unlock analytics and custom aliases</p>
              <p className="mt-0.5 text-xs text-ink-soft">
                Track clicks over time, referrers, countries, and devices. Claim memorable aliases
                for your links instead of random codes.
              </p>
            </div>
            <button
              onClick={onLoginClick}
              className="shrink-0 self-center rounded-full bg-orange px-4 py-1.5 text-xs font-medium text-white transition hover:bg-ink"
            >
              Sign up
            </button>
          </div>
        )}

        <div className="mb-3 mt-10 flex items-center justify-between">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-soft">My Links</h2>
          <div className="flex items-center gap-2">
            {user && links.length > 0 && (
              <button
                type="button"
                onClick={handleRefreshLinks}
                disabled={refreshingLinks}
                title={
                  refreshingLinks
                    ? 'Refreshing links'
                    : refreshStatus === 'success'
                      ? 'Links refreshed'
                      : refreshStatus === 'error'
                        ? 'Refresh failed'
                        : 'Refresh links'
                }
                aria-label={
                  refreshingLinks
                    ? 'Refreshing links'
                    : refreshStatus === 'success'
                      ? 'Links refreshed'
                      : refreshStatus === 'error'
                        ? 'Refresh failed'
                        : 'Refresh links'
                }
                className={`rounded-full border border-ink/15 p-1.5 transition hover:border-orange hover:text-orange disabled:opacity-70 ${
                  refreshStatus === 'error'
                    ? 'text-red-600'
                    : refreshStatus === 'success'
                      ? 'text-orange'
                      : 'text-ink-soft'
                }`}
              >
                {refreshingLinks ? (
                  <ArrowClockwise className="h-3.5 w-3.5 refresh-spin" />
                ) : refreshStatus === 'success' ? (
                  <Check className="h-3.5 w-3.5" />
                ) : refreshStatus === 'error' ? (
                  <WarningCircle className="h-3.5 w-3.5" />
                ) : (
                  <ArrowClockwise className="h-3.5 w-3.5" />
                )}
              </button>
            )}
            {links.length > 0 && (
              <span className="rounded-full bg-orange-light/50 px-2 py-0.5 text-xs font-medium text-orange">
                {links.length}
              </span>
            )}
          </div>
        </div>

        {loadingLinks && <p className="text-sm text-ink-soft">Loading your links…</p>}

        {!loadingLinks && links.length === 0 && (
          <p className="rounded-2xl border border-dashed border-ink/15 bg-cream-card py-10 text-center text-sm text-ink-soft">
            No links yet — shorten your first one above.
          </p>
        )}

        {!loadingLinks && links.length > 0 && (
          <ul className="divide-y divide-ink/8 overflow-hidden rounded-2xl border border-ink/10 bg-cream-card shadow-lg shadow-orange/5">
            {links.map((link, i) => {
              const expiryBadge = getExpiryBadge(link);
              return (
                <li
                  key={link.shortCode}
                  className="group flex animate-fade-in-up items-center justify-between gap-3 px-5 py-4 transition-colors hover:bg-orange-light/10 sm:gap-4"
                  style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                      <a
                        href={link.shortUrl}
                        target="_blank"
                        rel="noreferrer"
                        onClick={() => handleShortLinkOpen(link)}
                        className="min-w-0 truncate font-medium text-orange hover:underline"
                      >
                        {link.shortUrl.replace(/^https?:\/\//, '')}
                      </a>
                      {expiryBadge && (
                        <span
                          title={new Date(link.expiresAt!).toLocaleString()}
                          className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${
                            expiryBadge.expired
                              ? 'bg-red-50 text-red-600 ring-1 ring-red-100'
                              : 'bg-orange-light/45 text-orange ring-1 ring-orange/10'
                          }`}
                        >
                          {expiryBadge.label}
                        </span>
                      )}
                    </div>
                    <p className="truncate text-sm text-ink-soft" title={link.originalUrl}>
                      {link.originalUrl}
                    </p>
                  </div>
                  <div className="hidden shrink-0 text-right text-sm text-ink-soft sm:block">
                    {user && <div>{link.clickCount ?? 0} clicks</div>}
                    <div className="text-xs text-ink-soft/80">
                      {new Date(link.createdAt).toLocaleDateString()}
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center justify-end gap-1">
                    <button
                      onClick={() => handleCopy(link.shortUrl, link.shortCode)}
                      title={copiedCode === link.shortCode ? 'Copied' : 'Copy link'}
                      aria-label={copiedCode === link.shortCode ? 'Copied' : 'Copy link'}
                      className={`rounded-full border p-2 transition ${
                        copiedCode === link.shortCode
                          ? 'border-orange text-orange'
                          : 'border-ink/15 text-ink-soft hover:border-orange hover:text-orange'
                      }`}
                    >
                      {copiedCode === link.shortCode ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                    </button>
                    <button
                      onClick={() => setQrLink(link)}
                      title="Show QR code"
                      aria-label="Show QR code"
                      className="rounded-full border border-ink/15 p-2 text-ink-soft transition hover:border-orange hover:text-orange"
                    >
                      <QrCode className="h-4 w-4" />
                    </button>
                    {user && (
                      <button
                        onClick={() => setStatsLink(link)}
                        title="View stats"
                        aria-label="View stats"
                        className="rounded-full border border-ink/15 p-2 text-ink-soft transition hover:border-orange hover:text-orange"
                      >
                        <ChartBar className="h-4 w-4" />
                      </button>
                    )}
                    <button
                      onClick={() => (user ? handleDelete(link.shortCode) : handleRemoveAnonymous(link.shortCode))}
                      title={user ? 'Delete link' : 'Remove link'}
                      aria-label={user ? 'Delete link' : 'Remove link'}
                      className="rounded-full border border-transparent p-2 text-ink-soft transition hover:border-red-200 hover:text-red-600"
                    >
                      <Trash className="h-4 w-4" />
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}

        {statsLink && (
          <LinkStatsModal
            link={statsLink}
            onClose={() => setStatsLink(null)}
            onStatsLoaded={(totalClicks) => updateLinkClickCount(statsLink.shortCode, totalClicks)}
          />
        )}

        {qrLink && (
          <div
            className="fixed inset-0 z-30 flex items-center justify-center bg-ink/35 px-4 py-8 backdrop-blur-sm"
            onClick={() => setQrLink(null)}
          >
            <div
              className="w-full max-w-xs rounded-2xl border border-ink/10 bg-cream-card p-5 text-center shadow-2xl shadow-ink/20"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="mx-auto flex h-52 w-52 items-center justify-center rounded-xl bg-white p-3 ring-1 ring-ink/10">
                <img
                  src={api.getQrCodeUrl(qrLink)}
                  alt={`QR code for ${qrLink.shortUrl}`}
                  className="h-full w-full"
                />
              </div>
              <p className="mt-4 truncate text-sm font-medium text-orange" title={qrLink.shortUrl}>
                {qrLink.shortUrl.replace(/^https?:\/\//, '')}
              </p>
              <p className="mt-1 truncate text-xs text-ink-soft" title={qrLink.originalUrl}>
                {qrLink.originalUrl}
              </p>
              <div className="mt-5 flex gap-2">
                <a
                  href={api.getQrCodeUrl(qrLink)}
                  target="_blank"
                  rel="noreferrer"
                  download={`${qrLink.shortCode}-qr.png`}
                  className="flex-1 rounded-full bg-orange px-4 py-2 text-sm font-medium text-white transition hover:bg-ink"
                >
                  Download
                </a>
                <button
                  type="button"
                  onClick={() => setQrLink(null)}
                  className="flex-1 rounded-full border border-ink/15 px-4 py-2 text-sm text-ink-soft transition hover:border-orange hover:text-orange"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}

        {user && (
          <div className="mt-16 border-t border-ink/10 pt-6 text-center">
            {!showDeleteAccount ? (
              <button
                onClick={() => setShowDeleteAccount(true)}
                className="text-xs text-ink-soft/60 transition hover:text-red-600"
              >
                Delete account
              </button>
            ) : (
              <div className="mx-auto max-w-sm rounded-2xl border border-red-200 bg-red-50/50 p-5 text-left">
                <p className="text-sm font-medium text-red-700">Delete your account</p>
                <p className="mt-1 text-xs text-red-700/70">
                  This deactivates your account and signs you out everywhere. Your existing links will keep
                  working during the recovery window.
                </p>
                <form onSubmit={handleDeleteAccount} className="mt-3 space-y-2">
                  <input
                    type="password"
                    required
                    placeholder="Confirm your password"
                    value={deletePassword}
                    onChange={(e) => setDeletePassword(e.target.value)}
                    className="w-full rounded-lg border border-red-200 bg-white px-3 py-1.5 text-sm text-ink outline-none focus:border-red-400 focus:ring-2 focus:ring-red-100"
                  />
                  {deleteAccountError && <p className="text-xs text-red-600">{deleteAccountError}</p>}
                  <div className="flex gap-2 pt-1">
                    <button
                      type="submit"
                      disabled={deletingAccount}
                      className="rounded-full bg-red-600 px-4 py-1.5 text-xs font-medium text-white transition hover:bg-red-700 disabled:opacity-50"
                    >
                      {deletingAccount ? 'Deleting…' : 'Delete my account'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setShowDeleteAccount(false);
                        setDeletePassword('');
                        setDeleteAccountError('');
                      }}
                      className="rounded-full px-4 py-1.5 text-xs text-ink-soft transition hover:text-ink"
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
