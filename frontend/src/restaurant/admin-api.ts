import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";
export type AdminRestaurant = components["schemas"]["RestaurantAdminView"];
export type RestaurantCorrection =
  components["schemas"]["RestaurantCorrectionRequest"];
export async function getAdminRestaurant(
  id: string,
  signal: AbortSignal,
): Promise<AdminRestaurant> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/restaurants/${encodeURIComponent(id)}`,
      { signal },
    )
  ).json();
}
export async function correctRestaurant(
  id: string,
  input: RestaurantCorrection,
): Promise<AdminRestaurant> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/restaurants/${encodeURIComponent(id)}/corrections`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(input),
      },
    )
  ).json();
}
export function correctionDraft(view: AdminRestaurant): RestaurantCorrection {
  return {
    expectedVersion: view.version!,
    name: view.snapshot?.name ?? "",
    address: view.snapshot?.address ?? "",
    businessStatus: view.snapshot?.businessStatus ?? "UNKNOWN",
    ...(view.snapshot?.latitude != null
      ? { latitude: view.snapshot.latitude }
      : {}),
    ...(view.snapshot?.longitude != null
      ? { longitude: view.snapshot.longitude }
      : {}),
    phone: view.snapshot?.phone ?? "",
    reason: "",
    locationReviewed: false,
    businessPhoneReviewed: false,
  };
}
