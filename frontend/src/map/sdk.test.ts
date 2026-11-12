import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { KakaoMaps } from "./sdk";
beforeEach(() => {
  vi.resetModules();
});
afterEach(() => {
  vi.useRealTimers();
  document.getElementById("kakao-map-sdk")?.remove();
  delete window.kakao;
});
it("키가 없으면 네트워크 스크립트를 만들지 않는다", async () => {
  const { loadMapSdk } = await import("./sdk");
  await expect(loadMapSdk("")).rejects.toThrow("MAP_KEY_MISSING");
  expect(document.getElementById("kakao-map-sdk")).toBeNull();
});
it("동시 호출은 한 스크립트와 완료 시점을 공유한다", async () => {
  const { loadMapSdk } = await import("./sdk");
  let loaded = () => {};
  const maps = {
    load: vi.fn((callback) => {
      loaded = callback;
    }),
  } as unknown as KakaoMaps;
  window.kakao = { maps };
  const first = loadMapSdk("test-javascript-key"),
    second = loadMapSdk("test-javascript-key");
  expect(first).toBe(second);
  const script = document.getElementById("kakao-map-sdk") as HTMLScriptElement;
  expect(new URL(script.src).searchParams.get("autoload")).toBe("false");
  expect(document.querySelectorAll("#kakao-map-sdk")).toHaveLength(1);
  script.dispatchEvent(new Event("load"));
  loaded();
  await expect(first).resolves.toBe(maps);
  await expect(loadMapSdk("test-javascript-key")).resolves.toBe(maps);
});
it("실패와 시간 초과 뒤 기존 요소를 정리하고 명시적 재시도를 허용한다", async () => {
  vi.useFakeTimers();
  const { loadMapSdk } = await import("./sdk");
  const first = loadMapSdk("test-key");
  const failed = expect(first).rejects.toThrow("MAP_SDK_UNAVAILABLE");
  document.getElementById("kakao-map-sdk")!.dispatchEvent(new Event("error"));
  await failed;
  expect(document.getElementById("kakao-map-sdk")).toBeNull();
  const retry = loadMapSdk("test-key");
  const timedOut = expect(retry).rejects.toThrow("MAP_SDK_UNAVAILABLE");
  await vi.advanceTimersByTimeAsync(15000);
  await timedOut;
  expect(document.getElementById("kakao-map-sdk")).toBeNull();
});
