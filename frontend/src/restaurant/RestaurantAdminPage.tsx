import { useState, useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { MemberGate } from "../auth/MemberGate";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import type { components } from "../api/generated";
import { reportError } from "../report/api";
import {
  correctRestaurant,
  correctionDraft,
  getAdminRestaurant,
  type AdminRestaurant,
} from "./admin-api";
import { RestaurantCorrectionFields } from "./RestaurantCorrectionFields";

export function RestaurantAdminPage() {
  const { id = "" } = useParams();
  return (
    <main id="main-content">
      <h1>업소 정보 정정</h1>
      <MemberGate admin>
        <RestaurantEditor key={id} id={id} />
      </MemberGate>
    </main>
  );
}
function RestaurantEditor({ id }: { id: string }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const query = useQuery({
    queryKey: ["private", session.member?.id, "admin-restaurant", id],
    queryFn: ({ signal }) => getAdminRestaurant(id, signal),
    retry: false,
  });
  if (query.isPending)
    return <p role="status">업소 정보를 불러오고 있습니다.</p>;
  if (query.isError)
    return <p role="alert">업소 정보를 확인하지 못했습니다.</p>;
  return (
    <>
      <Link to={`/restaurants/${id}`}>공개 상세 확인</Link>
      <p>
        폐업 처리한 업소는 공개 상세에 표시되지 않습니다. 원산지 확인 날짜는
        업소 정보 정정으로 바뀌지 않습니다.
      </p>
      <CorrectionForm key={query.data.version} id={id} data={query.data} />
      <button onClick={() => void query.refetch()}>최신 내용 다시 확인</button>
      <h2>정정 이력</h2>
      <p>최근 100건을 표시합니다.</p>
      {!query.data.corrections?.length && <p>정정 이력이 없습니다.</p>}
      {query.data.corrections?.map((audit) => (
        <section key={audit.id}>
          <h3>
            {audit.createdAt
              ? new Date(audit.createdAt).toLocaleString("ko-KR", {
                  timeZone: "Asia/Seoul",
                })
              : "시각 미확인"}
          </h3>
          <p>{audit.reason}</p>
          <p>처리 관리자: {audit.actorId}</p>
          <details>
            <summary>변경 전후 확인</summary>
            <h4>변경 전</h4>
            <Snapshot value={audit.before} />
            <h4>변경 후</h4>
            <Snapshot value={audit.after} />
          </details>
        </section>
      ))}
    </>
  );
}
function CorrectionForm({ id, data }: { id: string; data: AdminRestaurant }) {
  const client = useQueryClient();
  const [value, setValue] = useState(() => correctionDraft(data));
  const mutation = useMutation({
    mutationFn: () => correctRestaurant(id, value),
    onSuccess: async () => {
      await Promise.all(
        ["private", "restaurant", "restaurant-search", "like-count"].map(
          (key) => client.invalidateQueries({ queryKey: [key] }),
        ),
      );
    },
  });
  return (
    <form
      className="report-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (!mutation.isPending) mutation.mutate();
      }}
    >
      <fieldset disabled={mutation.isPending}>
        <legend>정정할 내용</legend>
        <RestaurantCorrectionFields value={value} onChange={setValue} />
        <label htmlFor="correction-reason">정정 사유와 확인 근거</label>
        <textarea
          id="correction-reason"
          required
          maxLength={2000}
          value={value.reason}
          onChange={(e) => setValue({ ...value, reason: e.target.value })}
        />
        <button type="submit">
          {mutation.isPending ? "반영 중…" : "업소 정정 반영"}
        </button>
      </fieldset>
      {mutation.isError && <p role="alert">{reportError(mutation.error)}</p>}
    </form>
  );
}
function Snapshot({
  value,
}: {
  value: components["schemas"]["RestaurantAdminSnapshot"] | undefined;
}) {
  if (!value) return <p>정보 없음</p>;
  const statuses: Record<string, string> = {
    OPEN: "영업 중",
    CLOSED: "폐업",
    UNKNOWN: "미확인",
  };
  return (
    <dl>
      <dt>가게 이름</dt>
      <dd>{value.name}</dd>
      <dt>주소</dt>
      <dd>{value.address}</dd>
      <dt>영업 상태</dt>
      <dd>{statuses[value.businessStatus ?? ""] ?? "미확인"}</dd>
      <dt>위도 / 경도</dt>
      <dd>
        {value.latitude ?? "미확인"} / {value.longitude ?? "미확인"}
      </dd>
      <dt>업소 전화번호</dt>
      <dd>{value.phone || "없음"}</dd>
      <dt>공개 설정</dt>
      <dd>
        {value.published ? "공개 대상 (원산지 조건 별도 적용)" : "비공개"}
      </dd>
    </dl>
  );
}
