import { useSyncExternalStore } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { getBookmarks } from "./api";
import { BookmarkToggle } from "./BookmarkToggle";

export function BookmarksPage() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const query = useInfiniteQuery({
    queryKey: ["private", session.member?.id, "bookmarks"],
    queryFn: ({ pageParam, signal }) => getBookmarks(pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    enabled: session.status === "authenticated",
    retry: false,
  });
  const items = query.data?.pages.flatMap((page) => page.items ?? []) ?? [];
  return (
    <main id="main-content">
      <h1>내 즐겨찾기</h1>
      {session.status === "loading" || session.status === "unknown" ? (
        <p role="status">로그인을 확인하고 있습니다.</p>
      ) : session.status !== "authenticated" ? (
        <p>
          즐겨찾기를 보려면 <Link to="/login">로그인</Link>해 주세요.
        </p>
      ) : query.isPending ? (
        <p role="status">즐겨찾기를 불러오고 있습니다.</p>
      ) : query.isError ? (
        <div role="alert">
          즐겨찾기를 불러오지 못했습니다.
          <button onClick={() => void query.refetch()}>다시 시도</button>
        </div>
      ) : (
        <>
          {items.length === 0 && (
            <p>
              아직 저장한 업소가 없습니다. 마음에 드는 업소를 즐겨찾기에
              담아보세요.
            </p>
          )}
          <ul className="saved-restaurants">
            {items.map((item) => (
              <li key={item.restaurantId}>
                <h2>
                  <Link to={`/restaurants/${item.restaurantId}/contact`}>
                    {item.name}
                  </Link>
                </h2>
                <p>{item.address}</p>
                {item.restaurantId && (
                  <BookmarkToggle restaurantId={item.restaurantId} />
                )}
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
    </main>
  );
}
