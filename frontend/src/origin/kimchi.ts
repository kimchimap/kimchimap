export const kimchiIngredients = {
  cabbage: "00000000-0000-4000-8000-000000000001",
  pepper: "00000000-0000-4000-8000-000000000002",
} as const;
export function isKimchiIngredient(id: string | undefined): boolean {
  return id === kimchiIngredients.cabbage || id === kimchiIngredients.pepper;
}
