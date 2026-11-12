import "./map.css";
import { searchRestaurants } from "./api";
import { useEffect, useMemo, useRef, useState } from "react";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { ApiError, getSystemStatus } from "../api/client";
import { usages } from "../report/api";
import { RestaurantDetail } from "../restaurant/RestaurantDetail";
import { MapCanvas } from "./MapCanvas";
import { RegionSearch } from "./RegionSearch";
import { OriginFilters } from "./OriginFilters";
import { defaultKimchiFilters } from "./kimchi-filters";
import {
  initialCenter,
  type Bounds,
  type Center,
  type Filters,
  type SearchRequest,
} from "./types";
export function MapPage() {
  const [center, setCenter] = useState(initialCenter);
  const [bounds, setBounds] = useState<Bounds>();
  const [mode, setMode] = useState<"bounds" | "radius">("bounds");
  const [radius, setRadius] = useState(3000);
  const [filters, setFilters] = useState<Filters>(defaultKimchiFilters);
  const [pickedLocation, setPickedLocation] = useState<Center>();
  const [selected, setSelected] = useState("");
  const [expanded, setExpanded] = useState(false);
  const [locationNotice, setLocationNotice] = useState("");
  const [locating, setLocating] = useState(false);
  const generation = useRef(0);
  const closeButton = useRef<HTMLButtonElement>(null);
  const listHeading = useRef<HTMLHeadingElement>(null);
  const returnTarget = useRef("");
  const dragStart = useRef(0);
  const dragged = useRef(false);
  useEffect(
    () => () => {
      generation.current++;
    },
    [],
  );
  useEffect(() => {
    if (selected) closeButton.current?.focus();
    else if (returnTarget.current) {
      const target = document.getElementById(
        `restaurant-result-${returnTarget.current}`,
      );
      (target ?? listHeading.current)?.focus();
      returnTarget.current = "";
    }
  }, [selected]);
  const request = useMemo<SearchRequest>(
    () => ({
      ...filters,
      ...(mode === "bounds" && bounds
        ? { bounds }
        : { center, radiusMeters: radius }),
      limit: 50,
    }),
    [filters, mode, bounds, center, radius],
  );
  const results = useInfiniteQuery({
    queryKey: ["restaurant-search", request],
    queryFn: ({ signal, pageParam }) =>
      searchRestaurants(
        { ...request, ...(pageParam ? { cursor: pageParam } : {}) },
        signal,
      ),
    initialPageParam: "",
    getNextPageParam: (last, pages) =>
      pages.reduce((total, page) => total + (page.items?.length ?? 0), 0) >= 200
        ? undefined
        : last.nextCursor || undefined,
    retry: false,
  });
  const items = useMemo(
    () => results.data?.pages.flatMap((page) => page.items ?? []) ?? [],
    [results.data],
  );
  const last = results.data?.pages.at(-1);
  const status = useQuery({
    queryKey: ["system-status"],
    queryFn: ({ signal }) => getSystemStatus(signal),
    retry: false,
  });
  function moveTo(next: Center) {
    generation.current++;
    setLocating(false);
    setLocationNotice("");
    setCenter({ ...next });
    setBounds(undefined);
  }
  function locate() {
    if (!navigator.geolocation) {
      setLocationNotice(
        "이 브라우저에서 현재 위치를 확인할 수 없습니다. 지역을 선택해 주세요.",
      );
      return;
    }
    const current = ++generation.current;
    setLocating(true);
    setLocationNotice("");
    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (current !== generation.current) return;
        moveTo({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        setMode("radius");
      },
      (error) => {
        if (current !== generation.current) return;
        setLocating(false);
        setLocationNotice(
          error.code === 1
            ? "위치 권한이 거부되었습니다. 지역 선택이나 지도 이동으로 이용해 주세요."
            : error.code === 3
              ? "위치 확인 시간이 초과되었습니다. 다시 시도하거나 지역을 선택해 주세요."
              : "현재 위치를 확인하지 못했습니다. 지역을 선택해 주세요.",
        );
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    );
  }
  function select(id: string) {
    returnTarget.current = id;
    setSelected(id);
    setExpanded(true);
  }
  function close() {
    setSelected("");
  }
  return (
    <main id="main-content" className="map-page">
      <div className="map-heading">
        <div>
          <p className="eyebrow">원산지를 알고 고르는 한 끼</p>
          <h1>우리 동네 김치, 어디에서 왔을까요?</h1>
        </div>
        <Link to="/about/data">정보 확인 기준</Link>
      </div>
      <div className="search-toolbar">
        <RegionSearch onSelect={moveTo} />
        <button onClick={locate} disabled={locating}>
          {locating ? "위치 확인 중…" : "내 주변 찾기"}
        </button>
        <OriginFilters
          value={filters}
          onApply={(value) => {
            setFilters(value);
            setSelected("");
          }}
        />
      </div>
      <div className="search-range">
        <label>
          검색 범위
          <select
            value={mode}
            onChange={(event) =>
              setMode(event.target.value as "bounds" | "radius")
            }
          >
            <option value="bounds">현재 지도 영역</option>
            <option value="radius">선택 지역 중심 반경</option>
          </select>
        </label>
        {mode === "radius" && (
          <label>
            반경
            <select
              value={radius}
              onChange={(event) => setRadius(Number(event.target.value))}
            >
              <option value={1000}>1km</option>
              <option value={3000}>3km</option>
              <option value={10000}>10km</option>
              <option value={50000}>50km</option>
            </select>
          </label>
        )}
        <Link to="/likes">좋아요</Link>
        {!bounds && mode === "bounds" && (
          <span>지도 사용 전에는 선택 지역 중심 3km를 조회합니다.</span>
        )}
        <span className="connection" role="status">
          {status.isPending
            ? "서버 연결을 확인하고 있습니다."
            : status.isError
              ? "서버 연결을 확인하지 못했습니다."
              : "서버가 정상적으로 연결되었습니다."}
          {status.isError && (
            <button onClick={() => void status.refetch()}>다시 시도</button>
          )}
        </span>
      </div>
      {locationNotice && (
        <p role="status" className="location-notice">
          {locationNotice}
        </p>
      )}
      {pickedLocation && (
        <section aria-label="제보할 위치" className="location-notice">
          <p>
            선택한 위치의 가게를 제보합니다. 주소와 표시판 사진은 다음 화면에서
            확인해 주세요.
          </p>
          <Link
            to={`/reports/new?latitude=${pickedLocation.latitude}&longitude=${pickedLocation.longitude}`}
          >
            이 위치 제보하기
          </Link>
          <button onClick={() => setPickedLocation(undefined)}>
            위치 선택 취소
          </button>
        </section>
      )}
      <div className={`map-workspace ${expanded ? "sheet-expanded" : ""}`}>
        <MapCanvas
          center={center}
          items={items}
          selected={selected}
          onBounds={(next) => {
            if (!selected) setBounds(next);
          }}
          onSelect={(id) => {
            setPickedLocation(undefined);
            select(id);
          }}
          onPick={(point) => {
            setSelected("");
            setPickedLocation(point);
          }}
          pickedLocation={pickedLocation}
        />
        <section
          className="results-sheet"
          aria-label={selected ? "선택한 업소 상세" : "업소 검색 결과"}
        >
          <button
            className="sheet-handle"
            aria-expanded={expanded}
            onPointerDown={(event) => {
              dragStart.current = event.clientY;
              dragged.current = false;
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerUp={(event) => {
              const delta = event.clientY - dragStart.current;
              if (Math.abs(delta) > 30) {
                dragged.current = true;
                setExpanded(delta < 0);
              }
            }}
            onClick={() => {
              if (!dragged.current) setExpanded((value) => !value);
              dragged.current = false;
            }}
          >
            {expanded ? "목록·상세 접기" : "목록·상세 펼치기"}
          </button>
          <div className="sheet-content">
            {selected ? (
              <>
                <button ref={closeButton} onClick={close}>
                  상세 닫기 · 목록으로
                </button>
                <RestaurantDetail id={selected} />
              </>
            ) : (
              <>
                <div className="section-heading">
                  <h2 ref={listHeading} tabIndex={-1}>
                    주변 업소 <span>{items.length}개 표시</span>
                  </h2>
                  <button
                    onClick={() => void results.refetch()}
                    disabled={results.isFetching}
                  >
                    다시 검색
                  </button>
                </div>
                <p className="data-notice">
                  원산지 정보는 제보·검수 근거에 따라 제공합니다. 정보가 없다는
                  것은 수입산이라는 뜻이 아닙니다.
                </p>
                {results.isPending ? (
                  <p role="status">조건에 맞는 업소를 찾고 있습니다.</p>
                ) : results.isError ? (
                  <div role="alert">
                    <p>
                      {results.error instanceof ApiError &&
                      results.error.status === 429
                        ? "검색 요청이 많습니다. 잠시 후 다시 검색해 주세요."
                        : "검색 결과를 불러오지 못했습니다. 다시 검색해 주세요."}
                    </p>
                  </div>
                ) : (
                  <>
                    {last?.zoomRequired && (
                      <p role="status">
                        검색 영역이 넓습니다. 지도를 확대하거나 반경을 줄여
                        주세요.
                      </p>
                    )}
                    {!items.length && !last?.zoomRequired && (
                      <div className="empty-message">
                        <h3>조건에 맞는 업소가 없습니다</h3>
                        <p>
                          지역을 옮기거나 원산지 조건을 바꿔 보세요. 아직
                          확인되지 않은 업소는 조건에 포함되지 않습니다.
                        </p>
                      </div>
                    )}
                    {(last?.truncated || last?.nextCursor) && (
                      <p className="data-notice">
                        일부 결과만 표시합니다. 지도와 목록은 표시된 업소에 같은
                        조건을 적용합니다.
                      </p>
                    )}
                    {last?.notice && <p>{last.notice}</p>}
                    <ol className="restaurant-results">
                      {items.map((item, index) => (
                        <li key={item.id}>
                          <button
                            id={`restaurant-result-${item.id}`}
                            onClick={() => item.id && select(item.id)}
                          >
                            <span className="result-number">{index + 1}</span>
                            <span>
                              <strong>{item.name}</strong>
                              <small>{item.address}</small>
                              <small>
                                검색 중심에서{" "}
                                {item.distanceMeters != null
                                  ? item.distanceMeters < 1000
                                    ? `${Math.round(item.distanceMeters)}m`
                                    : `${(item.distanceMeters / 1000).toFixed(1)}km`
                                  : "거리 미상"}
                              </small>
                              {item.matchedScopes?.length ? (
                                item.matchedScopes.map((scope) => (
                                  <span
                                    className="matched-scope"
                                    key={`${scope.groupIndex}-${scope.id}`}
                                  >
                                    조건 일치: {scope.name} ·{" "}
                                    {usages[scope.usage ?? ""] ?? "용도 미확인"}
                                  </span>
                                ))
                              ) : (
                                <small>원산지는 상세에서 확인해 주세요</small>
                              )}
                            </span>
                            <span aria-hidden="true">›</span>
                          </button>
                        </li>
                      ))}
                    </ol>
                    {results.hasNextPage && (
                      <button
                        className="load-more"
                        disabled={results.isFetchingNextPage}
                        onClick={() => void results.fetchNextPage()}
                      >
                        {results.isFetchingNextPage
                          ? "더 불러오는 중…"
                          : "업소 더 보기"}
                      </button>
                    )}
                    {items.length >= 200 && (
                      <p>
                        최대 200개를 표시했습니다. 지도를 확대하거나 필터를 좁혀
                        주세요.
                      </p>
                    )}
                  </>
                )}
              </>
            )}
          </div>
        </section>
      </div>
    </main>
  );
}
