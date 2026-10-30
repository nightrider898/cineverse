"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { catalogApi, userApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import type { City } from "@/types/api";

const STORAGE_KEY = "cv.city";
const DEFAULT_CITY = "hyderabad";

interface CityContextValue {
  city: City | null;
  citySlug: string;
  cities: City[];
  setCity: (c: City) => void;
  pickerOpen: boolean;
  openPicker: () => void;
  closePicker: () => void;
  detect: () => Promise<City | null>;
}

const CityContext = createContext<CityContextValue | null>(null);

function readStored(): string | null {
  try { return window.localStorage.getItem(STORAGE_KEY); } catch { return null; }
}

export function CityProvider({ children }: { children: ReactNode }) {
  const { user, status } = useAuth();
  const [slug, setSlug] = useState<string>(DEFAULT_CITY);
  const [hydrated, setHydrated] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const { data: cities = [] } = useQuery({ queryKey: ["cities"], queryFn: catalogApi.cities, staleTime: 3_600_000 });

  useEffect(() => {
    const stored = readStored();
    if (stored) setSlug(stored);
    else setPickerOpen(true); // first visit: ask where they are
    setHydrated(true);
  }, []);

  // A signed-in user's saved city wins over the browser's.
  useEffect(() => {
    if (status === "authenticated" && user?.preferredCity?.slug) {
      setSlug(user.preferredCity.slug);
      try { window.localStorage.setItem(STORAGE_KEY, user.preferredCity.slug); } catch { /* ignore */ }
    }
  }, [status, user?.preferredCity?.slug]);

  const setCity = useCallback((c: City) => {
    setSlug(c.slug);
    setPickerOpen(false);
    try { window.localStorage.setItem(STORAGE_KEY, c.slug); } catch { /* ignore */ }
    if (status === "authenticated") userApi.setCity(c.id).catch(() => undefined);
  }, [status]);

  const detect = useCallback(() => new Promise<City | null>((resolve) => {
    if (!("geolocation" in navigator)) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try { resolve(await catalogApi.nearestCity(pos.coords.latitude, pos.coords.longitude)); } catch { resolve(null); }
      },
      () => resolve(null),
      { timeout: 8000, maximumAge: 600_000 },
    );
  }), []);

  const value = useMemo<CityContextValue>(() => ({
    city: cities.find((c) => c.slug === slug) ?? null,
    citySlug: hydrated ? slug : DEFAULT_CITY,
    cities,
    setCity,
    pickerOpen,
    openPicker: () => setPickerOpen(true),
    closePicker: () => setPickerOpen(false),
    detect,
  }), [cities, slug, hydrated, setCity, pickerOpen, detect]);

  return <CityContext.Provider value={value}>{children}</CityContext.Provider>;
}

export function useCity() {
  const ctx = useContext(CityContext);
  if (!ctx) throw new Error("useCity must be used inside CityProvider");
  return ctx;
}
