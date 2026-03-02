"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { PublicCachedMapResponse } from "@/types";
import { PublicGameMap } from "@/components/game/public-game-map";

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  return date.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function LoadingCard() {
  return (
    <Card className="w-full max-w-[700px]">
      <CardHeader>
        <CardTitle>서버 현황</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Skeleton className="h-44 w-full" />
        <Skeleton className="h-4 w-24" />
        <div className="space-y-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
        </div>
      </CardContent>
    </Card>
  );
}

export function ServerStatusCard() {
  const [data, setData] = useState<PublicCachedMapResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const apiBase =
          process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
        const response = await fetch(`${apiBase}/public/cached-map`, {
          method: "GET",
          cache: "no-store",
        });
        if (!response.ok) {
          throw new Error("공개 지도 조회 실패");
        }
        const payload = (await response.json()) as PublicCachedMapResponse;
        setData(payload);
      } catch {
        setData(null);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return <LoadingCard />;
  }

  return (
    <Card className="w-full max-w-[700px]">
      <CardHeader>
        <CardTitle>서버 현황</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {!data?.available ? (
          <div className="flex h-44 items-center justify-center border border-gray-700 bg-black/30 text-sm text-muted-foreground">
            현재 가동 중인 서버가 없습니다
          </div>
        ) : (
          <PublicGameMap data={data} />
        )}

        <div>
          <h3 className="mb-2 text-sm font-semibold">최근 동향</h3>
          {!data?.available || data.history.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              표시할 기록이 없습니다
            </p>
          ) : (
            <ul className="space-y-1">
              {data.history.map((item) => (
                <li
                  key={item.id}
                  className="text-sm leading-relaxed text-zinc-200"
                >
                  <span className="mr-2 text-xs text-muted-foreground">
                    {formatDateTime(item.sentAt)}
                  </span>
                  <span>{item.text}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
