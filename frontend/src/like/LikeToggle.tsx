import { useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { getLikeCount, getLikeStatus, setLike } from "./api";

export function LikeToggle({ restaurantId }: { restaurantId: string }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const client = useQueryClient();
  const count = useQuery({
    queryKey: ["like-count", restaurantId],
    queryFn: ({ signal }) => getLikeCount(restaurantId, signal),
    retry: false,
  });
  const total = (
    <p aria-live="polite">
      {count.isPending
        ? "좋아요 수 확인 중"
        : count.isError
          ? "좋아요 수를 불러오지 못했습니다."
          : `좋아요 ${count.data.count ?? 0}개`}
    </p>
  );
  const owner = session.member?.id;
  const query = useQuery({
    queryKey: ["private", owner, "like", restaurantId],
    queryFn: ({ signal }) => getLikeStatus(restaurantId, signal),
    enabled: session.status === "authenticated",
    retry: false,
  });
  const mutation = useMutation({
    mutationFn: (saved: boolean) => setLike(restaurantId, saved),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ["private", owner] }),
        client.invalidateQueries({ queryKey: ["like-count", restaurantId] }),
      ]);
    },
  });
  if (session.status !== "authenticated")
    return (
      <div>
        {total}
        <Link to="/login">로그인하고 좋아요</Link>
      </div>
    );
  return (
    <div>
      {total}
      <button
        aria-pressed={Boolean(query.data?.saved)}
        disabled={query.isPending || query.isError || mutation.isPending}
        onClick={() => mutation.mutate(!query.data?.saved)}
      >
        {query.data?.saved ? "좋아요 해제" : "좋아요 추가"}
      </button>
      {(query.isError || mutation.isError) && (
        <p role="alert">
          좋아요를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.
        </p>
      )}
      {query.isError && (
        <button onClick={() => void query.refetch()}>다시 확인</button>
      )}
    </div>
  );
}
