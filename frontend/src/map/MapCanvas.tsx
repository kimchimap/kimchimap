import { useEffect, useEffectEvent, useRef, useState } from "react";
import { boundsOf, loadMapSdk, type KakaoMap, type KakaoMaps } from "./sdk";
import type { Bounds, Center, RestaurantItem } from "./types";
export function MapCanvas({
  center,
  items,
  selected,
  onBounds,
  onSelect,
  onPick,
  pickedLocation,
}: {
  center: Center;
  items: RestaurantItem[];
  selected: string;
  onBounds: (bounds: Bounds) => void;
  onSelect: (id: string) => void;
  onPick?: (point: Center) => void;
  pickedLocation?: Center | undefined;
}) {
  const node = useRef<HTMLDivElement>(null);
  const [instance, setInstance] = useState<{ map: KakaoMap; sdk: KakaoMaps }>();
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState("loading");
  const initial = useRef(center);
  const notifyBounds = useEffectEvent(onBounds);
  const select = useEffectEvent(onSelect);
  const pick = useEffectEvent((point: Center) => onPick?.(point));
  const key = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY ?? "";
  useEffect(() => {
    let cancelled = false;
    let release = () => {};
    void loadMapSdk(key)
      .then((sdk) => {
        if (cancelled || !node.current) return;
        const element = node.current;
        const map = new sdk.Map(element, {
          center: new sdk.LatLng(
            initial.current.latitude,
            initial.current.longitude,
          ),
          level: 5,
        });
        let timer = 0;
        const idle = () => {
          clearTimeout(timer);
          timer = window.setTimeout(() => notifyBounds(boundsOf(map)), 300);
        };
        const click = (event?: {
          latLng: { getLat(): number; getLng(): number };
        }) => {
          if (event)
            pick({
              latitude: event.latLng.getLat(),
              longitude: event.latLng.getLng(),
            });
        };
        sdk.event.addListener(map, "click", click);
        sdk.event.addListener(map, "dragend", idle);
        sdk.event.addListener(map, "zoom_changed", idle);
        const resize = new ResizeObserver(() => {
          const current = map.getCenter();
          map.relayout();
          map.setCenter(current);
        });
        resize.observe(element);
        release = () => {
          clearTimeout(timer);
          resize.disconnect();
          sdk.event.removeListener(map, "click", click);
          sdk.event.removeListener(map, "dragend", idle);
          sdk.event.removeListener(map, "zoom_changed", idle);
          element.replaceChildren();
        };
        setInstance({ map, sdk });
        setStatus("ready");
        idle();
      })
      .catch(() => {
        if (!cancelled) setStatus(key ? "failed" : "missing");
      });
    return () => {
      cancelled = true;
      release();
    };
  }, [key, attempt]);
  useEffect(() => {
    if (!instance) return;
    instance.map.setCenter(
      new instance.sdk.LatLng(center.latitude, center.longitude),
    );
    notifyBounds(boundsOf(instance.map));
  }, [instance, center]);
  useEffect(() => {
    if (!instance) return;
    const { sdk, map } = instance;
    const markers = items
      .filter(
        (item) => item.id && item.latitude != null && item.longitude != null,
      )
      .map((item) => {
        const marker = new sdk.Marker({
          map,
          position: new sdk.LatLng(item.latitude!, item.longitude!),
          title: item.name ?? "업소",
          clickable: true,
        });
        marker.setZIndex(item.id === selected ? 10 : 1);
        marker.setOpacity(selected && item.id !== selected ? 0.65 : 1);
        const click = () => select(item.id!);
        sdk.event.addListener(marker, "click", click);
        return () => {
          sdk.event.removeListener(marker, "click", click);
          marker.setMap(null);
        };
      });
    return () => markers.forEach((remove) => remove());
  }, [instance, items, selected]);
  useEffect(() => {
    if (!instance || !selected) return;
    const item = items.find((item) => item.id === selected);
    if (item?.latitude == null || item.longitude == null) return;
    const previous = instance.map.getCenter();
    instance.map.setCenter(
      new instance.sdk.LatLng(item.latitude, item.longitude),
    );
    return () => instance.map.setCenter(previous);
  }, [instance, selected, items]);
  useEffect(() => {
    if (!instance || !pickedLocation) return;
    const marker = new instance.sdk.Marker({
      map: instance.map,
      position: new instance.sdk.LatLng(
        pickedLocation.latitude,
        pickedLocation.longitude,
      ),
      title: "제보할 위치",
      clickable: false,
    });
    marker.setZIndex(20);
    return () => marker.setMap(null);
  }, [instance, pickedLocation]);
  return (
    <section className="map-pane" aria-label="업소 지도">
      <div ref={node} className="map-surface" aria-label="카카오 지도" />
      {status !== "ready" && (
        <div className="map-fallback" role="status">
          <strong>
            {status === "loading"
              ? "지도를 불러오고 있습니다"
              : status === "missing"
                ? "지도 키가 설정되지 않았습니다"
                : "카카오 지도를 불러오지 못했습니다"}
          </strong>
          <p>지역 선택과 업소 목록은 계속 이용할 수 있습니다.</p>
          {status === "failed" && (
            <button
              onClick={() => {
                setStatus("loading");
                setAttempt((value) => value + 1);
              }}
            >
              지도 다시 시도
            </button>
          )}
        </div>
      )}
      {instance && status === "ready" && (
        <div className="map-zoom" aria-label="지도 확대·축소">
          <button
            onClick={() => {
              const point = instance.map.getCenter();
              onPick?.({ latitude: point.getLat(), longitude: point.getLng() });
            }}
          >
            지도 중심 위치 선택
          </button>
          <button
            aria-label="지도 확대"
            onClick={() =>
              instance.map.setLevel(Math.max(1, instance.map.getLevel() - 1))
            }
          >
            ＋
          </button>
          <button
            aria-label="지도 축소"
            onClick={() =>
              instance.map.setLevel(Math.min(14, instance.map.getLevel() + 1))
            }
          >
            −
          </button>
        </div>
      )}
    </section>
  );
}
