"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useWorldStore } from "@/stores/worldStore";
import { useGeneralStore } from "@/stores/generalStore";
import { useGameStore } from "@/stores/gameStore";
import { messageApi } from "@/lib/gameApi";
import type { Message } from "@/types";
import { Eye, Trash2, Send, Users, Inbox, Forward } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { PageHeader } from "@/components/game/page-header";
import { LoadingState } from "@/components/game/loading-state";
import { EmptyState } from "@/components/game/empty-state";
import { Button } from "@/components/ui/button";

export default function SpyPage() {
  const currentWorld = useWorldStore((s) => s.currentWorld);
  const { myGeneral, fetchMyGeneral } = useGeneralStore();
  const { generals, cities, nations, loading, loadAll } = useGameStore();
  const [reports, setReports] = useState<Message[]>([]);
  const [mailLoading, setMailLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Mailbox tabs
  const [mailboxTab, setMailboxTab] = useState<"spy" | "send" | "groups">(
    "spy",
  );

  // Send message
  const [sendTargetIds, setSendTargetIds] = useState<number[]>([]);
  const [sendMessage, setSendMessage] = useState("");
  const [sending, setSending] = useState(false);

  // Recipient groups
  const [groups, setGroups] = useState<{ name: string; memberIds: number[] }[]>(
    [
      { name: "참모진", memberIds: [] },
      { name: "첩보대", memberIds: [] },
    ],
  );
  const [newGroupName, setNewGroupName] = useState("");

  // Forward
  const [forwardingId, setForwardingId] = useState<number | null>(null);
  const [forwardTargetId, setForwardTargetId] = useState("");

  useEffect(() => {
    if (!currentWorld) return;
    fetchMyGeneral(currentWorld.id);
    loadAll(currentWorld.id);
  }, [currentWorld, fetchMyGeneral, loadAll]);

  const fetchReports = useCallback(async () => {
    if (!myGeneral) return;
    setRefreshing(true);
    try {
      const { data } = await messageApi.getMine(myGeneral.id);
      const filtered = data
        .filter(isSpyReport)
        .sort(
          (a, b) => new Date(b.sentAt).getTime() - new Date(a.sentAt).getTime(),
        );
      setReports(filtered);
    } finally {
      setMailLoading(false);
      setRefreshing(false);
    }
  }, [myGeneral]);

  useEffect(() => {
    if (!myGeneral) return;
    fetchReports();
  }, [myGeneral, fetchReports]);

  const cityMap = useMemo(
    () => new Map(cities.map((c) => [c.id, c])),
    [cities],
  );
  const generalMap = useMemo(
    () => new Map(generals.map((g) => [g.id, g])),
    [generals],
  );

  const nationMap = useMemo(
    () => new Map(nations.map((n) => [n.id, n])),
    [nations],
  );

  const nationGenerals = useMemo(() => {
    if (!myGeneral?.nationId) return [];
    return generals.filter((g) => g.nationId === myGeneral.nationId);
  }, [generals, myGeneral?.nationId]);

  if (!currentWorld)
    return (
      <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>
    );
  if (loading) return <LoadingState />;
  if (mailLoading) return <LoadingState message="첩보함을 불러오는 중..." />;

  const unreadCount = reports.filter((m) => !getReadAt(m.meta)).length;

  const handleSendSpyMessage = async () => {
    if (!myGeneral || sendTargetIds.length === 0 || !sendMessage.trim()) return;
    setSending(true);
    try {
      for (const targetId of sendTargetIds) {
        await messageApi.send(
          currentWorld!.id,
          myGeneral.id,
          targetId,
          sendMessage.trim(),
          {
            messageType: "spy",
          },
        );
      }
      setSendMessage("");
      setSendTargetIds([]);
    } finally {
      setSending(false);
    }
  };

  const handleForward = async (reportId: number) => {
    if (!myGeneral || !forwardTargetId) return;
    const report = reports.find((r) => r.id === reportId);
    if (!report) return;
    try {
      await messageApi.send(
        currentWorld!.id,
        myGeneral.id,
        Number(forwardTargetId),
        `[전달된 정찰 보고] ${formatScoutResult(report.payload)}`,
        {
          messageType: "scout_forward",
        },
      );
      setForwardingId(null);
      setForwardTargetId("");
    } catch {
      /* ignore */
    }
  };

  const toggleGroupMember = (groupIdx: number, generalId: number) => {
    setGroups((prev) =>
      prev.map((g, i) => {
        if (i !== groupIdx) return g;
        const has = g.memberIds.includes(generalId);
        return {
          ...g,
          memberIds: has
            ? g.memberIds.filter((id) => id !== generalId)
            : [...g.memberIds, generalId],
        };
      }),
    );
  };

  const addGroup = () => {
    if (!newGroupName.trim()) return;
    setGroups((prev) => [
      ...prev,
      { name: newGroupName.trim(), memberIds: [] },
    ]);
    setNewGroupName("");
  };

  const handleMarkAsRead = async (id: number) => {
    try {
      await messageApi.markAsRead(id);
      const now = new Date().toISOString();
      setReports((prev) =>
        prev.map((m) =>
          m.id === id ? { ...m, meta: { ...m.meta, readAt: now } } : m,
        ),
      );
    } catch {}
  };

  const handleDelete = async (id: number) => {
    try {
      await messageApi.delete(id);
      setReports((prev) => prev.filter((m) => m.id !== id));
    } catch {}
  };

  return (
    <div className="p-4 space-y-6 max-w-3xl mx-auto">
      <div className="flex items-center justify-between">
        <PageHeader icon={Eye} title="첩보함" />
        <Button size="sm" variant="outline" onClick={fetchReports}>
          새로고침
        </Button>
      </div>

      <Card>
        <CardContent className="pt-6 text-sm text-muted-foreground flex items-center gap-2">
          <Badge variant="outline">수신 첩보 {reports.length}건</Badge>
          <Badge className="bg-amber-500 text-black">
            미확인 {unreadCount}건
          </Badge>
          {refreshing && <span>첩보를 갱신하고 있습니다...</span>}
        </CardContent>
      </Card>

      <Tabs
        value={mailboxTab}
        onValueChange={(v) => setMailboxTab(v as typeof mailboxTab)}
      >
        <TabsList>
          <TabsTrigger value="spy">
            <Inbox className="size-3 mr-1" />
            우편함
          </TabsTrigger>
          <TabsTrigger value="send">
            <Send className="size-3 mr-1" />
            메시지 전송
          </TabsTrigger>
          <TabsTrigger value="groups">
            <Users className="size-3 mr-1" />
            수신자 그룹
          </TabsTrigger>
        </TabsList>

        {/* Send Tab */}
        <TabsContent value="send" className="space-y-3 mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">비밀 메시지 전송</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">
                  수신자 (클릭하여 선택)
                </label>
                <div className="flex flex-wrap gap-1 max-h-32 overflow-y-auto">
                  {nationGenerals.map((g) => (
                    <Button
                      key={g.id}
                      size="sm"
                      variant={
                        sendTargetIds.includes(g.id) ? "default" : "outline"
                      }
                      className="h-6 px-2 text-[10px]"
                      onClick={() =>
                        setSendTargetIds((prev) =>
                          prev.includes(g.id)
                            ? prev.filter((id) => id !== g.id)
                            : [...prev, g.id],
                        )
                      }
                    >
                      {g.name}
                    </Button>
                  ))}
                </div>
                {/* Quick select from groups */}
                <div className="flex gap-1 mt-1">
                  {groups
                    .filter((g) => g.memberIds.length > 0)
                    .map((g, idx) => (
                      <Button
                        key={idx}
                        size="sm"
                        variant="ghost"
                        className="h-5 px-1.5 text-[9px]"
                        onClick={() => setSendTargetIds(g.memberIds)}
                      >
                        📋 {g.name}
                      </Button>
                    ))}
                </div>
              </div>
              <Textarea
                value={sendMessage}
                onChange={(e) => setSendMessage(e.target.value)}
                placeholder="첩보 메시지를 입력하세요..."
                className="h-24 text-sm"
              />
              <Button
                onClick={handleSendSpyMessage}
                disabled={
                  sending || sendTargetIds.length === 0 || !sendMessage.trim()
                }
              >
                {sending ? "전송 중..." : `${sendTargetIds.length}명에게 전송`}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Groups Tab */}
        <TabsContent value="groups" className="space-y-3 mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">비밀 수신자 그룹</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {groups.map((group, gIdx) => (
                <div key={gIdx} className="border rounded p-3 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{group.name}</span>
                    <Badge variant="outline" className="text-[10px]">
                      {group.memberIds.length}명
                    </Badge>
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {nationGenerals.map((g) => (
                      <Button
                        key={g.id}
                        size="sm"
                        variant={
                          group.memberIds.includes(g.id) ? "default" : "outline"
                        }
                        className="h-5 px-1.5 text-[9px]"
                        onClick={() => toggleGroupMember(gIdx, g.id)}
                      >
                        {g.name}
                      </Button>
                    ))}
                  </div>
                </div>
              ))}
              <div className="flex gap-2">
                <Input
                  value={newGroupName}
                  onChange={(e) => setNewGroupName(e.target.value)}
                  placeholder="새 그룹 이름..."
                  className="text-xs h-8"
                />
                <Button size="sm" onClick={addGroup}>
                  추가
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Spy reports tab */}
        <TabsContent value="spy" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>첩보 결과</CardTitle>
            </CardHeader>
            <CardContent>
              {reports.length === 0 ? (
                <EmptyState icon={Eye} title="수신된 첩보 결과가 없습니다." />
              ) : (
                <div className="space-y-2">
                  {reports.map((report) => {
                    const payload = report.payload;
                    const targetCityId = extractNumber(payload, [
                      "destCityId",
                      "targetCityId",
                    ]);
                    const targetGeneralId = extractNumber(payload, [
                      "targetGeneralId",
                      "destGeneralId",
                    ]);
                    const nestedSpy = getRecord(payload, "spyResult");
                    const nestedScout = getRecord(payload, "scoutResult");
                    const nestedTargetCity =
                      extractNumber(nestedSpy, [
                        "destCityId",
                        "targetCityId",
                      ]) ??
                      extractNumber(nestedScout, [
                        "destCityId",
                        "targetCityId",
                      ]);

                    const city = cityMap.get(
                      targetCityId ?? nestedTargetCity ?? -1,
                    );
                    const targetGeneral = generalMap.get(targetGeneralId ?? -1);
                    const sender = report.srcId
                      ? generalMap.get(report.srcId)
                      : null;
                    const senderNation = report.srcId
                      ? nationMap.get(sender?.nationId ?? -1)
                      : null;

                    return (
                      <Card
                        key={report.id}
                        className="cursor-pointer"
                        onClick={() => handleMarkAsRead(report.id)}
                      >
                        <CardContent className="pt-4 space-y-2">
                          <div className="flex items-center gap-2 text-xs text-muted-foreground">
                            <span className="font-medium text-foreground">
                              {sender?.name ??
                                senderNation?.name ??
                                "첩보 보고"}
                            </span>
                            <span>
                              {new Date(report.sentAt).toLocaleString("ko-KR")}
                            </span>
                            {!getReadAt(report.meta) && (
                              <Badge className="bg-amber-500 text-black">
                                NEW
                              </Badge>
                            )}
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-6 w-6 p-0"
                              onClick={(event) => {
                                event.stopPropagation();
                                setForwardingId(
                                  forwardingId === report.id ? null : report.id,
                                );
                              }}
                              title="전달"
                            >
                              <Forward className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="ml-auto h-6 w-6 p-0"
                              onClick={(event) => {
                                event.stopPropagation();
                                handleDelete(report.id);
                              }}
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </div>

                          {/* Forward UI */}
                          {forwardingId === report.id && (
                            <div
                              className="flex items-center gap-2 bg-muted/30 rounded p-2"
                              onClick={(e) => e.stopPropagation()}
                            >
                              <select
                                value={forwardTargetId}
                                onChange={(e) =>
                                  setForwardTargetId(e.target.value)
                                }
                                className="h-7 border border-gray-600 bg-[#111] px-1 text-xs text-white rounded flex-1"
                              >
                                <option value="">전달 대상 선택...</option>
                                {nationGenerals.map((g) => (
                                  <option key={g.id} value={g.id}>
                                    {g.name}
                                  </option>
                                ))}
                              </select>
                              <Button
                                size="sm"
                                className="h-7 text-xs"
                                onClick={() => handleForward(report.id)}
                              >
                                전달
                              </Button>
                            </div>
                          )}

                          <div className="text-sm space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-muted-foreground">
                                목표 도시
                              </span>
                              <Badge variant="outline">
                                {city?.name ?? "미상"}
                              </Badge>
                              <span className="text-muted-foreground">
                                목표 장수
                              </span>
                              <Badge variant="outline">
                                {targetGeneral?.name ?? "정보 없음"}
                              </Badge>
                            </div>
                            <p className="text-foreground break-all">
                              {formatScoutResult(report.payload)}
                            </p>
                          </div>
                        </CardContent>
                      </Card>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function getString(
  value: Record<string, unknown> | null | undefined,
  key: string,
): string | null {
  if (!value) return null;
  const raw = value[key];
  return typeof raw === "string" ? raw : null;
}

function getRecord(
  value: Record<string, unknown> | null | undefined,
  key: string,
): Record<string, unknown> {
  if (!value) return {};
  const raw = value[key];
  if (raw && typeof raw === "object" && !Array.isArray(raw)) {
    return raw as Record<string, unknown>;
  }
  return {};
}

function extractNumber(
  value: Record<string, unknown> | null | undefined,
  keys: string[],
): number | null {
  if (!value) return null;
  for (const key of keys) {
    const raw = value[key];
    if (typeof raw === "number" && Number.isFinite(raw)) return raw;
    if (typeof raw === "string") {
      const parsed = Number(raw);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return null;
}

function getReadAt(
  meta: Record<string, unknown> | null | undefined,
): string | null {
  return getString(meta, "readAt");
}

function hasSpyKeyword(value: string | null): boolean {
  if (!value) return false;
  return /(spy|scout|첩보)/i.test(value);
}

function isSpyReport(message: Message): boolean {
  const payload = message.payload;
  const spyResult = getRecord(payload, "spyResult");
  const scoutResult = getRecord(payload, "scoutResult");

  if (
    Object.keys(spyResult).length > 0 ||
    Object.keys(scoutResult).length > 0
  ) {
    return true;
  }

  if (extractNumber(payload, ["destCityId", "targetCityId"]) != null) {
    return true;
  }

  if (extractNumber(payload, ["targetGeneralId", "destGeneralId"]) != null) {
    return true;
  }

  const content = getString(payload, "content");
  return (
    hasSpyKeyword(message.mailboxCode) ||
    hasSpyKeyword(message.messageType) ||
    hasSpyKeyword(content)
  );
}

function formatScoutResult(
  payload: Record<string, unknown> | null | undefined,
): string {
  if (!payload) return "첩보 결과 형식을 확인할 수 없습니다.";

  const content = getString(payload, "content");
  if (content) return content;

  const spyResult = getRecord(payload, "spyResult");
  if (Object.keys(spyResult).length > 0) {
    return Object.entries(spyResult)
      .map(([key, value]) => `${key}:${String(value)}`)
      .join(" / ");
  }

  const scoutResult = getRecord(payload, "scoutResult");
  if (Object.keys(scoutResult).length > 0) {
    return Object.entries(scoutResult)
      .map(([key, value]) => `${key}:${String(value)}`)
      .join(" / ");
  }

  return "첩보 결과 형식을 확인할 수 없습니다.";
}
