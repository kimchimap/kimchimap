import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { ApiError, getRestaurant } from "../api/client";
import { RestaurantContact } from "./RestaurantContact";
import { BookmarkToggle } from "../bookmark/BookmarkToggle";

export function RestaurantContactPage() {
  const { id = "" } = useParams();
  const restaurant = useQuery({
    queryKey: ["restaurant", id],
    queryFn: ({ signal }) => getRestaurant(id, signal),
    retry: false,
  });

  return (
    <main id="main-content">
      <Link to="/">처음으로 돌아가기</Link>
      {restaurant.isPending ? (
        <p role="status">업소 연락처를 불러오고 있습니다.</p>
      ) : restaurant.isError ? (
        <div role="alert">
          <h1>업소 연락처를 확인하지 못했습니다</h1>
          {restaurant.error instanceof ApiError &&
          restaurant.error.status === 404 ? (
            <p>공개된 업소 정보를 찾을 수 없습니다.</p>
          ) : (
            <>
              <p>잠시 후 다시 시도해 주세요.</p>
              <button onClick={() => void restaurant.refetch()}>
                다시 시도
              </button>
            </>
          )}
        </div>
      ) : (
        <>
          <h1>{restaurant.data.name}</h1>
          <p>{restaurant.data.address}</p>
          <RestaurantContact contact={restaurant.data.contact} />
          <BookmarkToggle restaurantId={id} />
          <Link to={`/reports/new?restaurantId=${encodeURIComponent(id)}`}>
            원산지 제보
          </Link>
        </>
      )}
    </main>
  );
}
