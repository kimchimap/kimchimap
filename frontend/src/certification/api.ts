import type { components } from "../api/generated";
import { authenticatedFetch } from "../auth/session";
export type Designation = components["schemas"]["DesignationInput"];
export type DesignationView = components["schemas"]["DesignationAdminView"];
export type DesignationSource = components["schemas"]["DesignationSource"];
export async function getDesignations(
  restaurantId: string,
  signal: AbortSignal,
): Promise<components["schemas"]["DesignationListItem"][]> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/designations${restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : ""}`,
      { signal },
    )
  ).json();
}
export async function getDesignation(
  id: string,
  signal: AbortSignal,
): Promise<DesignationView> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/designations/${encodeURIComponent(id)}`,
      { signal },
    )
  ).json();
}
export async function getDesignationSources(
  signal: AbortSignal,
): Promise<DesignationSource[]> {
  return (
    await authenticatedFetch("/api/v1/admin/designations/sources", { signal })
  ).json();
}
export async function saveDesignation(
  designation: Designation,
  reason: string,
  existing?: DesignationView,
): Promise<DesignationView> {
  return (
    await authenticatedFetch(
      `/api/v1/admin/designations${existing?.id ? `/${encodeURIComponent(existing.id)}` : ""}`,
      {
        method: existing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          designation,
          reason,
          ...(existing ? { expectedVersion: existing.version } : {}),
        }),
      },
    )
  ).json();
}
