import { kimchiIngredients } from "../origin/kimchi";
import type { Filters } from "./types";

const { cabbage, pepper } = kimchiIngredients;
export const choices = [
  { name: "국산 배추", ingredients: [cabbage] },
  { name: "국산 고춧가루", ingredients: [pepper] },
  { name: "둘 다 국산", ingredients: [cabbage, pepper] },
];
export function filterFor(ingredients: string[]): Filters {
  return {
    groups: [
      {
        ingredients: ingredients.map((ingredientId) => ({
          ingredientId,
          mode: "DOMESTIC",
          countries: [],
          includeMixed: false,
        })),
      },
    ],
  };
}
export const defaultKimchiFilters = filterFor([cabbage, pepper]);
