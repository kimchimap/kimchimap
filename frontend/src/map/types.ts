import type { components } from "../api/generated";
export type SearchRequest = components["schemas"]["SearchRequest"];
export type SearchResult = components["schemas"]["SearchResponse"];
export type RestaurantItem = NonNullable<SearchResult["items"]>[number];
export type Center = components["schemas"]["Center"];
export type Bounds = components["schemas"]["Bounds"];
export type ScopeFilter = components["schemas"]["ScopeFilter"];
export type IngredientFilter = components["schemas"]["IngredientFilter"];
export type Filters = Pick<
  SearchRequest,
  "groups" | "maxAgeDays" | "evidenceKinds"
>;
export const initialCenter: Center = { latitude: 37.5665, longitude: 126.978 };
