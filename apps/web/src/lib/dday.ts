export type DdayUrgency = 'overdue' | 'imminent' | 'upcoming' | 'normal';

export type DdayInfo = Readonly<{
  label: string;
  daysLeft: number;
  urgency: DdayUrgency;
}>;

export function computeDday(dueAtIso: string | null, now?: Date): DdayInfo | null {
  if (dueAtIso === null) return null;

  const today = now ?? new Date();
  const due = new Date(dueAtIso);

  if (isNaN(due.getTime())) return null;

  const todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const dueDate = new Date(due.getFullYear(), due.getMonth(), due.getDate());

  const diffMs = dueDate.getTime() - todayDate.getTime();
  const daysLeft = Math.round(diffMs / (1000 * 60 * 60 * 24));

  let label: string;
  if (daysLeft === 0) label = 'D-Day';
  else if (daysLeft > 0) label = `D-${daysLeft}`;
  else label = `D+${Math.abs(daysLeft)}`;

  let urgency: DdayUrgency;
  if (daysLeft < 0) urgency = 'overdue';
  else if (daysLeft <= 1) urgency = 'imminent';
  else if (daysLeft <= 3) urgency = 'upcoming';
  else urgency = 'normal';

  return { label, daysLeft, urgency };
}
