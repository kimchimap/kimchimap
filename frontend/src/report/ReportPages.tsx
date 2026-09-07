import { useState, useSyncExternalStore, type ReactNode } from "react";
import {
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { Link, useParams, useSearchParams } from "react-router";
import { getRestaurant } from "../api/client";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { ReportForm } from "./ReportForm";
import { PrivatePhoto } from "./PrivatePhoto";
import {
  classifications,
  getCatalogs,
  getReport,
  getReports,
  reportError,
  reportPath,
  sendReport,
  states,
  usages,
  type Report,
} from "./api";
import type { components } from "../api/generated";

function Access({
  admin = false,
  children,
}: {
  admin?: boolean;
  children: ReactNode;
}) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  if (session.status === "loading" || session.status === "unknown")
    return <p role="status">로그인을 확인하고 있습니다.</p>;
  if (session.status !== "authenticated")
    return (
      <p>
        <Link to="/login">로그인</Link>한 뒤 이용해 주세요.
      </p>
    );
  if (admin && session.member?.role !== "ADMIN")
    return <p role="alert">관리자만 이용할 수 있습니다.</p>;
  return children;
}
export function NewReportPage() {
  const [params] = useSearchParams();
  const restaurantId = params.get("restaurantId");
  return (
    <main id="main-content">
      <h1>원산지 제보</h1>
      <Access>
        {restaurantId ? (
          <ReportForm restaurantId={restaurantId} />
        ) : (
          <p>업소 상세 화면에서 제보할 업소를 선택해 주세요.</p>
        )}
      </Access>
    </main>
  );
}
export function ReportsPage({ admin = false }: { admin?: boolean }) {
  return (
    <main id="main-content">
      <h1>{admin ? "제보 검수" : "내 제보"}</h1>
      <Access admin={admin}>
        <ReportList admin={admin} />
      </Access>
    </main>
  );
}
function ReportList({ admin }: { admin: boolean }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [state, setState] = useState(admin ? "PENDING" : "");
  const query = useInfiniteQuery({
    queryKey: ["private", session.member?.id, "reports", admin, state],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      getReports(admin, state, pageParam, signal),
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
  });
  const items = query.data?.pages.flatMap((page) => page.items ?? []) ?? [];
  return (
    <>
      <label htmlFor="report-state">제보 상태</label>
      <select
        id="report-state"
        value={state}
        onChange={(e) => setState(e.target.value)}
      >
        <option value="">전체</option>
        {Object.entries(states).map(([code, name]) => (
          <option key={code} value={code}>
            {name}
          </option>
        ))}
      </select>
      {query.isPending ? (
        <p role="status">제보를 불러오고 있습니다.</p>
      ) : query.isError ? (
        <div role="alert">
          제보를 불러오지 못했습니다.
          <button onClick={() => void query.refetch()}>다시 시도</button>
        </div>
      ) : (
        <>
          {!items.length && <p>해당하는 제보가 없습니다.</p>}
          <ul>
            {items.map((item) => (
              <li key={item.id}>
                <Link to={`${admin ? "/admin" : ""}/reports/${item.id}`}>
                  {item.restaurantName} ·{" "}
                  {states[item.state ?? ""] ?? "상태 확인 필요"}
                </Link>
                <p>
                  {item.createdAt
                    ? new Date(item.createdAt).toLocaleString("ko-KR", {
                        timeZone: "Asia/Seoul",
                      })
                    : ""}
                </p>
              </li>
            ))}
          </ul>
          {query.hasNextPage && (
            <button
              disabled={query.isFetchingNextPage}
              onClick={() => void query.fetchNextPage()}
            >
              더 보기
            </button>
          )}
        </>
      )}
    </>
  );
}
export function ReportDetailPage({ admin = false }: { admin?: boolean }) {
  const { id = "" } = useParams();
  return (
    <main id="main-content">
      <h1>{admin ? "제보 검수 상세" : "제보 상세"}</h1>
      <Link to={admin ? "/admin/reports" : "/reports"}>제보 목록</Link>
      <Access admin={admin}>
        <ReportDetail key={id} id={id} admin={admin} />
      </Access>
    </main>
  );
}
function ReportDetail({ id, admin }: { id: string; admin: boolean }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [editing, setEditing] = useState(false);
  const query = useQuery({
    queryKey: ["private", session.member?.id, "report", admin, id],
    queryFn: ({ signal }) => getReport(admin, id, signal),
    retry: false,
  });
  const catalogs = useQuery({
    queryKey: ["catalogs"],
    queryFn: ({ signal }) => getCatalogs(signal),
    retry: false,
  });
  if (query.isPending) return <p role="status">제보를 불러오고 있습니다.</p>;
  if (query.isError || !query.data.submission)
    return (
      <div role="alert">
        제보를 확인하지 못했습니다.
        <button onClick={() => void query.refetch()}>다시 시도</button>
      </div>
    );
  const report = query.data,
    submission = query.data.submission;
  const editable = ["PENDING", "NEEDS_MORE_INFO"].includes(report.state ?? "");
  return (
    <>
      <p>상태: {states[report.state ?? ""]}</p>
      <h2>
        {submission.scopeName} · {usages[submission.usage]}
      </h2>
      <p>표시판 실제 확인일: {submission.observedOn}</p>
      <ul>
        {submission.claims.map((claim) => (
          <li key={claim.ingredientId}>
            {catalogs.data?.ingredients.find(
              (item) => item.id === claim.ingredientId,
            )?.name ?? "식재료 정보 확인 중"}
            : {classifications[claim.classification]}
            <p>{claim.originalExpression}</p>
            <p>
              {claim.components
                .map((part) =>
                  part.countryCode
                    ? (catalogs.data?.countries.find(
                        (item) => item.code === part.countryCode,
                      )?.name ?? part.countryCode)
                    : "국가 미표기 수입산",
                )
                .join(" · ")}
            </p>
          </li>
        ))}
      </ul>
      {submission.mediaIds.map((mediaId, index) => (
        <PrivatePhoto key={mediaId} id={mediaId} index={index} />
      ))}
      <p>
        승인은 표시판 제보에 대한 검수이며 실제 납품 원산지 검증이 아닙니다.
      </p>
      <h2>검토 이력</h2>
      {!report.reviews?.length && <p>아직 검토 이력이 없습니다.</p>}
      <ol>
        {report.reviews?.map((review, index) => (
          <li key={index}>
            {states[review.decision ?? ""]} · {review.reason}
            <p>
              {review.createdAt
                ? new Date(review.createdAt).toLocaleString("ko-KR", {
                    timeZone: "Asia/Seoul",
                  })
                : ""}
            </p>
          </li>
        ))}
      </ol>
      {!admin && editable && (
        <>
          <button onClick={() => setEditing(!editing)}>
            {editing ? "수정 양식 닫기" : "제보 수정·보완"}
          </button>
          {editing && (
            <ReportForm
              key={report.version}
              restaurantId={submission.restaurantId}
              existing={report}
            />
          )}
          <ReportAction
            key={`withdraw-${report.version}`}
            report={report}
            admin={false}
          />
        </>
      )}
      {admin && report.state === "PENDING" && (
        <ReportAction key={`review-${report.version}`} report={report} admin />
      )}
      <button
        onClick={() => {
          setEditing(false);
          void query.refetch();
        }}
      >
        최신 내용 다시 확인
      </button>
    </>
  );
}
function ReportAction({ report, admin }: { report: Report; admin: boolean }) {
  const client = useQueryClient();
  const [decision, setDecision] = useState("NEEDS_MORE_INFO");
  const [reason, setReason] = useState("");
  const [scope, setScope] = useState(report.submission?.scopeId ?? "");
  const [photos, setPhotos] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const restaurant = useQuery({
    queryKey: ["restaurant", report.submission?.restaurantId],
    queryFn: ({ signal }) =>
      getRestaurant(report.submission!.restaurantId, signal),
    enabled: admin,
    retry: false,
  });
  async function submit() {
    if (busy || !report.id || report.version === undefined) return;
    setBusy(true);
    setError("");
    try {
      const body:
        | components["schemas"]["ReportReviewRequest"]
        | components["schemas"]["ReportWithdrawRequest"] = admin
        ? {
            expectedVersion: report.version,
            decision,
            reason,
            privacyReviewedMediaIds: decision === "APPROVED" ? photos : [],
            ...(decision === "APPROVED" && scope
              ? { approvedScopeId: scope }
              : {}),
          }
        : { expectedVersion: report.version, reason };
      await sendReport(
        `${reportPath(admin, report.id)}/${admin ? "reviews" : "withdraw"}`,
        "POST",
        body,
      );
      await client.invalidateQueries({ queryKey: ["private"] });
      await client.invalidateQueries({ queryKey: ["restaurant"] });
    } catch (cause) {
      setError(reportError(cause));
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
      <fieldset disabled={busy}>
        <legend>{admin ? "검수 결정" : "제보 철회"}</legend>
        {admin && (
          <>
            <label htmlFor="review-decision">검수 결과</label>
            <select
              id="review-decision"
              value={decision}
              onChange={(e) => setDecision(e.target.value)}
            >
              {["NEEDS_MORE_INFO", "APPROVED", "REJECTED"].map((code) => (
                <option key={code} value={code}>
                  {states[code]}
                </option>
              ))}
            </select>
            {decision === "APPROVED" && (
              <>
                <label htmlFor="review-scope">공개 반영할 메뉴·용도</label>
                <select
                  id="review-scope"
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                >
                  {!report.submission?.scopeId && (
                    <option value="">새 품목으로 생성</option>
                  )}
                  {restaurant.data?.scopes
                    ?.filter((item) => item.usage === report.submission?.usage)
                    .map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name} · {usages[item.usage ?? ""]}
                      </option>
                    ))}
                </select>
                {restaurant.isError && (
                  <p role="alert">
                    기존 품목을 불러오지 못했습니다. 승인 전에 다시 확인해
                    주세요.
                  </p>
                )}
                <p>
                  이름이 비슷하다는 이유만으로 다른 품목을 합치지 마세요. 사진
                  공개는 선택 사항입니다.
                </p>
                {report.submission?.mediaIds.map((id, index) => (
                  <label key={id}>
                    <input
                      type="checkbox"
                      checked={photos.includes(id)}
                      onChange={(e) =>
                        setPhotos(
                          e.target.checked
                            ? [...photos, id]
                            : photos.filter((item) => item !== id),
                        )
                      }
                    />
                    사진 {index + 1}의 개인정보를 확인했으며 정제본 공개가
                    가능합니다.
                  </label>
                ))}
              </>
            )}
          </>
        )}
        <label htmlFor={admin ? "review-reason" : "withdraw-reason"}>
          {admin ? "검수 사유" : "철회 사유"}
        </label>
        <textarea
          id={admin ? "review-reason" : "withdraw-reason"}
          required
          maxLength={2000}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <button
          type="submit"
          disabled={admin && decision === "APPROVED" && !restaurant.isSuccess}
        >
          {busy ? "처리 중…" : admin ? "검수 결과 반영" : "제보 철회"}
        </button>
      </fieldset>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
