import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";

export async function getBookmarks(cursor?: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor) query.set("cursor", cursor);
  const response = await authenticatedFetch(
    `/api/v1/bookmarks?${query}`,
    signal ? { signal } : {},
  );
  return response.json() as Promise<components["schemas"]["BookmarkPage"]>;
}
export async function getBookmarkStatus(id: string, signal?: AbortSignal) {
  const response = await authenticatedFetch(
    `/api/v1/bookmarks/${encodeURIComponent(id)}`,
    signal ? { signal } : {},
  );
  return response.json() as Promise<components["schemas"]["BookmarkStatus"]>;
}
export async function setBookmark(id: string, saved: boolean) {
  await authenticatedFetch(`/api/v1/bookmarks/${encodeURIComponent(id)}`, {
    method: saved ? "PUT" : "DELETE",
  });
}
