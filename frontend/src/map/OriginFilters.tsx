import type { Filters } from "./types";
import { choices, filterFor } from "./kimchi-filters";
export function OriginFilters({
  value,
  onApply,
}: {
  value: Filters;
  onApply: (value: Filters) => void;
}) {
  const selected =
    value.groups?.[0]?.ingredients?.map((item) => item.ingredientId) ?? [];
  return (
    <fieldset className="kimchi-filters">
      <legend>김치 원산지 필터</legend>
      {choices.map((choice) => (
        <button
          key={choice.name}
          type="button"
          aria-pressed={
            choice.ingredients.length === selected.length &&
            choice.ingredients.every((id) => selected.includes(id))
          }
          onClick={() => onApply(filterFor(choice.ingredients))}
        >
          {choice.name}
        </button>
      ))}
    </fieldset>
  );
}
