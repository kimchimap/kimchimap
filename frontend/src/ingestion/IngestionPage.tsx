import { useRef, useState, useSyncExternalStore } from "react";
import {
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { MemberGate } from "../auth/MemberGate";
import { ApiError } from "../api/client";
import type { components } from "../api/generated";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import {
  jobStates,
  readAdmin,
  requestRun,
  type Job,
  type Source,
  type Run,
} from "./api";

export function IngestionPage() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  return (
    <main id="main-content">
      <h1>데이터 수집 관리</h1>
      <MemberGate admin>
        <Dashboard memberId={session.member?.id ?? ""} />
      </MemberGate>
    </main>
  );
}
function Dashboard({ memberId }: { memberId: string }) {
  const [selected, setSelected] = useState("");
  const sources = useQuery({
    queryKey: ["private", memberId, "ingestion-sources"],
    queryFn: ({ signal }) => readAdmin<Source[]>("sources", signal),
    retry: false,
  });
  const jobs = useQuery({
    queryKey: ["private", memberId, "ingestion-jobs"],
    queryFn: ({ signal }) => readAdmin<Job[]>("jobs", signal),
    retry: false,
    refetchInterval: 15000,
    refetchIntervalInBackground: false,
  });
  return (
    <>
      <p>
        국내산 사용 항목이 확인된 업소만 수집 대상으로 삼습니다. 원산지 정보가
        없는 일반 음식점 전체 수집은 중단했습니다. 협회 데이터도 이용 허락
        전에는 수집하지 않습니다.
      </p>
      <h2>수집 소스</h2>
      {sources.isPending ? (
        <p role="status">소스를 불러오고 있습니다.</p>
      ) : sources.isError ? (
        <div role="alert">
          수집 소스를 확인하지 못했습니다.
          <button onClick={() => void sources.refetch()}>다시 시도</button>
        </div>
      ) : (
        sources.data.map((source) => (
          <section key={source.id}>
            <h3>{source.name}</h3>
            <p>
              수집·재게시 조건:{" "}
              {source.collectionAllowed && source.republicationAllowed
                ? "확인됨"
                : "허가 확인 필요"}{" "}
              · 인증키: {source.credentialConfigured ? "설정됨" : "미설정"}
            </p>
            <p>
              정기 수집: {source.scheduled ? "활성" : "꺼짐"} · 원천 기준 주기:{" "}
              {source.intervalSeconds
                ? source.intervalSeconds / 86400
                : "미확인"}
              일
            </p>
            {source.domesticQualificationSupported && source.id ? (
              <RunForm source={source} />
            ) : (
              <p>
                국내산 사용 여부를 선별할 수 없는 소스입니다. 신규 수집과 기존
                작업 재개를 차단했습니다.
              </p>
            )}
          </section>
        ))
      )}
      <h2>최근 수집 작업 · 최대 50개</h2>
      <p>
        15초마다 상태를 확인합니다. 처리 완료는 해당 조회 범위의 페이지 완료이며
        전국 업소 최신화나 모든 레코드 정상 반영을 뜻하지 않습니다.
      </p>
      <button onClick={() => void jobs.refetch()}>상태 새로고침</button>
      {jobs.isPending ? (
        <p role="status">작업을 불러오고 있습니다.</p>
      ) : jobs.isError ? (
        <p role="alert">수집 상태를 확인하지 못했습니다.</p>
      ) : (
        <ul>
          {!jobs.data.length && <li>아직 수집 작업이 없습니다.</li>}
          {jobs.data.map((job) => (
            <li key={job.id}>
              <button onClick={() => setSelected(job.id ?? "")}>
                {jobStates[job.status ?? ""] ?? "상태 확인 필요"} ·{" "}
                {job.requestedAt
                  ? new Date(job.requestedAt).toLocaleString("ko-KR", {
                      timeZone: "Asia/Seoul",
                    })
                  : ""}
              </button>
              <p>
                읽음 {job.readCount ?? 0} · 변경 {job.changedCount ?? 0} · 격리{" "}
                {job.quarantinedCount ?? 0} · 다음 페이지 {job.nextPage}
              </p>
              {job.errorCode && <p>오류 코드: {job.errorCode}</p>}
            </li>
          ))}
        </ul>
      )}
      {selected && (
        <JobDetails key={selected} id={selected} memberId={memberId} />
      )}
    </>
  );
}
function RunForm({ source }: { source: Source }) {
  const client = useQueryClient();
  const [mode, setMode] = useState("INCREMENTAL");
  const [pages, setPages] = useState(2);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [failed, setFailed] = useState(false);
  const request = useRef({ body: "", key: "" });
  async function submit() {
    if (busy || !source.id) return;
    setBusy(true);
    setMessage("");
    setFailed(false);
    const body: Run = { mode, pageBudget: pages, reason };
    const fingerprint = JSON.stringify(body);
    if (request.current.body !== fingerprint)
      request.current = { body: fingerprint, key: crypto.randomUUID() };
    try {
      const job = await requestRun(source.id, body, request.current.key);
      setMessage(
        `수집 요청을 접수했습니다. 현재 상태: ${jobStates[job.status ?? ""] ?? "확인 중"}`,
      );
      await client.invalidateQueries({ queryKey: ["private"] });
    } catch (cause) {
      setFailed(true);
      setMessage(
        cause instanceof ApiError && cause.status === 409
          ? "진행 중인 작업·요청 내용 또는 이용 허가가 변경되었습니다. 최신 상태를 확인해 주세요."
          : cause instanceof ApiError && cause.status === 503
            ? "인증키 또는 서버 연결을 확인해 주세요. 작업 상태를 먼저 확인한 뒤 재시도해 주세요."
            : "요청 결과를 확인하지 못했습니다. 작업 상태를 확인한 뒤 다시 시도해 주세요.",
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
      <fieldset
        disabled={
          busy ||
          !source.collectionAllowed ||
          !source.republicationAllowed ||
          !source.credentialConfigured
        }
      >
        <legend>제한된 수집 재실행</legend>
        <label>
          수집 방식
          <select value={mode} onChange={(e) => setMode(e.target.value)}>
            <option value="INCREMENTAL">증분 수집</option>
            <option value="FULL">전체 대조</option>
          </select>
        </label>
        <label>
          추가 페이지 예산 · 페이지당 최대 100건
          <input
            type="number"
            min={1}
            max={100}
            required
            value={pages}
            onChange={(e) => setPages(Number(e.target.value))}
          />
        </label>
        <label>
          재실행 사유
          <textarea
            required
            maxLength={2000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <p>
          진행 중인 작업은 이어서 처리합니다. 일부 처리 상태의 재실행은 지정한
          페이지 예산만 추가합니다. 같은 요청의 재전송은 예산을 다시 추가하지
          않습니다.
        </p>
        <button type="submit">{busy ? "접수 중…" : "수집 실행 요청"}</button>
      </fieldset>
      {message && <p role={failed ? "alert" : "status"}>{message}</p>}
    </form>
  );
}
function JobDetails({ id, memberId }: { id: string; memberId: string }) {
  const events = useInfiniteQuery({
    queryKey: ["private", memberId, "ingestion-events", id],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      readAdmin<components["schemas"]["IngestionEventPage"]>(
        `jobs/${encodeURIComponent(id)}/events?cursor=${pageParam}&limit=50`,
        signal,
      ),
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
  });
  const quarantine = useInfiniteQuery({
    queryKey: ["private", memberId, "ingestion-quarantine", id],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      readAdmin<components["schemas"]["IngestionQuarantinePage"]>(
        `jobs/${encodeURIComponent(id)}/quarantines?limit=50${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ""}`,
        signal,
      ),
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
  });
  return (
    <section>
      <h2>선택한 작업의 처리 이력</h2>
      <button
        onClick={() => {
          void events.refetch();
          void quarantine.refetch();
        }}
      >
        이력 새로고침
      </button>
      {events.isPending ? (
        <p role="status">처리 이력을 불러오고 있습니다.</p>
      ) : events.isError ? (
        <p role="alert">처리 이력을 불러오지 못했습니다.</p>
      ) : (
        <>
          <ul>
            {events.data.pages
              .flatMap((page) => page.items ?? [])
              .map((event) => (
                <li key={event.id}>
                  {event.pageNo}페이지 ·{" "}
                  {jobStates[event.status ?? ""] ?? event.status}{" "}
                  {event.errorCode && `· 오류 코드: ${event.errorCode}`}
                </li>
              ))}
          </ul>
          {events.hasNextPage && (
            <button
              disabled={events.isFetchingNextPage}
              onClick={() => void events.fetchNextPage()}
            >
              처리 이력 더 보기
            </button>
          )}
        </>
      )}
      <h3>격리된 레코드</h3>
      <p>
        오류 코드와 페이지·행 번호만 표시하며 원문 개인정보와 비밀값은 노출하지
        않습니다.
      </p>
      {quarantine.isError ? (
        <p role="alert">격리 정보를 불러오지 못했습니다.</p>
      ) : (
        <ul>
          {quarantine.data?.pages
            .flatMap((page) => page.items ?? [])
            .map((item) => (
              <li key={item.id}>
                {item.pageNo}페이지 {item.rowIndex}행 · {item.errorCode}
              </li>
            ))}
        </ul>
      )}
      {quarantine.hasNextPage && (
        <button
          disabled={quarantine.isFetchingNextPage}
          onClick={() => void quarantine.fetchNextPage()}
        >
          격리 정보 더 보기
        </button>
      )}
    </section>
  );
}
