export function Logo({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight ${className}`}>
      <svg viewBox="0 0 32 32" className="h-7 w-7" aria-hidden="true">
        <rect width="32" height="32" rx="8" className="fill-ink" />
        <path
          d="M9 23c0-6 14-4 14-11a5 5 0 0 0-10 0"
          fill="none"
          className="stroke-marigold"
          strokeWidth="3"
          strokeLinecap="round"
        />
        <circle cx="9" cy="23" r="2.6" className="fill-marigold" />
      </svg>
      Ghoomo
    </span>
  );
}
