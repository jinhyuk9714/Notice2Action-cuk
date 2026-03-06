import type { ReactElement } from 'react';
import type { UserProfile, StudentStatus } from '../lib/profile';
import { STUDENT_STATUSES } from '../lib/profile';

type ProfileSettingsProps = Readonly<{
  profile: UserProfile;
  onProfileChange: (profile: UserProfile) => void;
}>;

const YEARS = [1, 2, 3, 4] as const;

export function ProfileSettings({ profile, onProfileChange }: ProfileSettingsProps): ReactElement {
  function handleDepartmentChange(value: string): void {
    onProfileChange({ ...profile, department: value.trim().length > 0 ? value.trim() : null });
  }

  function handleKeywordChange(value: string): void {
    onProfileChange({
      ...profile,
      interestKeywords: value.split(',').map((keyword) => keyword.trim()).filter((keyword) => keyword.length > 0),
    });
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
    <section className="panel profile-settings">
      <div className="panel-header">
        <div>
          <p className="eyebrow">프로필</p>
          <h2>개인화 기준</h2>
        </div>
      </div>

      <div className="profile-settings-body">
        <div className="profile-field">
          <label htmlFor="profileDepartment">학과</label>
          <input
            id="profileDepartment"
            type="text"
            placeholder="예: 컴퓨터공학과"
            value={profile.department ?? ''}
            onChange={(e) => { handleDepartmentChange(e.target.value); }}
          />
        </div>

        <div className="profile-field">
          <label id="yearLabel">학년</label>
          <div className="year-group" role="group" aria-labelledby="yearLabel">
            {YEARS.map((year) => (
              <button
                key={year}
                className={`year-btn${profile.year === year ? ' year-btn-active' : ''}`}
                onClick={() => { handleYearToggle(year); }}
                aria-pressed={profile.year === year}
              >
                {year}
              </button>
            ))}
          </div>
        </div>

        <div className="profile-field">
          <label htmlFor="profileStatus">신분</label>
          <select id="profileStatus" value={profile.status ?? ''} onChange={(e) => { handleStatusChange(e.target.value); }}>
            <option value="">선택 안 함</option>
            {STUDENT_STATUSES.map((status) => (
              <option key={status} value={status}>{status}</option>
            ))}
          </select>
        </div>

        <div className="profile-field">
          <label htmlFor="profileKeywords">관심 키워드</label>
          <input
            id="profileKeywords"
            type="text"
            placeholder="예: 장학금, 학생증, 수강신청"
            value={(profile.interestKeywords ?? []).join(', ')}
            onChange={(e) => { handleKeywordChange(e.target.value); }}
          />
        </div>
      </div>
    </section>
  );
}
