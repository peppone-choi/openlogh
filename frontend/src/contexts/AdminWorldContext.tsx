"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useSearchParams } from "next/navigation";
import { adminApi } from "@/lib/gameApi";
import type { AdminWorldListEntry } from "@/types";

interface AdminWorldContextValue {
  worlds: AdminWorldListEntry[];
  worldId: number | undefined;
  setWorldId: (id: number) => void;
  refreshWorlds: () => Promise<void>;
  loading: boolean;
}

const AdminWorldContext = createContext<AdminWorldContextValue | null>(null);

export function AdminWorldProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const searchParams = useSearchParams();
  const [worlds, setWorlds] = useState<AdminWorldListEntry[]>([]);
  const [worldId, setWorldId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [initializedFromUrl, setInitializedFromUrl] = useState(false);

  const refreshWorlds = useCallback(async () => {
    try {
      const res = await adminApi.listWorlds();
      const list = res.data;
      setWorlds(list);
      setWorldId((prev) => {
        // On first load, honor ?worldId= URL param if present and valid
        if (!initializedFromUrl) {
          const urlWorldId = searchParams.get("worldId");
          if (urlWorldId) {
            const parsed = Number(urlWorldId);
            if (!isNaN(parsed) && list.some((w) => w.id === parsed)) {
              setInitializedFromUrl(true);
              return parsed;
            }
          }
          setInitializedFromUrl(true);
        }
        if (prev != null && list.some((w) => w.id === prev)) return prev;
        const active = list.find((w) => !w.locked);
        return active?.id ?? list[0]?.id;
      });
    } catch {
      // intentionally empty: layout auth guard handles unauthorized
    } finally {
      setLoading(false);
    }
  }, [searchParams, initializedFromUrl]);

  useEffect(() => {
    refreshWorlds();
  }, [refreshWorlds]);

  const value = useMemo(
    () => ({ worlds, worldId, setWorldId, refreshWorlds, loading }),
    [worlds, worldId, refreshWorlds, loading],
  );

  return (
    <AdminWorldContext.Provider value={value}>
      {children}
    </AdminWorldContext.Provider>
  );
}

export function useAdminWorld(): AdminWorldContextValue {
  const ctx = useContext(AdminWorldContext);
  if (!ctx)
    throw new Error("useAdminWorld must be used within AdminWorldProvider");
  return ctx;
}
