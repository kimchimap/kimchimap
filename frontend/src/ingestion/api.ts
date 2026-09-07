import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";
export type Job = components["schemas"]["IngestionJobStatus"];
export type Source = components["schemas"]["IngestionSourceStatus"];
export type Run = components["schemas"]["IngestionRunRequest"];
export async function readAdmin<T>(
  path: string,
  signal: AbortSignal,
): Promise<T> {
  return (
    await authenticatedFetch(`/api/v1/admin/ingestion/${path}`, { signal })
  ).json();
}
export async function requestRun(
  id: string,
  body: Run,
  key: string,
): Promise<Job> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/ingestion/sources/${encodeURIComponent(id)}/runs`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": key },
        body: JSON.stringify(body),
      },
    )
  ).json();
}
export const jobStates: Record<string, string> = {
  QUEUED: "실행 대기",
  RUNNING: "수집 중",
  WAITING: "재시도 대기",
  SUCCEEDED: "조회 범위 처리 완료",
  PARTIAL: "일부 처리 · 재개 필요",
  FAILED: "실패",
  CANCELLED: "취소",
};
