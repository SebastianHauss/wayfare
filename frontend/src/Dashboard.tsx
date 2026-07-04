import { useEffect, useState } from 'react';
import * as api from './api';
import type { LinkResponse, MeResponse } from './types';

const SHRINK_DISTANCE = 200;
const TITLE_MAX = 64;
const TITLE_MIN = 22;
const PAD_MAX = 32;
const PAD_MIN = 12;

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
  const [newUrl, setNewUrl] = useState('');
  const [shortening, setShortening] = useState(false);
  const [shortenError, setShortenError] = useState('');
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [qrLink, setQrLink] = useState<LinkResponse | null>(null);
  const [scrollY, setScrollY] = useState(0);
  const [showDeleteAccount, setShowDeleteAccount] = useState(false);
  const [deletingAccount, setDeletingAccount] = useState(false);
  const [deleteAccountError, setDeleteAccountError] = useState('');

  useEffect(() => {
    loadLinks();
  }, []);

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

  async function loadLinks() {
    if (!user) {
      setLinks(api.getAnonymousLinks());
      setLoadingLinks(false);
      return;
    }
    setLoadingLinks(true);
    try {
      setLinks(await api.getMyLinks());
    } finally {
      setLoadingLinks(false);
    }
  }

  async function handleShorten(e: React.FormEvent) {
    e.preventDefault();
    setShortening(true);
    setShortenError('');
    try {
      const created = await api.shorten(newUrl);
      setNewUrl('');
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

  async function handleDeleteAccount(e: React.FormEvent) {
    e.preventDefault();
    setDeletingAccount(true);
    setDeleteAccountError('');
    try {
      await api.deleteAccount();
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
          {shortenError && <p className="mt-2 text-sm text-red-600">{shortenError}</p>}
        </form>

        <div className="mb-3 mt-10 flex items-center justify-between">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-soft">My Links</h2>
          {links.length > 0 && (
            <span className="rounded-full bg-orange-light/50 px-2 py-0.5 text-xs font-medium text-orange">
              {links.length}
            </span>
          )}
        </div>

        {loadingLinks && <p className="text-sm text-ink-soft">Loading your links…</p>}

        {!loadingLinks && links.length === 0 && (
          <p className="rounded-2xl border border-dashed border-ink/15 bg-cream-card py-10 text-center text-sm text-ink-soft">
            No links yet — shorten your first one above.
          </p>
        )}

        {!loadingLinks && !user && links.length > 0 && (
          <div className="mb-4 flex flex-col items-center justify-between gap-3 rounded-2xl border border-orange/20 bg-orange-light/20 px-4 py-3 text-center text-sm text-ink sm:flex-row sm:text-left">
            <span>Sign up to track clicks and manage your links from anywhere.</span>
            <button
              onClick={onLoginClick}
              className="shrink-0 rounded-full bg-orange px-4 py-1.5 text-xs font-medium text-white transition hover:bg-ink"
            >
              Sign up
            </button>
          </div>
        )}

        {!loadingLinks && links.length > 0 && (
          <ul className="divide-y divide-ink/8 overflow-hidden rounded-2xl border border-ink/10 bg-cream-card shadow-lg shadow-orange/5">
            {links.map((link, i) => (
              <li
                key={link.shortCode}
                className="group flex animate-fade-in-up items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-orange-light/10"
                style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}
              >
                <div className="min-w-0 flex-1">
                  <a
                    href={link.shortUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="font-medium text-orange hover:underline"
                  >
                    {link.shortUrl.replace(/^https?:\/\//, '')}
                  </a>
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
                <div className="flex shrink-0 flex-wrap items-center justify-end gap-2 text-sm">
                  <button
                    onClick={() => handleCopy(link.shortUrl, link.shortCode)}
                    className="rounded-full border border-ink/15 px-3 py-1 text-ink-soft transition hover:border-orange hover:text-orange"
                  >
                    {copiedCode === link.shortCode ? 'Copied' : 'Copy'}
                  </button>
                  <button
                    onClick={() => setQrLink(link)}
                    className="rounded-full border border-ink/15 px-3 py-1 text-ink-soft transition hover:border-orange hover:text-orange"
                  >
                    QR
                  </button>
                  {user ? (
                    <button
                      onClick={() => handleDelete(link.shortCode)}
                      className="rounded-full px-3 py-1 text-ink-soft transition hover:text-red-600"
                    >
                      Delete
                    </button>
                  ) : (
                    <button
                      onClick={() => handleRemoveAnonymous(link.shortCode)}
                      className="rounded-full px-3 py-1 text-ink-soft transition hover:text-red-600"
                    >
                      Remove
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
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
