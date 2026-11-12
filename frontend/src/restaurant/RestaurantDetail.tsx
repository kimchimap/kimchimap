import { useSyncExternalStore } from "react";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { ApiError, getRestaurant } from "../api/client";
import type { components } from "../api/generated";
import { LikeToggle } from "../like/LikeToggle";
import { RestaurantContact } from "./RestaurantContact";
import { usages, classifications } from "../report/api";
const evidenceNames: Record<string, string> = {
  SIGNBOARD_OBSERVATION: "원산지 표시판 관찰",
  ASSOCIATION_DESIGNATION: "협회 지정",
  USER_SUBMISSION: "이용자 제출 자료",
  SUPPLY_VERIFICATION: "납품 원산지 검증 자료",
};
const statuses: Record<string, string> = {
  CURRENT: "현재 공개 정보",
  DISPUTED: "상충 정보 · 검토 필요",
  UNAVAILABLE: "현재 유효한 정보 없음",
  EXPIRED: "유효기간 만료",
};
const freshness: Record<string, string> = {
  RECENT: "최근 확인",
  STALE: "오래된 확인 정보",
  UNKNOWN: "확인일 미상",
  EXPIRED: "유효기간 만료",
};
function date(value?: string, precision?: string) {
  if (!value) return "정보 없음";
  if (precision === "DATE")
    return new Date(value).toLocaleDateString("ko-KR", {
      timeZone: "Asia/Seoul",
    });
  return new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
}
function reference(value?: string) {
  if (!value) return undefined;
  if (/^\/api\/v1\/media\/[0-9a-f-]{36}\/public$/i.test(value)) return value;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password
      ? url.href
      : undefined;
  } catch {
    return undefined;
  }
}
function OriginClaim({
  claim,
  selected,
}: {
  claim: components["schemas"]["Claim"];
  selected: boolean;
}) {
  const proof = reference(claim.publicReference);
  return (
    <div className="origin-claim">
      <p>
        {selected
          ? "현재 대표 근거"
          : "공개 이력 · 현재 대표 근거로 선정되지 않음"}
      </p>
      <strong>{classifications[claim.classification ?? ""] ?? "미확인"}</strong>
      <p>
        {claim.components
          ?.map(
            (component) =>
              `${component.countryName ?? (component.kind === "IMPORTED_UNSPECIFIED" ? "국가 미표기 수입산" : "미확인")}${component.ratio != null ? ` ${Number((component.ratio * 100).toFixed(4))}%` : ""}`,
          )
          .join(" · ")}
      </p>
      <blockquote>{claim.originalExpression || "원문 표현 없음"}</blockquote>
      <p>
        {evidenceNames[claim.evidenceKind ?? ""] ?? "근거 종류 미확인"} ·{" "}
        {claim.sourceName ?? "출처 미상"}
      </p>
      {claim.publicSummary && <p>{claim.publicSummary}</p>}
      <dl className="evidence-dates">
        <dt>원산지 실제 관찰일</dt>
        <dd>{date(claim.observedAt, claim.observedPrecision)}</dd>
        <dt>원문 갱신일</dt>
        <dd>{date(claim.sourceUpdatedAt, claim.sourceUpdatedPrecision)}</dd>
        <dt>최초 수집일</dt>
        <dd>{date(claim.collectedAt)}</dd>
        <dt>마지막 수집 성공일</dt>
        <dd>{date(claim.lastFetchSucceededAt)}</dd>
        {claim.approvedAt && (
          <>
            <dt>인증 날짜 (국산김치맵 제보 승인)</dt>
            <dd>{date(claim.approvedAt, "DATE")}</dd>
          </>
        )}
        <dt>관리자 검토일</dt>
        <dd>{date(claim.reviewedAt)}</dd>
        <dt>유효기간</dt>
        <dd>
          {claim.validUntil ? date(claim.validUntil) : "명시된 종료일 없음"}
        </dd>
      </dl>
      <p className="data-badge">
        {freshness[claim.freshness ?? ""] ?? "최신성 별도 확인 필요"}
      </p>
      {proof && (
        <a href={proof} target="_blank" rel="noopener noreferrer">
          공개 근거 확인 (새 창)
        </a>
      )}
    </div>
  );
}
export function RestaurantDetail({ id }: { id: string }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const query = useQuery({
    queryKey: ["restaurant", id],
    queryFn: ({ signal }) => getRestaurant(id, signal),
    retry: false,
  });
  if (query.isPending)
    return <p role="status">업소 정보를 불러오고 있습니다.</p>;
  if (query.isError)
    return (
      <div role="alert">
        <p>
          {query.error instanceof ApiError && query.error.status === 404
            ? "공개된 업소 정보를 찾을 수 없습니다."
            : "업소 상세를 불러오지 못했습니다."}
        </p>
        <button onClick={() => void query.refetch()}>상세 다시 시도</button>
      </div>
    );
  const data = query.data;
  return (
    <article className="restaurant-detail">
      <p className="eyebrow">출처와 함께 보는 업소 정보</p>
      <h1>{data.name}</h1>
      <p>{data.address}</p>
      <p>
        {data.businessStatus === "OPEN"
          ? "원천 정보상 영업 중"
          : data.businessStatus === "CLOSED"
            ? "원천 정보상 폐업"
            : "영업 상태 확인 필요"}{" "}
        · 방문 전 업소에 확인해 주세요.
      </p>
      <div className="activity-links">
        <LikeToggle restaurantId={id} />
        <Link
          to={`/information-reports/new?restaurantId=${encodeURIComponent(id)}`}
        >
          정보 오류 신고하기
        </Link>
        <Link to={`/reports/new?restaurantId=${encodeURIComponent(id)}`}>
          원산지 제보하기
        </Link>
      </div>
      <RestaurantContact contact={data.contact ?? null} />
      {session.member?.role === "ADMIN" && (
        <Link to={`/admin/designations?restaurantId=${encodeURIComponent(id)}`}>
          지정 정보 관리
        </Link>
      )}
      <section>
        <h2>메뉴·용도별 원산지</h2>
        {!data.scopes?.length && (
          <p className="empty-message">
            확보된 원산지 정보가 없습니다. 정보 없음은 수입산을 뜻하지 않습니다.
          </p>
        )}
        {data.scopes?.map((scope) => (
          <section className="origin-scope" key={scope.id}>
            <h3>
              {scope.name}{" "}
              <small>{usages[scope.usage ?? ""] ?? "용도 미확인"}</small>
            </h3>
            <p>
              이 품목에 적용되는 정보입니다. 업소의 모든 메뉴를 뜻하지 않습니다.
            </p>
            {!scope.origins?.length && (
              <p>이 품목의 식재료 원산지 정보가 없습니다.</p>
            )}
            {scope.origins?.map((origin) => (
              <section key={origin.ingredientId}>
                <h4>
                  {origin.ingredientName} ·{" "}
                  {statuses[origin.status ?? ""] ?? "확인 필요"}
                </h4>
                {origin.status === "DISPUTED" && (
                  <p role="note">
                    서로 다른 원산지 기록이 있어 긍정적인 국내산 검색에서
                    제외합니다.
                  </p>
                )}
                {origin.claims?.map((claim) => (
                  <OriginClaim
                    key={claim.id}
                    claim={claim}
                    selected={origin.selectedRecordId === claim.id}
                  />
                ))}
              </section>
            ))}
          </section>
        ))}
      </section>
      <section>
        <h2>인증·지정 이력</h2>
        {!data.designations?.length && <p>공개 가능한 지정 정보가 없습니다.</p>}
        {data.designations?.map((designation) => (
          <section className="origin-scope" key={designation.id}>
            <h3>{designation.schemeName}</h3>
            <p>출처: {designation.sourceName}</p>
            <p>적용 품목: {designation.applicableItems}</p>
            <blockquote>{designation.criteriaOriginal}</blockquote>
            <p>
              인증 날짜 (자율표시제 지정일){" "}
              {designation.designatedOn ?? "지정일 미제공"} · 만료일{" "}
              {designation.expiresOn ?? "미상"} · 취소일{" "}
              {designation.cancelledOn ?? "없음"}
            </p>
            <p>이 지정은 모든 부재료의 국내산 여부를 확인한 정보가 아닙니다.</p>
          </section>
        ))}
      </section>
      <p>{data.informationNotice}</p>
      <Link to="/about/data">정보의 출처와 확인 수준 알아보기</Link>
    </article>
  );
}
export function RestaurantDetailPage() {
  const { id = "" } = useParams();
  return (
    <main id="main-content">
      <Link to="/">지도와 목록으로</Link>
      <RestaurantDetail id={id} />
    </main>
  );
}
