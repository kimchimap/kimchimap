import type { components } from "../api/generated";
import { ApiError } from "../api/client";
import { authenticatedFetch } from "../auth/session";

export type Submission = components["schemas"]["ReportSubmission"];
export type Report = components["schemas"]["ReportView"];
export type Claim = components["schemas"]["ReportClaim"];
export type Country = components["schemas"]["CountryItem"];
export type Ingredient = components["schemas"]["IngredientItem"];
export const states: Record<string, string> = {
  PENDING: "검수 대기",
  APPROVED: "승인",
  REJECTED: "반려",
  NEEDS_MORE_INFO: "보완 요청",
  WITHDRAWN: "철회",
};
export const usages: Record<string, string> = {
  SIDE_DISH: "반찬용",
  STEW: "찌개용",
  MAIN_DISH: "주메뉴용",
  OTHER: "기타",
  UNKNOWN: "용도 미확인",
};
export const classifications: Record<string, string> = {
  DOMESTIC: "국내산",
  IMPORTED_SPECIFIED: "수입산 · 국가 명시",
  IMPORTED_UNSPECIFIED: "수입산 · 국가 미표기",
  MIXED: "혼합",
  UNKNOWN: "미확인",
};
export function reportPath(admin: boolean, id?: string) {
  return `/api/v1/${admin ? "admin/" : ""}reports${id ? `/${encodeURIComponent(id)}` : ""}`;
}
export async function getReport(
  admin: boolean,
  id: string,
  signal: AbortSignal,
): Promise<Report> {
  return (await authenticatedFetch(reportPath(admin, id), { signal })).json();
}
export async function getReports(
  admin: boolean,
  state: string,
  cursor: string | undefined,
  signal: AbortSignal,
): Promise<components["schemas"]["ReportPage"]> {
  const params = new URLSearchParams({ limit: "20" });
  if (state) params.set("state", state);
  if (cursor) params.set("cursor", cursor);
  return (
    await authenticatedFetch(`${reportPath(admin)}?${params}`, { signal })
  ).json();
}
export async function getCatalogs(signal: AbortSignal) {
  const read = async <T>(kind: string): Promise<T> => {
    const response = await fetch(`/api/v1/catalogs/${kind}`, { signal });
    if (!response.ok) throw new ApiError(response.status);
    return response.json() as Promise<T>;
  };
  const [ingredients, countries] = await Promise.all([
    read<Ingredient[]>("ingredients"),
    read<Country[]>("countries"),
  ]);
  return { ingredients, countries };
}
export async function sendReport(
  path: string,
  method: string,
  body: unknown,
  key?: string,
): Promise<Report> {
  return (
    await authenticatedFetch(path, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...(key ? { "Idempotency-Key": key } : {}),
      },
      body: JSON.stringify(body),
    })
  ).json();
}
export function reportError(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 409)
      return "다른 변경이 먼저 반영되었습니다. 최신 내용을 확인한 뒤 다시 작성해 주세요.";
    if (cause.status === 401)
      return "로그인이 만료되었습니다. 다시 로그인해 주세요.";
    if (cause.status === 403) return "이 작업을 수행할 권한이 없습니다.";
    if (cause.status === 400 || cause.status === 404)
      return "입력 내용과 사진 소유권, 메뉴·용도를 확인해 주세요.";
    if (cause.status === 429)
      return "요청이 많습니다. 잠시 후 다시 시도해 주세요.";
  }
  return "요청 결과를 확인하지 못했습니다. 내용을 보존했습니다. 목록을 확인하거나 다시 시도해 주세요.";
}
