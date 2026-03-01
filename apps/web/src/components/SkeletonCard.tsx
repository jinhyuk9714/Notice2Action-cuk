import type { ReactElement } from 'react';

type SkeletonCardProps = Readonly<{
  lines?: number;
}>;

export function SkeletonCard({ lines = 3 }: SkeletonCardProps): ReactElement {
  return (
    <div className="skeleton-card" aria-hidden="true">
      <div className="skeleton-line skeleton-line-short" />
      {Array.from({ length: lines }, (_, i) => (
        <div
          key={i}
          className={`skeleton-line${i === lines - 1 ? ' skeleton-line-medium' : ''}`}
        />
      ))}
    </div>
  );
}
