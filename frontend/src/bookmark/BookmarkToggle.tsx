import { useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { getBookmarkStatus, setBookmark } from "./api";

export function BookmarkToggle({ restaurantId }: { restaurantId: string }) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const client = useQueryClient();
  const owner = session.member?.id;
  const query = useQuery({
    queryKey: ["private", owner, "bookmark", restaurantId],
    queryFn: ({ signal }) => getBookmarkStatus(restaurantId, signal),
    enabled: session.status === "authenticated",
    retry: false,
  });
  const mutation = useMutation({
    mutationFn: (saved: boolean) => setBookmark(restaurantId, saved),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["private", owner] });
    },
  });
  if (session.status !== "authenticated")
    return <Link to="/login">로그인하고 즐겨찾기</Link>;
  return (
    <div>
      <button
        aria-pressed={Boolean(query.data?.saved)}
        disabled={query.isPending || query.isError || mutation.isPending}
        onClick={() => mutation.mutate(!query.data?.saved)}
      >
        {query.data?.saved ? "즐겨찾기 해제" : "즐겨찾기 추가"}
      </button>
      {(query.isError || mutation.isError) && (
        <p role="alert">
          즐겨찾기를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.
        </p>
      )}
      {query.isError && (
        <button onClick={() => void query.refetch()}>다시 확인</button>
      )}
    </div>
  );
}
