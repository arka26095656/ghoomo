import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { CloudRain, GripVertical, Sun, Trash2, Cloud } from 'lucide-react';
import clsx from 'clsx';
import type { Activity, DayPlan, DayWeather, ItineraryPlan } from '../types';
import { dayColor, inr, shortDate, weatherLabel } from '../lib/format';

interface Props {
  plan: ItineraryPlan;
  budgetInr?: number | null;
  editable?: boolean;
  onChange?: (plan: ItineraryPlan) => void;
  focusedDay?: number | null;
  onFocusDay?: (day: number | null) => void;
}

const CATEGORY_LABEL: Record<string, string> = {
  sight: 'Sight',
  food: 'Food',
  nature: 'Nature',
  culture: 'Culture',
  shopping: 'Shopping',
  nightlife: 'Evening',
  adventure: 'Adventure',
  transport: 'Getting around',
};

export function RouteItinerary({ plan, budgetInr, editable, onChange, focusedDay, onFocusDay }: Props) {
  const total = plan.estimatedTotalInr;
  const over = budgetInr != null && total != null && total > budgetInr;

  const updateDay = (index: number, day: DayPlan) => {
    if (!onChange) return;
    const days = plan.days.slice();
    days[index] = day;
    const estimatedTotalInr = days.reduce(
      (sum, d) => sum + d.activities.reduce((s, a) => s + (a.estCostInr ?? 0), 0),
      0,
    );
    onChange({ ...plan, days, estimatedTotalInr });
  };

  return (
    <div className="pb-8">
      <header className="mb-6">
        <h2 className="text-[1.65rem] font-semibold leading-tight">{plan.title}</h2>
        {plan.summary && <p className="mt-2 max-w-[62ch] text-muted">{plan.summary}</p>}
        <dl className="mt-4 flex flex-wrap gap-x-8 gap-y-2 text-sm">
          <div>
            <dt className="text-muted">Days</dt>
            <dd className="font-display text-xl font-semibold">{plan.days.length}</dd>
          </div>
          <div>
            <dt className="text-muted">Activities & food</dt>
            <dd className="font-display text-xl font-semibold">{inr(total)}</dd>
          </div>
          {budgetInr != null && (
            <div>
              <dt className="text-muted">Your budget</dt>
              <dd className={clsx('font-display text-xl font-semibold', over ? 'text-danger' : 'text-leaf')}>
                {inr(budgetInr)}
              </dd>
            </div>
          )}
        </dl>
        {budgetInr != null && total != null && (
          <p className="mt-2 text-sm text-muted">
            {over
              ? `About ${inr(total - budgetInr)} over budget before travel and stay.`
              : `Leaves about ${inr(budgetInr - total)} for travel and stay.`}
          </p>
        )}
      </header>

      <ol className="space-y-7">
        {plan.days.map((day, i) => (
          <DaySection
            key={day.day}
            day={day}
            weather={plan.weather?.[i] ?? null}
            editable={editable}
            focused={focusedDay == null || focusedDay === day.day}
            onFocus={() => onFocusDay?.(focusedDay === day.day ? null : day.day)}
            onChange={(d) => updateDay(i, d)}
          />
        ))}
      </ol>

      {plan.tips && plan.tips.length > 0 && (
        <section className="mt-9 border-t border-line pt-5">
          <h3 className="text-lg font-semibold">Good to know</h3>
          <ul className="mt-2 list-disc space-y-1.5 pl-5 text-[0.95rem] text-ink/90">
            {plan.tips.map((t, i) => (
              <li key={i}>{t}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function DaySection({
  day,
  weather,
  editable,
  focused,
  onFocus,
  onChange,
}: {
  day: DayPlan;
  weather: DayWeather | null;
  editable?: boolean;
  focused: boolean;
  onFocus: () => void;
  onChange: (d: DayPlan) => void;
}) {
  const color = dayColor(day.day);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const ids = day.activities.map((_, i) => `${day.day}-${i}`);

  const onDragEnd = (e: DragEndEvent) => {
    if (!e.over || e.active.id === e.over.id) return;
    const from = ids.indexOf(String(e.active.id));
    const to = ids.indexOf(String(e.over.id));
    onChange({ ...day, activities: arrayMove(day.activities, from, to) });
  };

  const remove = (index: number) =>
    onChange({ ...day, activities: day.activities.filter((_, i) => i !== index) });

  return (
    <li className={clsx('transition-opacity', !focused && 'opacity-45')}>
      <button
        type="button"
        onClick={onFocus}
        className="group flex w-full items-baseline gap-3 text-left"
        aria-label={`Show day ${day.day} on the map`}
      >
        <span className="font-display text-4xl font-bold leading-none" style={{ color }}>
          {day.day}
        </span>
        <span className="flex-1">
          <span className="block font-display text-lg font-semibold leading-snug group-hover:underline">
            {day.theme || `Day ${day.day}`}
          </span>
          {weather && <WeatherLine w={weather} />}
        </span>
      </button>

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={ids} strategy={verticalListSortingStrategy}>
          <ol className="relative ml-[0.95rem] mt-3 border-l-2 pl-6" style={{ borderColor: color }}>
            {day.activities.map((a, i) => (
              <ActivityRow
                key={ids[i]}
                id={ids[i]}
                activity={a}
                color={color}
                editable={editable}
                onRemove={() => remove(i)}
              />
            ))}
            {day.activities.length === 0 && (
              <li className="py-2 text-sm text-muted">No activities left on this day.</li>
            )}
          </ol>
        </SortableContext>
      </DndContext>
    </li>
  );
}

function ActivityRow({
  id,
  activity: a,
  color,
  editable,
  onRemove,
}: {
  id: string;
  activity: Activity;
  color: string;
  editable?: boolean;
  onRemove: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
    disabled: !editable,
  });

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={clsx('relative py-2.5', isDragging && 'z-10')}
    >
      {/* stop marker on the route line */}
      <span
        className="absolute -left-[2.05rem] top-[1.05rem] h-3.5 w-3.5 rounded-full border-[3px] bg-surface"
        style={{ borderColor: color }}
        aria-hidden="true"
      />
      <div
        className={clsx(
          'group flex gap-3 rounded-xl px-3 py-2.5',
          isDragging ? 'bg-surface shadow-lg ring-1 ring-line' : 'hover:bg-surface',
        )}
      >
        <span className="w-11 shrink-0 pt-0.5 font-display text-sm font-semibold tabular-nums text-muted">
          {a.time ?? ''}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-baseline gap-x-2">
            <span className="font-semibold">{a.name}</span>
            {a.category && (
              <span className="text-xs text-muted">{CATEGORY_LABEL[a.category] ?? a.category}</span>
            )}
          </div>
          {a.description && <p className="mt-0.5 text-sm text-muted">{a.description}</p>}
        </div>
        <span className="shrink-0 pt-0.5 text-sm tabular-nums text-ink/80">
          {a.estCostInr ? inr(a.estCostInr) : a.estCostInr === 0 ? 'Free' : ''}
        </span>
        {editable && (
          <span className="flex shrink-0 items-start gap-0.5 opacity-60 group-hover:opacity-100">
            <button
              type="button"
              className="cursor-grab rounded-md p-1 text-muted hover:bg-sunk active:cursor-grabbing"
              aria-label={`Drag to reorder ${a.name}`}
              {...attributes}
              {...listeners}
            >
              <GripVertical size={16} />
            </button>
            <button
              type="button"
              onClick={onRemove}
              className="rounded-md p-1 text-muted hover:bg-sunk hover:text-danger"
              aria-label={`Remove ${a.name}`}
            >
              <Trash2 size={15} />
            </button>
          </span>
        )}
      </div>
    </li>
  );
}

function WeatherLine({ w }: { w: DayWeather }) {
  const rainy = (w.rainChance ?? 0) >= 50 || (w.code != null && w.code >= 51);
  const Icon = rainy ? CloudRain : w.code != null && w.code <= 1 ? Sun : Cloud;
  return (
    <span className="mt-0.5 inline-flex items-center gap-1.5 text-sm text-muted">
      <Icon size={14} aria-hidden="true" />
      {shortDate(w.date)} · {weatherLabel(w.code)}
      {w.maxC != null && `, ${Math.round(w.maxC)}° / ${Math.round(w.minC ?? w.maxC)}°`}
      {w.rainChance != null && w.rainChance > 0 && `, ${w.rainChance}% rain`}
    </span>
  );
}
