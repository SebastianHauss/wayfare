import { useEffect, useState } from 'react';
import * as api from './api';
import type { DailyCount, LinkResponse, LinkStats, StatBucket } from './types';

const WINDOW_DAYS = 30;

// The API only returns days that actually had clicks. Zero-fill the whole window
// so the chart renders as a continuous timeline (mostly flat, spikes on active
// days) instead of one stretched bar. Day keys are UTC to match the backend's
// date_trunc grouping.
function buildDailySeries(clicksByDay: DailyCount[]): DailyCount[] {
  const counts = new Map(clicksByDay.map((d) => [d.day, d.count]));
  const now = new Date();
  const series: DailyCount[] = [];
  for (let i = WINDOW_DAYS - 1; i >= 0; i--) {
    const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - i));
    const day = d.toISOString().slice(0, 10);
    series.push({ day, count: counts.get(day) ?? 0 });
  }
  return series;
}

function formatDay(day: string): string {
  return new Date(`${day}T00:00:00Z`).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

function BarList({ title, buckets, empty }: { title: string; buckets: StatBucket[]; empty: string }) {
  const max = buckets.reduce((m, b) => Math.max(m, b.count), 0) || 1;
  return (
    <div>
      <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-soft">{title}</h4>
      {buckets.length === 0 ? (
        <p className="text-xs text-ink-soft/70">{empty}</p>
      ) : (
        <ul className="space-y-1.5">
          {buckets.map((b) => (
            <li key={b.label} className="text-sm">
              <div className="mb-0.5 flex items-center justify-between gap-2">
                <span className="truncate text-ink" title={b.label}>
                  {b.label}
                </span>
                <span className="shrink-0 text-xs text-ink-soft">{b.count}</span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-ink/5">
                <div
                  className="h-full rounded-full bg-orange"
                  style={{ width: `${(b.count / max) * 100}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function LinkStatsModal({
  link,
  onClose,
  onStatsLoaded,
}: {
  link: LinkResponse;
  onClose: () => void;
  onStatsLoaded?: (totalClicks: number) => void;
}) {
  const [stats, setStats] = useState<LinkStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api
      .getLinkStats(link.shortCode)
      .then((s) => {
        if (!active) return;
        setStats(s);
        onStatsLoaded?.(s.totalClicks);
      })
      .catch((e) => active && setError(e instanceof Error ? e.message : 'Failed to load stats'))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [link.shortCode]);

  const series = stats ? buildDailySeries(stats.clicksByDay) : [];
  const maxDay = series.reduce((m, d) => Math.max(m, d.count), 0) || 1;

  return (
    <div
      className="fixed inset-0 z-30 flex items-center justify-center bg-ink/35 px-4 py-8 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="max-h-[85vh] w-full max-w-md overflow-y-auto rounded-2xl border border-ink/10 bg-cream-card p-5 shadow-2xl shadow-ink/20"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-orange" title={link.shortUrl}>
              {link.shortUrl.replace(/^https?:\/\//, '')}
            </p>
            <p className="truncate text-xs text-ink-soft" title={link.originalUrl}>
              {link.originalUrl}
            </p>
          </div>
          <button
            onClick={onClose}
            className="shrink-0 rounded-full border border-ink/15 px-3 py-1 text-sm text-ink-soft transition hover:border-orange hover:text-orange"
          >
            Close
          </button>
        </div>

        {loading && <p className="mt-6 text-sm text-ink-soft">Loading stats…</p>}
        {error && <p className="mt-6 text-sm text-red-600">{error}</p>}

        {stats && (
          <div className="mt-5 space-y-6">
            <div className="rounded-xl bg-orange-light/20 px-4 py-3">
              <div className="font-display text-2xl font-bold text-ink">{stats.totalClicks}</div>
              <div className="text-xs text-ink-soft">total clicks</div>
            </div>

            <div>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-soft">
                Last 30 days
              </h4>
              <div className="flex h-24 items-end gap-px">
                {series.map((d) => (
                  <div
                    key={d.day}
                    className={`flex-1 rounded-t ${d.count > 0 ? 'bg-orange' : 'bg-ink/10'}`}
                    style={{ height: d.count > 0 ? `${Math.max((d.count / maxDay) * 100, 8)}%` : '3px' }}
                    title={`${formatDay(d.day)}: ${d.count} click${d.count === 1 ? '' : 's'}`}
                  />
                ))}
              </div>
              <div className="mt-1 flex justify-between text-[10px] text-ink-soft/70">
                <span>{formatDay(series[0].day)}</span>
                <span>{formatDay(series[series.length - 1].day)}</span>
              </div>
            </div>

            <BarList title="Top referrers" buckets={stats.topReferrers} empty="No referrer data yet." />
            <BarList title="Top countries" buckets={stats.topCountries} empty="No country data yet." />
            <BarList title="Devices" buckets={stats.devices} empty="No device data yet." />
          </div>
        )}
      </div>
    </div>
  );
}
