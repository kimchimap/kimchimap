import { useState, useSyncExternalStore } from "react";
import {
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { MemberGate } from "../auth/MemberGate";
import { classifications, reportError, usages } from "../report/api";
import {
  correctOrigin,
  originDetail,
  originGroups,
  originStates,
  type Detail,
  type Group,
} from "./admin-api";

export function OriginAdminPage() {
  return (
    <main id="main-content">
      <h1>원산지 검토·정정</h1>
      <MemberGate admin>
        <OriginQueue />
      </MemberGate>
    </main>
  );
}
function OriginQueue() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [status, setStatus] = useState("DISPUTED");
  const [selected, setSelected] = useState<Group | null>(null);
  const groups = useInfiniteQuery({
    queryKey: ["private", session.member?.id, "origin-groups", status],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) => originGroups(status, pageParam, signal),
    getNextPageParam: (page) => page.nextOffset ?? undefined,
    retry: false,
  });
  const items = groups.data?.pages.flatMap((page) => page.items ?? []) ?? [];
  return (
    <>
      <p>
        메뉴·용도와 식재료별 근거를 확인하고 잘못된 기록만 제외합니다. 새 원산지
        내용은 사진과 함께 제보·검수하여 추가합니다.
      </p>
      <label htmlFor="origin-state">공개 판정 상태</label>
      <select
        id="origin-state"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value);
          setSelected(null);
        }}
      >
        <option value="">전체</option>
        {Object.entries(originStates).map(([code, name]) => (
          <option key={code} value={code}>
            {name}
          </option>
        ))}
      </select>
      <button onClick={() => void groups.refetch()}>목록 새로고침</button>
      {groups.isPending ? (
        <p role="status">원산지 판정을 불러오고 있습니다.</p>
      ) : groups.isError ? (
        <p role="alert">원산지 목록을 확인하지 못했습니다.</p>
      ) : (
        <>
          {!items.length && <p>해당하는 원산지 판정이 없습니다.</p>}
          <ul>
            {items.map((group) => (
              <li key={`${group.scopeId}-${group.ingredientId}`}>
                <button onClick={() => setSelected(group)}>
                  {group.restaurantName} · {group.scopeName} ·{" "}
                  {usages[group.usage ?? ""]} · {group.ingredientName}
                </button>
                <p>{originStates[group.status ?? ""]}</p>
              </li>
            ))}
          </ul>
          {groups.hasNextPage && (
            <button
              disabled={groups.isFetchingNextPage}
              onClick={() => void groups.fetchNextPage()}
            >
              더 보기
            </button>
          )}
          {groups.data.pages.some((page) => page.truncated) && (
            <p>
              조회 상한에 도달했습니다. 표시된 항목이 전체가 아닙니다. 상태별로
              나누어 확인해 주세요.
            </p>
          )}
        </>
      )}
      {selected?.scopeId && selected.ingredientId && (
        <OriginDetails
          key={`${selected.scopeId}-${selected.ingredientId}`}
          scope={selected.scopeId}
          ingredient={selected.ingredientId}
        />
      )}
    </>
  );
}
function OriginDetails({
  scope,
  ingredient,
}: {
  scope: string;
  ingredient: string;
}) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const detail = useQuery({
    queryKey: [
      "private",
      session.member?.id,
      "origin-detail",
      scope,
      ingredient,
    ],
    queryFn: ({ signal }) => originDetail(scope, ingredient, signal),
    retry: false,
  });
  return (
    <section>
      <h2>선택한 원산지 근거</h2>
      <button onClick={() => void detail.refetch()}>최신 근거 다시 확인</button>
      {detail.isPending ? (
        <p role="status">근거를 불러오고 있습니다.</p>
      ) : detail.isError ? (
        <p role="alert">
          원산지 상세를 확인하지 못했습니다. 이력 상한 또는 네트워크 상태를
          확인해 주세요.
        </p>
      ) : (
        <CorrectionForm key={detail.data.group?.version} detail={detail.data} />
      )}
    </section>
  );
}
function date(value?: string) {
  return value
    ? new Date(value).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" })
    : "미상";
}
function CorrectionForm({ detail }: { detail: Detail }) {
  const client = useQueryClient();
  const [selected, setSelected] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit() {
    const group = detail.group;
    if (
      busy ||
      !group?.scopeId ||
      !group.ingredientId ||
      group.version === undefined ||
      !selected.length
    )
      return;
    setBusy(true);
    setError("");
    try {
      await correctOrigin({
        scopeId: group.scopeId,
        ingredientId: group.ingredientId,
        expectedVersion: group.version,
        withdrawRecordIds: selected,
        reason,
      });
      await client.invalidateQueries({ queryKey: ["private"] });
      await client.invalidateQueries({ queryKey: ["restaurant"] });
      await client.invalidateQueries({ queryKey: ["search"] });
    } catch (cause) {
      setError(reportError(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <h3>
        {detail.group?.scopeName} · {detail.group?.ingredientName}
      </h3>
      <p>{originStates[detail.group?.status ?? ""]}</p>
      <p>
        철회해도 원문과 이전 이력은 보존됩니다. 실제 납품 원산지를 인증하는
        작업이 아닙니다.
      </p>
      {detail.group?.restaurantId && (
        <Link
          to={`/reports/new?restaurantId=${encodeURIComponent(detail.group.restaurantId)}`}
        >
          새 근거와 원산지 제보
        </Link>
      )}
      <form
        className="report-form"
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <fieldset disabled={busy}>
          <legend>제외할 기록 선택 · 최대 20개</legend>
          {detail.records?.map((record) => (
            <article key={record.id}>
              <h4>
                {classifications[record.classification ?? ""] ??
                  "원산지 확인 필요"}
              </h4>
              <p>{record.originalExpression}</p>
              <p>
                출처: {record.sourceName} ·{" "}
                {record.evidenceKind === "SIGNBOARD_OBSERVATION"
                  ? "표시판 관찰"
                  : record.evidenceKind === "SUPPLY_VERIFICATION"
                    ? "납품 검증 자료"
                    : "이용자 제출"}
              </p>
              <p>
                관찰일 {date(record.observedAt)} · 원문 갱신일{" "}
                {date(record.sourceUpdatedAt)} · 관리자 검토일{" "}
                {date(record.reviewedAt)} · 만료일 {date(record.validUntil)}
              </p>
              {record.reportId && (
                <Link
                  to={`/admin/reports/${encodeURIComponent(record.reportId)}`}
                >
                  제보와 증빙 사진 확인
                </Link>
              )}
              {record.withdrawn ? (
                <p>철회됨: {record.withdrawalReason}</p>
              ) : (
                record.id && (
                  <label>
                    <input
                      type="checkbox"
                      checked={selected.includes(record.id)}
                      disabled={
                        selected.length >= 20 && !selected.includes(record.id)
                      }
                      onChange={(e) =>
                        setSelected(
                          e.target.checked
                            ? [...selected, record.id!]
                            : selected.filter((id) => id !== record.id),
                        )
                      }
                    />
                    이 원산지 기록 제외
                  </label>
                )
              )}
            </article>
          ))}
          <label htmlFor="correction-reason">정정 사유와 확인 근거</label>
          <textarea
            id="correction-reason"
            required
            maxLength={2000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <button type="submit" disabled={!selected.length}>
            {busy ? "정정 중…" : "선택 기록 철회·공개 재판정"}
          </button>
        </fieldset>
        {error && <p role="alert">{error}</p>}
      </form>
      <h3>최근 정정 이력 · 최대 100개</h3>
      {!detail.corrections?.length && <p>정정 이력이 없습니다.</p>}
      <ol>
        {detail.corrections?.map((audit) => (
          <li key={audit.id}>
            {date(audit.createdAt)} · {audit.reason}
          </li>
        ))}
      </ol>
    </>
  );
}
