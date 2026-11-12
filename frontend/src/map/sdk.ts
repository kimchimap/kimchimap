import type { Bounds, Center } from "./types";
interface LatLng {
  getLat(): number;
  getLng(): number;
}
interface LatLngBounds {
  getSouthWest(): LatLng;
  getNorthEast(): LatLng;
}
export interface KakaoMap {
  getBounds(): LatLngBounds;
  getCenter(): LatLng;
  getLevel(): number;
  setLevel(level: number): void;
  setCenter(center: LatLng): void;
  relayout(): void;
}
export interface KakaoMarker {
  setMap(map: KakaoMap | null): void;
  setZIndex(index: number): void;
  setOpacity(opacity: number): void;
}
interface Place {
  id: string;
  place_name: string;
  address_name: string;
  x: string;
  y: string;
}
export interface KakaoMaps {
  load(callback: () => void): void;
  Map: new (
    element: HTMLElement,
    options: { center: LatLng; level: number },
  ) => KakaoMap;
  LatLng: new (latitude: number, longitude: number) => LatLng;
  Marker: new (options: {
    map: KakaoMap;
    position: LatLng;
    title: string;
    clickable: boolean;
  }) => KakaoMarker;
  event: {
    addListener(
      target: KakaoMap | KakaoMarker,
      type: string,
      handler: (event?: { latLng: LatLng }) => void,
    ): void;
    removeListener(
      target: KakaoMap | KakaoMarker,
      type: string,
      handler: (event?: { latLng: LatLng }) => void,
    ): void;
  };
  services: {
    Places: new () => {
      keywordSearch(
        keyword: string,
        callback: (results: Place[], status: string) => void,
        options: { size: number },
      ): void;
    };
    Status: { OK: string; ZERO_RESULT: string };
  };
}
declare global {
  interface Window {
    kakao?: { maps: KakaoMaps };
  }
}
let loading: Promise<KakaoMaps> | undefined;
let ready: KakaoMaps | undefined;
export function loadMapSdk(key: string): Promise<KakaoMaps> {
  if (!key) return Promise.reject(new Error("MAP_KEY_MISSING"));
  if (ready) return Promise.resolve(ready);
  if (loading) return loading;
  loading = new Promise<KakaoMaps>((resolve, reject) => {
    const script = document.createElement("script");
    script.id = "kakao-map-sdk";
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&libraries=services&appkey=${encodeURIComponent(key)}`;
    let finished = false;
    const fail = () => {
      if (finished) return;
      finished = true;
      clearTimeout(timeout);
      script.onload = script.onerror = null;
      script.remove();
      reject(new Error("MAP_SDK_UNAVAILABLE"));
    };
    const timeout = window.setTimeout(fail, 15000);
    script.onerror = fail;
    script.onload = () => {
      if (finished) return;
      const maps = window.kakao?.maps;
      if (!maps?.load) {
        fail();
        return;
      }
      try {
        maps.load(() => {
          if (finished) return;
          finished = true;
          clearTimeout(timeout);
          script.onload = script.onerror = null;
          ready = maps;
          resolve(maps);
        });
      } catch {
        fail();
      }
    };
    document.head.append(script);
  }).catch((error: unknown) => {
    loading = undefined;
    throw error;
  });
  return loading;
}
export function boundsOf(map: KakaoMap): Bounds {
  const bounds = map.getBounds(),
    southWest = bounds.getSouthWest(),
    northEast = bounds.getNorthEast();
  return {
    south: southWest.getLat(),
    west: southWest.getLng(),
    north: northEast.getLat(),
    east: northEast.getLng(),
  };
}
export interface Region {
  id: string;
  name: string;
  address: string;
  center: Center;
}
export async function findRegions(
  keyword: string,
  key: string,
): Promise<Region[]> {
  const sdk = await loadMapSdk(key);
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(
      () => reject(new Error("REGION_TIMEOUT")),
      10000,
    );
    new sdk.services.Places().keywordSearch(
      keyword,
      (places, status) => {
        clearTimeout(timer);
        if (status === sdk.services.Status.ZERO_RESULT) {
          resolve([]);
          return;
        }
        if (status !== sdk.services.Status.OK) {
          reject(new Error("REGION_UNAVAILABLE"));
          return;
        }
        resolve(
          places
            .map((place) => ({
              id: place.id,
              name: place.place_name,
              address: place.address_name,
              center: { latitude: Number(place.y), longitude: Number(place.x) },
            }))
            .filter(
              (place) =>
                Number.isFinite(place.center.latitude) &&
                Number.isFinite(place.center.longitude) &&
                Math.abs(place.center.latitude) <= 90 &&
                Math.abs(place.center.longitude) <= 180,
            ),
        );
      },
      { size: 5 },
    );
  });
}
