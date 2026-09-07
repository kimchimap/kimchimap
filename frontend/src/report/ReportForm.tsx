import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { getRestaurant } from "../api/client";
import { PhotoUpload } from "../media/PhotoUpload";
import { ClaimEditor } from "./ClaimEditor";
import {
  getCatalogs,
  reportError,
  reportPath,
  sendReport,
  usages,
  type Report,
  type Submission,
} from "./api";

export function ReportForm({
  restaurantId,
  existing,
}: {
  restaurantId: string;
  existing?: Report;
}) {
  const navigate = useNavigate();
  const client = useQueryClient();
  const [draft, setDraft] = useState<Submission>(
    () =>
      existing?.submission ?? {
        restaurantId,
        scopeName: "",
        usage: "SIDE_DISH",
        observedOn: "",
        claims: [
          {
            ingredientId: "",
            classification: "UNKNOWN",
            components: [],
            originalExpression: "",
          },
        ],
        mediaIds: [],
        publicationConsent: false,
      },
  );
  const request = useRef({ body: "", key: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const catalogs = useQuery({
    queryKey: ["catalogs"],
    queryFn: ({ signal }) => getCatalogs(signal),
    retry: false,
  });
  const restaurant = useQuery({
    queryKey: ["restaurant", restaurantId],
    queryFn: ({ signal }) => getRestaurant(restaurantId, signal),
    retry: false,
  });
  async function submit() {
    if (busy) return;
    if (
      !draft.mediaIds.length ||
      !draft.publicationConsent ||
      draft.claims.some(
        (claim) =>
          claim.classification === "MIXED" && claim.components.length < 2,
      )
    ) {
      setError(
        "사진을 1장 이상 첨부하고 공개 동의와 혼합 원산지 선택을 확인해 주세요.",
      );
      return;
    }
    setBusy(true);
    setError("");
    const body = JSON.stringify(draft);
    if (request.current.body !== body)
      request.current = { body, key: crypto.randomUUID() };
    try {
      const result = existing?.id
        ? await sendReport(reportPath(false, existing.id), "PATCH", {
            expectedVersion: existing.version,
            submission: draft,
          })
        : await sendReport(
            reportPath(false),
            "POST",
            draft,
            request.current.key,
          );
      if (!result.id) throw new Error("제보 응답 식별자 누락");
      await client.invalidateQueries({ queryKey: ["private"] });
      await navigate(`/reports/${result.id}`);
    } catch (cause) {
      setError(reportError(cause));
    } finally {
      setBusy(false);
    }
  }
  if (catalogs.isPending || restaurant.isPending)
    return <p role="status">제보 양식을 불러오고 있습니다.</p>;
  if (catalogs.isError || restaurant.isError)
    return (
      <div role="alert">
        업소와 식재료 정보를 불러오지 못했습니다.
        <button
          onClick={() => {
            void catalogs.refetch();
            void restaurant.refetch();
          }}
        >
          다시 시도
        </button>
      </div>
    );
  return (
    <form
      className="report-form"
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <h2>{restaurant.data.name}</h2>
      <p>{restaurant.data.address}</p>
      <p>
        표시판에서 실제 확인한 내용만 적어 주세요. 제보 승인은 식재료 납품이나
        위생·안전성 인증이 아닙니다.
      </p>
      <fieldset disabled={busy}>
        <legend>적용 메뉴·용도</legend>
        <label htmlFor="report-scope">기존 품목 선택</label>
        <select
          id="report-scope"
          value={draft.scopeId ?? ""}
          onChange={(e) => {
            const scope = restaurant.data.scopes?.find(
              (item) => item.id === e.target.value,
            );
            const { scopeId: ignored, ...rest } = draft;
            void ignored;
            setDraft(
              scope?.id
                ? {
                    ...rest,
                    scopeId: scope.id,
                    scopeName: scope.name ?? "",
                    usage: scope.usage ?? "UNKNOWN",
                  }
                : { ...rest, scopeName: "", usage: "SIDE_DISH" },
            );
          }}
        >
          <option value="">새 품목 제보</option>
          {restaurant.data.scopes?.map((scope) => (
            <option key={scope.id} value={scope.id}>
              {scope.name} · {usages[scope.usage ?? ""]}
            </option>
          ))}
        </select>
        <label htmlFor="report-name">메뉴 또는 제공 품목 이름</label>
        <input
          id="report-name"
          required
          maxLength={200}
          readOnly={!!draft.scopeId}
          value={draft.scopeName}
          onChange={(e) => setDraft({ ...draft, scopeName: e.target.value })}
        />
        <label htmlFor="report-usage">사용 용도</label>
        <select
          id="report-usage"
          disabled={!!draft.scopeId}
          value={draft.usage}
          onChange={(e) => setDraft({ ...draft, usage: e.target.value })}
        >
          {Object.entries(usages).map(([code, name]) => (
            <option key={code} value={code}>
              {name}
            </option>
          ))}
        </select>
        <label htmlFor="report-date">표시판을 실제 확인한 날짜</label>
        <input
          id="report-date"
          type="date"
          required
          value={draft.observedOn}
          onChange={(e) => setDraft({ ...draft, observedOn: e.target.value })}
        />
        {draft.claims.map((claim, index) => (
          <section key={index}>
            <ClaimEditor
              value={claim}
              ingredients={catalogs.data.ingredients}
              countries={catalogs.data.countries}
              onChange={(value) =>
                setDraft({
                  ...draft,
                  claims: draft.claims.map((item, i) =>
                    i === index ? value : item,
                  ),
                })
              }
            />
            {draft.claims.length > 1 && (
              <button
                type="button"
                onClick={() =>
                  setDraft({
                    ...draft,
                    claims: draft.claims.filter((_, i) => i !== index),
                  })
                }
              >
                식재료 {index + 1} 삭제
              </button>
            )}
          </section>
        ))}
        <button
          type="button"
          disabled={draft.claims.length >= 10}
          onClick={() =>
            setDraft({
              ...draft,
              claims: [
                ...draft.claims,
                {
                  ingredientId: "",
                  classification: "UNKNOWN",
                  components: [],
                  originalExpression: "",
                },
              ],
            })
          }
        >
          식재료 추가
        </button>
        {draft.mediaIds.length < 5 && (
          <PhotoUpload
            onUploaded={(media) => {
              if (media.id)
                setDraft((current) => ({
                  ...current,
                  mediaIds: [
                    ...new Set([...current.mediaIds, media.id!]),
                  ].slice(0, 5),
                }));
            }}
          />
        )}
        <ul>
          {draft.mediaIds.map((id, index) => (
            <li key={id}>
              첨부 사진 {index + 1}
              <button
                type="button"
                onClick={() =>
                  setDraft({
                    ...draft,
                    mediaIds: draft.mediaIds.filter((item) => item !== id),
                  })
                }
              >
                사진 {index + 1} 제외
              </button>
            </li>
          ))}
        </ul>
        <label>
          <input
            type="checkbox"
            required
            checked={draft.publicationConsent ?? false}
            onChange={(e) =>
              setDraft({ ...draft, publicationConsent: e.target.checked })
            }
          />
          제보 정보와 제출 권한이 있는 사진을 검수 후 국산김치맵에 공개하는 데
          동의합니다.
        </label>
        <button type="submit">
          {busy ? "제보 저장 중…" : existing ? "보완 내용 제출" : "제보 제출"}
        </button>
      </fieldset>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
