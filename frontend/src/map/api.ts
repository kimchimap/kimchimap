import { ApiError } from "../api/client";
import type { SearchRequest, SearchResult } from "./types";
export async function searchRestaurants(
  request: SearchRequest,
  signal: AbortSignal,
): Promise<SearchResult> {
  const response = await fetch("/api/v1/restaurants/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.ok) throw new ApiError(response.status);
  return response.json();
}
