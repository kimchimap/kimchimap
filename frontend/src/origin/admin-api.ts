import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";
export type Group = components["schemas"]["OriginAdminGroup"];
export type Detail = components["schemas"]["OriginAdminDetail"];
export type Correction = components["schemas"]["OriginCorrectionRequest"];
export const originStates: Record<string, string> = {
  CURRENT: "공개 중",
  DISPUTED: "상충 · 검토 필요",
  UNAVAILABLE: "공개 가능한 정보 없음",
};
export async function originGroups(
  status: string,
  offset: number,
  signal: AbortSignal,
): Promise<components["schemas"]["OriginAdminGroupPage"]> {
  const params = new URLSearchParams({ offset: String(offset), limit: "20" });
  if (status) params.set("status", status);
  return (
    await authenticatedFetch(`/api/v1/admin/origins?${params}`, { signal })
  ).json();
}
export async function originDetail(
  scope: string,
  ingredient: string,
  signal: AbortSignal,
): Promise<Detail> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/origins/${encodeURIComponent(scope)}/${encodeURIComponent(ingredient)}`,
      { signal },
    )
  ).json();
}
export async function correctOrigin(body: Correction): Promise<Detail> {
  return (
    await authenticatedFetch("/api/v1/admin/origin-corrections", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
  ).json();
}
