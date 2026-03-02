import type { ReactElement } from 'react';
import type { FieldOverrideInfo } from '../lib/types';

type OverrideBadgeProps = Readonly<{
  overrides: readonly FieldOverrideInfo[];
  fieldName: string;
  reverting: string | null;
  onRevert: (fieldName: string) => void;
  showOriginalTitle?: boolean;
}>;

export function OverrideBadge({
  overrides,
  fieldName,
  reverting,
  onRevert,
  showOriginalTitle = false,
}: OverrideBadgeProps): ReactElement | null {
  const override = overrides.find((o) => o.fieldName === fieldName);
  if (override === undefined) return null;

  return (
    <span className="override-badge">
      <span className="override-label">수정됨</span>
      <button
        className="override-revert-btn"
        onClick={() => { onRevert(fieldName); }}
        disabled={reverting === fieldName}
        title={showOriginalTitle ? `원래 값: ${override.machineValue ?? ''}` : undefined}
      >
        되돌리기
      </button>
    </span>
  );
}
