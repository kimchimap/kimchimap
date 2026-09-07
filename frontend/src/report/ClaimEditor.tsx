import { useId } from "react";
import {
  classifications,
  type Claim,
  type Country,
  type Ingredient,
} from "./api";

export function ClaimEditor({
  value,
  ingredients,
  countries,
  onChange,
}: {
  value: Claim;
  ingredients: Ingredient[];
  countries: Country[];
  onChange: (value: Claim) => void;
}) {
  const id = useId();
  const selected = value.components.map(
    (part) => part.countryCode ?? "UNSPECIFIED",
  );
  function setCountries(codes: string[]) {
    onChange({
      ...value,
      components: codes.map((code) =>
        code === "UNSPECIFIED"
          ? { kind: "IMPORTED_UNSPECIFIED" }
          : {
              kind: code === "KR" ? "DOMESTIC" : "IMPORTED_SPECIFIED",
              countryCode: code,
            },
      ),
    });
  }
  return (
    <fieldset>
      <legend>식재료 원산지</legend>
      <label htmlFor={`${id}-ingredient`}>식재료</label>
      <select
        id={`${id}-ingredient`}
        required
        value={value.ingredientId}
        onChange={(e) => onChange({ ...value, ingredientId: e.target.value })}
      >
        <option value="">선택해 주세요</option>
        {ingredients
          .filter((item) => item.id)
          .map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
      </select>
      <label htmlFor={`${id}-kind`}>원산지 구분</label>
      <select
        id={`${id}-kind`}
        value={value.classification}
        onChange={(e) =>
          onChange({
            ...value,
            classification: e.target.value,
            components:
              e.target.value === "DOMESTIC"
                ? [{ kind: "DOMESTIC", countryCode: "KR" }]
                : e.target.value === "IMPORTED_UNSPECIFIED"
                  ? [{ kind: "IMPORTED_UNSPECIFIED" }]
                  : [],
          })
        }
      >
        {Object.entries(classifications).map(([code, name]) => (
          <option key={code} value={code}>
            {name}
          </option>
        ))}
      </select>
      {value.classification === "IMPORTED_SPECIFIED" && (
        <>
          <label htmlFor={`${id}-country`}>표시된 국가</label>
          <select
            id={`${id}-country`}
            required
            value={selected[0] ?? ""}
            onChange={(e) => setCountries([e.target.value])}
          >
            <option value="">선택해 주세요</option>
            {countries
              .filter((item) => item.code && item.code !== "KR")
              .map((item) => (
                <option key={item.code} value={item.code}>
                  {item.name}
                </option>
              ))}
          </select>
        </>
      )}
      {value.classification === "MIXED" && (
        <fieldset>
          <legend>표시된 혼합 원산지 · 두 개 이상</legend>
          {[
            ...countries.filter((item) => item.code),
            { code: "UNSPECIFIED", name: "수입산 · 국가 미표기" },
          ].map((item) => (
            <label key={item.code}>
              <input
                type="checkbox"
                checked={selected.includes(item.code!)}
                onChange={(e) =>
                  setCountries(
                    e.target.checked
                      ? [...selected, item.code!]
                      : selected.filter((code) => code !== item.code),
                  )
                }
              />
              {item.name}
            </label>
          ))}
          <p>
            혼합 비율은 추정하지 않습니다. 표시된 비율이 있다면 아래 원문에
            그대로 적어 주세요.
          </p>
        </fieldset>
      )}
      <label htmlFor={`${id}-expression`}>표시판 원문</label>
      <textarea
        id={`${id}-expression`}
        required
        maxLength={1000}
        value={value.originalExpression}
        onChange={(e) =>
          onChange({ ...value, originalExpression: e.target.value })
        }
      />
    </fieldset>
  );
}
