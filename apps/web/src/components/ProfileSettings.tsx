import { useState, type ReactElement } from 'react';
import type { UserProfile, StudentStatus } from '../lib/profile';
import { STUDENT_STATUSES } from '../lib/profile';

type ProfileSettingsProps = Readonly<{
  profile: UserProfile;
  onProfileChange: (profile: UserProfile) => void;
}>;

const YEARS = [1, 2, 3, 4] as const;

export function ProfileSettings({ profile, onProfileChange }: ProfileSettingsProps): ReactElement {
  const [expanded, setExpanded] = useState<boolean>(false);

  function handleDepartmentChange(value: string): void {
    onProfileChange({ ...profile, department: value.length > 0 ? value : null });
  }

  function handleYearToggle(year: number): void {
    onProfileChange({ ...profile, year: profile.year === year ? null : year });
  }

  function handleStatusChange(value: string): void {
    onProfileChange({
      ...profile,
      status: value.length > 0 ? (value as StudentStatus) : null,
    });
  }

  return (
    <div className="profile-settings">
      <button
        className="profile-settings-header"
        onClick={() => { setExpanded((prev) => !prev); }}
      >
        <span>내 프로필</span>
        <span>{expanded ? '\u25B2' : '\u25BC'}</span>
      </button>

      {expanded ? (
        <div className="profile-settings-body">
          <div className="profile-field">
            <label>학과</label>
            <input
              type="text"
              placeholder="예: 컴퓨터공학과"
              value={profile.department ?? ''}
              onChange={(e) => { handleDepartmentChange(e.target.value); }}
            />
          </div>

          <div className="profile-field">
            <label>학년</label>
            <div className="year-group">
              {YEARS.map((y) => (
                <button
                  key={y}
                  className={`year-btn${profile.year === y ? ' year-btn-active' : ''}`}
                  onClick={() => { handleYearToggle(y); }}
                >
                  {y}
                </button>
              ))}
            </div>
          </div>

          <div className="profile-field">
            <label>신분</label>
            <select
              value={profile.status ?? ''}
              onChange={(e) => { handleStatusChange(e.target.value); }}
            >
              <option value="">선택 안 함</option>
              {STUDENT_STATUSES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>
      ) : null}
    </div>
  );
}
