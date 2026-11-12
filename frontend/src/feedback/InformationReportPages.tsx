import { useRef, useState, useSyncExternalStore } from "react";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { getRestaurant } from "../api/client";
import { MemberGate } from "../auth/MemberGate";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { PhotoUpload } from "../media/PhotoUpload";
import { PrivatePhoto } from "../report/PrivatePhoto";
import { reportError } from "../report/api";
import { InformationReportResolutionForm } from "./InformationReportResolutionForm";
import {
  categories,
  states,
  getReport,
  listReports,
  submitReport,
  type Submission,
} from "./api";

export function NewInformationReportPage() {
  const [params] = useSearchParams();
  const restaurantId = params.get("restaurantId");
  return (
    <main id="main-content">
      <h1>정보 오류 신고</h1>
      <MemberGate>
        {restaurantId ? (
          <NewReport restaurantId={restaurantId} />
        ) : (
          <p>가게 상세에서 신고할 가게를 선택해 주세요.</p>
        )}
      </MemberGate>
    </main>
  );
}
function NewReport({ restaurantId }: { restaurantId: string }) {
  const navigate = useNavigate(),
    client = useQueryClient();
  const [category, setCategory] = useState("ORIGIN_CHANGED"),
    [description, setDescription] = useState("");
  const [mediaIds, setMediaIds] = useState<string[]>([]);
  const request = useRef({ body: "", key: "" });
  const restaurant = useQuery({
    queryKey: ["restaurant", restaurantId],
    queryFn: ({ signal }) => getRestaurant(restaurantId, signal),
    retry: false,
  });
  const mutation = useMutation({
    mutationFn: async (input: Submission) => {
      const body = JSON.stringify(input);
      if (request.current.body !== body)
        request.current = { body, key: crypto.randomUUID() };
      return submitReport(input, request.current.key);
    },
    onSuccess: async (data) => {
      await client.invalidateQueries({ queryKey: ["private"] });
      if (data.id) await navigate(`/information-reports/${data.id}`);
    },
  });
  if (restaurant.isPending)
    return <p role="status">가게 정보를 불러오고 있습니다.</p>;
  if (restaurant.isError)
    return <p role="alert">신고할 가게를 찾지 못했습니다.</p>;
  return (
    <form
      className="report-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (!mutation.isPending)
          mutation.mutate({ restaurantId, category, description, mediaIds });
      }}
    >
      <h2>{restaurant.data.name}</h2>
      <p>
        신고 내용과 사진은 본인과 관리자만 확인합니다. 신고만으로 공개 정보가
        변경되지는 않습니다.
      </p>
      <fieldset disabled={mutation.isPending}>
        <legend>오류 내용</legend>
        <label htmlFor="issue-category">신고 유형</label>
        <select
          id="issue-category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          {Object.entries(categories).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <label htmlFor="issue-description">어떤 정보가 다른가요?</label>
        <textarea
          id="issue-description"
          required
          maxLength={2000}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <p>
          개인 연락처 등 불필요한 개인정보는 적지 마세요. 사진은 최대 3장까지
          선택할 수 있습니다.
        </p>
        {mediaIds.map((id, index) => (
          <div key={id}>
            <PrivatePhoto id={id} index={index} />
            <button
              type="button"
              onClick={() =>
                setMediaIds(mediaIds.filter((current) => current !== id))
              }
            >
              신고 사진 {index + 1} 제외
            </button>
          </div>
        ))}
        {mediaIds.length < 3 && (
          <PhotoUpload
            onUploaded={(image) => {
              if (image.id)
                setMediaIds((ids) =>
                  ids.includes(image.id!) ? ids : [...ids, image.id!],
                );
            }}
          />
        )}
        <button type="submit">
          {mutation.isPending ? "접수 중…" : "신고 접수"}
        </button>
      </fieldset>
      {mutation.isError && <p role="alert">{reportError(mutation.error)}</p>}
    </form>
  );
}
export function InformationReportsPage({ admin = false }: { admin?: boolean }) {
  return (
    <main id="main-content">
      <h1>{admin ? "정보 오류 신고 관리" : "내 정보 오류 신고"}</h1>
      <MemberGate admin={admin}>
        <ReportList admin={admin} />
      </MemberGate>
    </main>
  );
}
function ReportList({ admin }: { admin: boolean }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [state, setState] = useState(admin ? "PENDING" : "");
  const query = useInfiniteQuery({
    queryKey: [
      "private",
      session.member?.id,
      "information-reports",
      admin,
      state,
    ],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      listReports(admin, state, pageParam, signal),
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
  });
  const items = query.data?.pages.flatMap((page) => page.items ?? []) ?? [];
  return (
    <>
      <label htmlFor="issue-state">신고 상태</label>
      <select
        id="issue-state"
        value={state}
        onChange={(e) => setState(e.target.value)}
      >
        <option value="">전체</option>
        {Object.entries(states).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      {query.isPending ? (
        <p role="status">신고를 불러오고 있습니다.</p>
      ) : query.isError ? (
        <p role="alert">신고 목록을 불러오지 못했습니다.</p>
      ) : (
        <>
          {!items.length && <p>해당하는 신고가 없습니다.</p>}
          <ul>
            {items.map((item) => (
              <li key={item.id}>
                <Link
                  to={`${admin ? "/admin" : ""}/information-reports/${item.id}`}
                >
                  {item.restaurantName} · {categories[item.category ?? ""]}
                </Link>
                <p>{states[item.state ?? ""]}</p>
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
export function InformationReportDetailPage({
  admin = false,
}: {
  admin?: boolean;
}) {
  const { id = "" } = useParams();
  return (
    <main id="main-content">
      <h1>{admin ? "정보 오류 신고 처리" : "신고 상세"}</h1>
      <Link to={`${admin ? "/admin" : ""}/information-reports`}>신고 목록</Link>
      <MemberGate admin={admin}>
        <ReportDetail key={id} id={id} admin={admin} />
      </MemberGate>
    </main>
  );
}
function ReportDetail({ id, admin }: { id: string; admin: boolean }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const query = useQuery({
    queryKey: ["private", session.member?.id, "information-report", admin, id],
    queryFn: ({ signal }) => getReport(admin, id, signal),
    retry: false,
  });
  if (query.isPending) return <p role="status">신고를 불러오고 있습니다.</p>;
  if (query.isError) return <p role="alert">신고를 확인하지 못했습니다.</p>;
  const data = query.data;
  return (
    <>
      <h2>{data.restaurantName}</h2>
      {admin && data.restaurantId && (
        <Link to={`/admin/restaurants/${data.restaurantId}`}>
          업소 정보·정정 이력
        </Link>
      )}
      <p>
        {categories[data.category ?? ""]} · {states[data.state ?? ""]}
      </p>
      <p className="report-description">{data.description}</p>
      {data.mediaIds?.map((media, index) => (
        <PrivatePhoto key={media} id={media} index={index} />
      ))}
      {data.resolutionReason && (
        <section>
          <h2>처리 결과</h2>
          <p>{data.resolutionReason}</p>
          <p>
            {data.resolvedAt
              ? new Date(data.resolvedAt).toLocaleString("ko-KR", {
                  timeZone: "Asia/Seoul",
                })
              : ""}
          </p>
        </section>
      )}
      {admin && data.state === "PENDING" && (
        <InformationReportResolutionForm key={data.version} report={data} />
      )}
      <button onClick={() => void query.refetch()}>최신 내용 다시 확인</button>
    </>
  );
}
