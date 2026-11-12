import { useState, useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { reportError } from "../report/api";
import {
  correctionDraft,
  getAdminRestaurant,
  type RestaurantCorrection,
} from "../restaurant/admin-api";
import { RestaurantCorrectionFields } from "../restaurant/RestaurantCorrectionFields";
import { resolveReport, type Detail } from "./api";

export function InformationReportResolutionForm({
  report,
}: {
  report: Detail;
}) {
  const client = useQueryClient();
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [decision, setDecision] = useState("DISMISSED");
  const [reason, setReason] = useState("");
  const [editRestaurant, setEditRestaurant] = useState(false);
  const [draft, setDraft] = useState<RestaurantCorrection | null>(null);
  const editing = decision === "RESOLVED" && editRestaurant;
  const restaurant = useQuery({
    queryKey: [
      "private",
      session.member?.id,
      "admin-restaurant",
      report.restaurantId,
    ],
    queryFn: ({ signal }) => getAdminRestaurant(report.restaurantId!, signal),
    enabled: editing && !!report.restaurantId,
    retry: false,
  });
  const current =
    draft ??
    (restaurant.data?.version !== undefined
      ? correctionDraft(restaurant.data)
      : null);
  const mutation = useMutation({
    mutationFn: () =>
      resolveReport(report.id!, {
        expectedVersion: report.version!,
        decision,
        reason,
        ...(editing && current
          ? { restaurantCorrection: { ...current, reason } }
          : {}),
      }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ["private"] }),
        client.invalidateQueries({ queryKey: ["restaurant"] }),
        client.invalidateQueries({ queryKey: ["restaurant-search"] }),
        client.invalidateQueries({ queryKey: ["like-count"] }),
      ]);
    },
  });
  return (
    <form
      className="report-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (!mutation.isPending && (!editing || current)) mutation.mutate();
      }}
    >
      <p>
        근거를 확인한 뒤 처리 내용을 남겨 주세요. 업소 정정을 선택하면 신고
        처리와 함께 반영합니다.
      </p>
      <Link to="/admin/origins">원산지 근거 확인·정정</Link>
      <fieldset disabled={mutation.isPending}>
        <legend>관리자 처리</legend>
        <label htmlFor="issue-decision">처리 결과</label>
        <select
          id="issue-decision"
          value={decision}
          onChange={(e) => setDecision(e.target.value)}
        >
          <option value="DISMISSED">기각</option>
          <option value="RESOLVED">처리 완료</option>
        </select>
        {decision === "RESOLVED" && (
          <label>
            <input
              type="checkbox"
              checked={editRestaurant}
              onChange={(e) => setEditRestaurant(e.target.checked)}
            />
            업소 정보도 함께 정정
          </label>
        )}
        {editing &&
          (restaurant.isPending ? (
            <p role="status">업소 정보를 불러오고 있습니다.</p>
          ) : restaurant.isError || !current ? (
            <p role="alert">
              업소 정보를 확인하지 못했습니다. 최신 내용을 다시 확인해 주세요.
            </p>
          ) : (
            <RestaurantCorrectionFields value={current} onChange={setDraft} />
          ))}
        <label htmlFor="issue-reason">확인 내용과 처리 사유</label>
        <textarea
          id="issue-reason"
          required
          maxLength={2000}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <button
          type="submit"
          disabled={editing && (!current || restaurant.isError)}
        >
          {mutation.isPending ? "처리 중…" : "신고 처리 반영"}
        </button>
      </fieldset>
      {mutation.isError && <p role="alert">{reportError(mutation.error)}</p>}
    </form>
  );
}
