import { useState, useSyncExternalStore } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { MemberGate } from "../auth/MemberGate";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { reportError } from "../report/api";
import { getMatches, getMatch, reviewMatch, type MatchDetail } from "./api";
export function MatchesPage() {
  return (
    <main id="main-content">
      <h1>업소 매칭 검토</h1>
      <MemberGate admin>
        <MatchQueue />
      </MemberGate>
    </main>
  );
}
function MatchQueue() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [selected, setSelected] = useState("");
  const list = useQuery({
    queryKey: ["private", session.member?.id, "matches"],
    queryFn: ({ signal }) => getMatches(signal),
    retry: false,
  });
  return (
    <>
      <p>
        수집 관찰본과 업소 주소·좌표·외부 식별자를 비교합니다. 이름만으로 같은
        업소라고 판단하지 마세요. 대기 목록은 최대 50개입니다.
      </p>
      <button onClick={() => void list.refetch()}>목록 새로고침</button>
      {list.isPending ? (
        <p role="status">매칭 후보를 불러오고 있습니다.</p>
      ) : list.isError ? (
        <p role="alert">매칭 후보를 확인하지 못했습니다.</p>
      ) : (
        <>
          {!list.data.length && <p>검토할 매칭 후보가 없습니다.</p>}
          <ul>
            {list.data.map((item) => (
              <li key={`${item.sourceId}-${item.externalId}`}>
                <p>
                  {item.sourceName} · {item.name ?? "업소 관찰본 없음"}
                </p>
                <p>{item.address}</p>
                {item.reviewable && item.id ? (
                  <button onClick={() => setSelected(item.id!)}>
                    관찰본·후보 비교
                  </button>
                ) : (
                  <p>
                    수집 관찰본이 없어 확정할 수 없습니다. 다음 허용 수집에서
                    갱신이 필요합니다.
                  </p>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
      {selected && <MatchView key={selected} id={selected} />}
    </>
  );
}
function MatchView({ id }: { id: string }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const detail = useQuery({
    queryKey: ["private", session.member?.id, "match", id],
    queryFn: ({ signal }) => getMatch(id, signal),
    retry: false,
  });
  return (
    <section>
      <h2>수집 관찰본과 지점 비교</h2>
      <button onClick={() => void detail.refetch()}>최신 관찰본 확인</button>
      {detail.isPending ? (
        <p role="status">비교 자료를 불러오고 있습니다.</p>
      ) : detail.isError ? (
        <p role="alert">비교할 관찰본을 확인하지 못했습니다.</p>
      ) : (
        <MatchForm key={detail.data.version} data={detail.data} />
      )}
    </section>
  );
}
function MatchForm({ data }: { data: MatchDetail }) {
  const client = useQueryClient();
  const [target, setTarget] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit() {
    if (busy || !data.id || data.version === undefined || !target) return;
    setBusy(true);
    setError("");
    try {
      await reviewMatch(data.id, {
        expectedVersion: data.version,
        decision: target === "DISTINCT" ? "DISTINCT" : "MATCHED",
        ...(target !== "DISTINCT" ? { restaurantId: target } : {}),
        reason,
      });
      await client.invalidateQueries({ queryKey: ["private"] });
      await client.invalidateQueries({ queryKey: ["restaurant"] });
    } catch (cause) {
      setError(reportError(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <h3>{data.name}</h3>
      <p>{data.address}</p>
      <p>외부 식별자: {data.externalId}</p>
      <p>
        수집 좌표:{" "}
        {data.latitude != null && data.longitude != null
          ? `${data.latitude}, ${data.longitude}`
          : "없음 · 위치를 추정하지 않음"}
      </p>
      <p>
        관찰본 수집일:{" "}
        {data.observedAt
          ? new Date(data.observedAt).toLocaleString("ko-KR", {
              timeZone: "Asia/Seoul",
            })
          : "미상"}{" "}
        · 원문 갱신일:{" "}
        {data.sourceUpdatedAt
          ? new Date(data.sourceUpdatedAt).toLocaleString("ko-KR", {
              timeZone: "Asia/Seoul",
            })
          : "미상"}
      </p>
      {data.state !== "PENDING" ? (
        <p role="status">
          검토가 반영되었습니다.{" "}
          {data.restaurantId && (
            <Link
              to={`/restaurants/${encodeURIComponent(data.restaurantId)}/contact`}
            >
              업소 확인
            </Link>
          )}
        </p>
      ) : (
        <form
          className="report-form"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <fieldset disabled={busy}>
            <legend>비교 결과</legend>
            {data.candidates?.map((candidate) => (
              <label key={candidate.id}>
                <input
                  type="radio"
                  name="match-target"
                  required
                  value={candidate.id}
                  checked={target === candidate.id}
                  onChange={() => setTarget(candidate.id ?? "")}
                />
                기존 업소 연결: {candidate.name} · {candidate.address}
                <span>
                  {" "}
                  · 좌표{" "}
                  {candidate.latitude != null && candidate.longitude != null
                    ? `${candidate.latitude}, ${candidate.longitude}`
                    : "없음"}
                </span>
              </label>
            ))}
            <label>
              <input
                type="radio"
                name="match-target"
                required
                value="DISTINCT"
                checked={target === "DISTINCT"}
                onChange={() => setTarget("DISTINCT")}
              />
              별도 업소로 확인 · 수집 관찰본으로 새 업소 생성
            </label>
            <p>
              같은 출처의 다른 식별자가 있는 기존 업소에는 연결할 수 없습니다.
              매칭 검토일을 새 수집일이나 원산지 확인일로 바꾸지 않습니다.
            </p>
            <label>
              비교 근거와 결정 사유
              <textarea
                required
                maxLength={2000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <button type="submit" disabled={!target}>
              {busy ? "반영 중…" : "비교 결과 반영"}
            </button>
          </fieldset>
          {error && <p role="alert">{error}</p>}
        </form>
      )}
    </>
  );
}
