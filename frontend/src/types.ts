// Mirrors the Spring Boot DTOs.

export interface Profile {
  id: number;
  name: string;
  email: string;
  homeCity: string | null;
  travelStyle: 'budget' | 'comfort' | 'luxury' | null;
  budgetInr: number | null;
  interests: string[];
}

export interface AuthResponse {
  token: string;
  user: Profile;
}

export type Category =
  | 'sight'
  | 'food'
  | 'nature'
  | 'culture'
  | 'shopping'
  | 'nightlife'
  | 'adventure'
  | 'transport'
  | string;

export interface Activity {
  time: string | null;
  name: string;
  description: string | null;
  category: Category | null;
  lat: number | null;
  lng: number | null;
  estCostInr: number | null;
}

export interface DayPlan {
  day: number;
  theme: string | null;
  activities: Activity[];
}

export interface DayWeather {
  date: string;
  maxC: number | null;
  minC: number | null;
  rainChance: number | null;
  code: number | null;
}

export interface ItineraryPlan {
  title: string;
  destination: string;
  summary: string | null;
  centerLat: number | null;
  centerLng: number | null;
  days: DayPlan[];
  estimatedTotalInr: number | null;
  tips: string[] | null;
  weather: DayWeather[] | null;
}

export interface ItinerarySummary {
  id: number;
  title: string;
  destination: string;
  startDate: string | null;
  days: number;
  budgetInr: number | null;
  estimatedInr: number | null;
  updatedAt: string;
}

export interface ItineraryDetail extends ItinerarySummary {
  createdAt: string;
  plan: ItineraryPlan;
  /** Colour theme picked by the backend from the destination: 'rajasthan', 'kerala', … or 'default'. */
  themeKey?: string | null;
}

export interface ChatSession {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number | string;
  role: 'user' | 'assistant';
  content: string;
  itineraryId: number | null;
  createdAt?: string;
  pending?: boolean;
  provider?: string;
  error?: boolean;
}
