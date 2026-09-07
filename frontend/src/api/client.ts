import type { components } from "./generated";

export class ApiError extends Error {
  constructor(public readonly status: number) {
    super("서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  }
}

export async function getRestaurant(
  id: string,
  signal?: AbortSignal,
): Promise<components["schemas"]["RestaurantDetail"]> {
  const response = await fetch(
    `/api/v1/restaurants/${encodeURIComponent(id)}`,
    signal ? { signal } : {},
  );
  if (!response.ok) throw new ApiError(response.status);
  return response.json() as Promise<components["schemas"]["RestaurantDetail"]>;
}

export async function getSystemStatus(
  signal?: AbortSignal,
): Promise<components["schemas"]["SystemStatus"]> {
  const response = await fetch(
    "/api/v1/system/status",
    signal ? { signal } : {},
  );
  if (!response.ok) throw new ApiError(response.status);
  return response.json() as Promise<components["schemas"]["SystemStatus"]>;
}
