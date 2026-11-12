import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";
export type Submission = components["schemas"]["InformationReportSubmission"];
export type Resolution = components["schemas"]["InformationReportResolution"];
export type Detail = components["schemas"]["InformationReportView"];
export const categories: Record<string, string> = {
  ORIGIN_CHANGED: "원산지가 달라요",
  CLOSED: "폐업했어요",
  LOCATION: "위치·주소가 달라요",
  CONTACT: "전화번호가 달라요",
  OTHER: "다른 정보 오류",
};
export const states: Record<string, string> = {
  PENDING: "검토 대기",
  RESOLVED: "처리 완료",
  DISMISSED: "기각",
};
export function reportPath(admin = false, id?: string) {
  return `/api/v1/${admin ? "admin/" : ""}information-reports${id ? `/${encodeURIComponent(id)}` : ""}`;
}
export async function listReports(
  admin: boolean,
  state: string,
  cursor: string | undefined,
  signal: AbortSignal,
): Promise<components["schemas"]["InformationReportPage"]> {
  const query = new URLSearchParams({ limit: "20" });
  if (state) query.set("state", state);
  if (cursor) query.set("cursor", cursor);
  return (
    await authenticatedFetch(`${reportPath(admin)}?${query}`, { signal })
  ).json();
}
export async function getReport(
  admin: boolean,
  id: string,
  signal: AbortSignal,
): Promise<Detail> {
  return (await authenticatedFetch(reportPath(admin, id), { signal })).json();
}
export async function submitReport(
  input: Submission,
  key: string,
): Promise<Detail> {
  return (
    await authenticatedFetch(reportPath(), {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": key },
      body: JSON.stringify(input),
    })
  ).json();
}
export async function resolveReport(
  id: string,
  input: Resolution,
): Promise<Detail> {
  return (
    await authenticatedFetch(`${reportPath(true, id)}/resolution`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    })
  ).json();
}
