import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";
export type MatchDetail = components["schemas"]["MatchReviewDetail"];
export async function getMatches(
  signal: AbortSignal,
): Promise<components["schemas"]["MatchListItem"][]> {
  return (await authenticatedFetch("/api/v1/admin/matches", { signal })).json();
}
export async function getMatch(
  id: string,
  signal: AbortSignal,
): Promise<MatchDetail> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/matches/${encodeURIComponent(id)}`,
      { signal },
    )
  ).json();
}
export async function reviewMatch(
  id: string,
  body: components["schemas"]["MatchReviewRequest"],
): Promise<MatchDetail> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/matches/${encodeURIComponent(id)}/reviews`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      },
    )
  ).json();
}
