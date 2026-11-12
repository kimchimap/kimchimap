import { ApiError } from "../api/client";
import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";

export async function getLikes(cursor?: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor) query.set("cursor", cursor);
  const response = await authenticatedFetch(
    `/api/v1/likes?${query}`,
    signal ? { signal } : {},
  );
  return response.json() as Promise<components["schemas"]["LikePage"]>;
}
export async function getLikeStatus(id: string, signal?: AbortSignal) {
  const response = await authenticatedFetch(
    `/api/v1/likes/${encodeURIComponent(id)}`,
    signal ? { signal } : {},
  );
  return response.json() as Promise<components["schemas"]["LikeStatus"]>;
}
export async function setLike(id: string, saved: boolean) {
  await authenticatedFetch(`/api/v1/likes/${encodeURIComponent(id)}`, {
    method: saved ? "PUT" : "DELETE",
  });
}

export async function getLikeCount(id: string, signal?: AbortSignal) {
  const response = await fetch(
    `/api/v1/restaurants/${encodeURIComponent(id)}/likes`,
    signal ? { signal } : {},
  );
  if (!response.ok) throw new ApiError(response.status);
  return response.json() as Promise<components["schemas"]["LikeCount"]>;
}
