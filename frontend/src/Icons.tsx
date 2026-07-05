type IconProps = { className?: string };

export function EyeIcon({ off, className = 'h-4 w-4' }: IconProps & { off: boolean }) {
  if (off) {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" className={className}>
        <path d="M3 3l18 18" />
        <path d="M10.6 5.1A10.6 10.6 0 0 1 12 5c5.5 0 9.5 4 11 7-.6 1.2-1.6 2.6-2.9 3.8M6.6 6.6C4.3 8 2.7 10 2 12c1.5 3 5.5 7 10 7 1.4 0 2.7-.3 3.9-.9" />
        <path d="M9.9 10a3 3 0 0 0 4.2 4.2" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}
