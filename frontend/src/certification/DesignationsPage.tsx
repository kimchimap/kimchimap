import { useState, useSyncExternalStore } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router";
import { MemberGate } from "../auth/MemberGate";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { getRestaurant } from "../api/client";
import {
  getDesignation,
  getDesignations,
  getDesignationSources,
  saveDesignation,
  type Designation,
  type DesignationSource,
  type DesignationView,
} from "./api";

export function DesignationsPage() {
  return (
    <main id="main-content">
      <h1>지정 정보 관리</h1>
      <MemberGate admin>
        <Designations />
      </MemberGate>
    </main>
  );
}
function Designations() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [params] = useSearchParams();
  const restaurantId = params.get("restaurantId") ?? "";
  const [selected, setSelected] = useState("");
  const sources = useQuery({
    queryKey: ["private", session.member?.id, "designation-sources"],
    queryFn: ({ signal }) => getDesignationSources(signal),
    retry: false,
  });
  const list = useQuery({
    queryKey: ["private", session.member?.id, "designations", restaurantId],
    queryFn: ({ signal }) => getDesignations(restaurantId, signal),
    retry: false,
  });
  return (
    <>
      <p>
        지정 사실과 적용 품목·제도 기준을 그대로 기록합니다. 모든 원재료가
        국내산이라는 정보로 확대하지 않습니다.
      </p>
      {sources.isPending ? (
        <p role="status">이용 가능한 소스를 확인하고 있습니다.</p>
      ) : sources.isError ? (
        <div role="alert">
          지정 정보 이용 조건을 확인하지 못했습니다.
          <button onClick={() => void sources.refetch()}>다시 시도</button>
        </div>
      ) : !sources.data.length ? (
        <p>
          지정 정보를 저장·재게시할 수 있는 소스가 아직 등록되지 않았습니다.
          협회 이용 허가는 대기 중입니다.
        </p>
      ) : restaurantId ? (
        <DesignationForm
          key={restaurantId}
          restaurantId={restaurantId}
          sources={sources.data}
        />
      ) : (
        <p>
          새 지정 정보를 추가하려면 업소 상세에서 지정 정보 관리를 선택해
          주세요.
        </p>
      )}
      <h2>최근 지정 정보 · 최대 50개</h2>
      <button onClick={() => void list.refetch()}>목록 새로고침</button>
      {list.isPending ? (
        <p role="status">지정 정보를 불러오고 있습니다.</p>
      ) : list.isError ? (
        <p role="alert">지정 정보를 불러오지 못했습니다.</p>
      ) : (
        <>
          {!list.data.length && <p>등록된 지정 정보가 없습니다.</p>}
          <ul>
            {list.data.map((item) => (
              <li key={item.id}>
                <button onClick={() => setSelected(item.id ?? "")}>
                  {item.restaurantName} · {item.schemeName}
                </button>
                <p>
                  {item.sourceAllowed
                    ? item.publiclyVisible
                      ? "공개 설정"
                      : "비공개 설정"
                    : "이용 조건에 따라 공개 중단"}
                </p>
              </li>
            ))}
          </ul>
        </>
      )}
      {selected && (
        <DesignationDetail
          key={selected}
          id={selected}
          sources={sources.data ?? []}
        />
      )}
    </>
  );
}
function DesignationDetail({
  id,
  sources,
}: {
  id: string;
  sources: DesignationSource[];
}) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const query = useQuery({
    queryKey: ["private", session.member?.id, "designation", id],
    queryFn: ({ signal }) => getDesignation(id, signal),
    retry: false,
  });
  return (
    <section>
      <h2>지정 내용·변경 이력</h2>
      <button onClick={() => void query.refetch()}>최신 내용 확인</button>
      {query.isPending ? (
        <p role="status">상세를 불러오고 있습니다.</p>
      ) : query.isError || !query.data.designation ? (
        <p role="alert">상세 정보를 확인하지 못했습니다.</p>
      ) : (
        <>
          <DesignationForm
            key={query.data.version}
            restaurantId={query.data.designation.restaurantId}
            sources={sources}
            existing={query.data}
          />
          <h3>최근 변경 이력 · 최대 100개</h3>
          <ol>
            {query.data.history?.map((item) => (
              <li key={item.version}>
                {item.createdAt
                  ? new Date(item.createdAt).toLocaleString("ko-KR", {
                      timeZone: "Asia/Seoul",
                    })
                  : "확인 시각 미상"}{" "}
                · {item.reason}
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}
function DesignationForm({
  restaurantId,
  sources,
  existing,
}: {
  restaurantId: string;
  sources: DesignationSource[];
  existing?: DesignationView;
}) {
  const client = useQueryClient();
  const [draft, setDraft] = useState<Designation>(
    () =>
      existing?.designation ?? {
        restaurantId,
        sourceId: "",
        externalId: "",
        schemeName: "",
        applicableItems: "",
        criteriaOriginal: "",
        publiclyVisible: false,
      },
  );
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [failed, setFailed] = useState(false);
  const restaurant = useQuery({
    queryKey: ["restaurant", restaurantId],
    queryFn: ({ signal }) => getRestaurant(restaurantId, signal),
    retry: false,
  });
  async function submit() {
    if (busy) return;
    setBusy(true);
    setMessage("");
    setFailed(false);
    try {
      await saveDesignation(draft, reason, existing);
      setMessage("지정 정보를 저장했습니다.");
      await client.invalidateQueries({ queryKey: ["private"] });
      await client.invalidateQueries({
        queryKey: ["restaurant", restaurantId],
      });
    } catch {
      setFailed(true);
      setMessage(
        "저장 결과를 확인하지 못했습니다. 소스 이용 허가·입력 날짜·최신 변경 이력을 확인해 주세요.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      className="report-form"
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <h3>{restaurant.data?.name ?? "업소 정보 확인 중"}</h3>
      {restaurant.isError && <p role="alert">업소를 확인하지 못했습니다.</p>}
      <fieldset disabled={busy || !restaurant.isSuccess}>
        <legend>{existing ? "지정 내용 수정·취소" : "지정 정보 추가"}</legend>
        <label>
          허가된 출처
          <select
            required
            disabled={!!existing}
            value={draft.sourceId}
            onChange={(e) => setDraft({ ...draft, sourceId: e.target.value })}
          >
            <option value="">선택해 주세요</option>
            {sources.map((source) => (
              <option key={source.id} value={source.id}>
                {source.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          원천 지정 식별자
          <input
            required
            readOnly={!!existing}
            maxLength={200}
            value={draft.externalId}
            onChange={(e) => setDraft({ ...draft, externalId: e.target.value })}
          />
        </label>
        <label>
          제도 이름
          <input
            required
            maxLength={200}
            value={draft.schemeName}
            onChange={(e) => setDraft({ ...draft, schemeName: e.target.value })}
          />
        </label>
        <label>
          실제 적용 품목·범위
          <textarea
            required
            maxLength={2000}
            value={draft.applicableItems}
            onChange={(e) =>
              setDraft({ ...draft, applicableItems: e.target.value })
            }
          />
        </label>
        <label>
          제도 기준 원문
          <textarea
            required
            maxLength={5000}
            value={draft.criteriaOriginal}
            onChange={(e) =>
              setDraft({ ...draft, criteriaOriginal: e.target.value })
            }
          />
        </label>
        {(
          [
            ["designatedOn", "지정일"],
            ["expiresOn", "만료일"],
            ["cancelledOn", "취소일"],
          ] as const
        ).map(([key, label]) => (
          <label key={key}>
            {label}
            <input
              type="date"
              value={draft[key] ?? ""}
              onChange={(e) => {
                const next = { ...draft };
                if (e.target.value) next[key] = e.target.value;
                else delete next[key];
                setDraft(next);
              }}
            />
          </label>
        ))}
        <p>
          원문에 날짜가 없으면 비워 둡니다. 지정 취소는 취소일과 사유를 기록하여
          이전 이력을 보존합니다.
        </p>
        <label>
          <input
            type="checkbox"
            checked={draft.publiclyVisible}
            onChange={(e) =>
              setDraft({ ...draft, publiclyVisible: e.target.checked })
            }
          />
          허가된 기준과 품목 범위로 공개
        </label>
        <label>
          확인·변경 사유
          <textarea
            required
            maxLength={2000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <button
          type="submit"
          disabled={!sources.some((source) => source.id === draft.sourceId)}
        >
          {busy ? "저장 중…" : "지정 정보 저장"}
        </button>
      </fieldset>
      {message && <p role={failed ? "alert" : "status"}>{message}</p>}
    </form>
  );
}
