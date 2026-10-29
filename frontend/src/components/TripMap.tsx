import { useEffect, useMemo } from 'react';
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { ItineraryPlan } from '../types';
import { dayColor, inr } from '../lib/format';

interface Pin {
  key: string;
  day: number;
  order: number;
  name: string;
  time: string | null;
  cost: number | null;
  pos: [number, number];
}

const iconCache = new Map<string, L.DivIcon>();
function pinIcon(color: string, label: string, faded: boolean) {
  const key = `${color}-${label}-${faded}`;
  let icon = iconCache.get(key);
  if (!icon) {
    icon = L.divIcon({
      className: '',
      html: `<div class="ghoomo-pin" style="background:${color};opacity:${faded ? 0.35 : 1}"><span>${label}</span></div>`,
      iconSize: [28, 28],
      iconAnchor: [14, 28],
      popupAnchor: [0, -26],
    });
    iconCache.set(key, icon);
  }
  return icon;
}

function FitBounds({ points, fallback }: { points: [number, number][]; fallback: [number, number] | null }) {
  const map = useMap();
  useEffect(() => {
    const fit = () => {
      if (points.length > 1) map.fitBounds(L.latLngBounds(points), { padding: [36, 36], maxZoom: 14 });
      else if (points.length === 1) map.setView(points[0], 13);
      else if (fallback) map.setView(fallback, 11);
    };
    fit();
    // The map may mount while hidden (mobile tab, collapsed panel): resize and refit once it has a size.
    const ro = new ResizeObserver(() => {
      map.invalidateSize();
      fit();
    });
    ro.observe(map.getContainer());
    return () => ro.disconnect();
  }, [map, points, fallback]);
  return null;
}

export function TripMap({
  plan,
  focusedDay,
  className = '',
}: {
  plan: ItineraryPlan;
  focusedDay?: number | null;
  className?: string;
}) {
  const pins = useMemo<Pin[]>(() => {
    const out: Pin[] = [];
    for (const d of plan.days) {
      d.activities.forEach((a, i) => {
        if (a.lat != null && a.lng != null) {
          out.push({ key: `${d.day}-${i}-${a.name}`, day: d.day, order: i + 1, name: a.name, time: a.time, cost: a.estCostInr, pos: [a.lat, a.lng] });
        }
      });
    }
    return out;
  }, [plan]);

  const boundsPoints = useMemo(
    () => (focusedDay == null ? pins : pins.filter((p) => p.day === focusedDay)).map((p) => p.pos),
    [pins, focusedDay],
  );
  const center = useMemo<[number, number] | null>(
    () => (plan.centerLat != null && plan.centerLng != null ? [plan.centerLat, plan.centerLng] : null),
    [plan.centerLat, plan.centerLng],
  );

  const lines = useMemo(() => {
    const byDay = new Map<number, [number, number][]>();
    pins.forEach((p) => byDay.set(p.day, [...(byDay.get(p.day) ?? []), p.pos]));
    return [...byDay.entries()];
  }, [pins]);

  return (
    <div className={`relative isolate overflow-hidden rounded-card border border-line ${className}`}>
      <MapContainer
        center={center ?? [22.5, 79]}
        zoom={center ? 11 : 4}
        scrollWheelZoom={false}
        className="h-full w-full"
        attributionControl
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {lines.map(([day, pts]) => (
          <Polyline
            key={day}
            positions={pts}
            pathOptions={{
              color: dayColor(day),
              weight: 3,
              dashArray: '6 6',
              opacity: focusedDay == null || focusedDay === day ? 0.9 : 0.15,
            }}
          />
        ))}
        {pins.map((p) => (
          <Marker
            key={p.key}
            position={p.pos}
            icon={pinIcon(dayColor(p.day), `${p.day}.${p.order}`, focusedDay != null && focusedDay !== p.day)}
          >
            <Popup>
              <strong>{p.name}</strong>
              <br />
              Day {p.day}
              {p.time ? `, ${p.time}` : ''}
              {p.cost ? ` · ${inr(p.cost)}` : ''}
            </Popup>
          </Marker>
        ))}
        <FitBounds points={boundsPoints} fallback={center} />
      </MapContainer>
      {pins.length === 0 && (
        <p className="pointer-events-none absolute inset-x-0 bottom-3 mx-auto w-fit rounded-full bg-surface/90 px-3 py-1 text-sm text-muted">
          No map locations for this plan yet.
        </p>
      )}
    </div>
  );
}
