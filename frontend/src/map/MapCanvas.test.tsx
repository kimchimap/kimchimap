import { StrictMode } from "react";
import { render, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MapCanvas } from "./MapCanvas";
import { loadMapSdk, type KakaoMaps } from "./sdk";
vi.mock("./sdk", async (original) => ({
  ...(await original<typeof import("./sdk")>()),
  loadMapSdk: vi.fn(),
}));
afterEach(() => vi.unstubAllGlobals());
it("개발 모드와 결과 변경에서 생성한 마커·이벤트·크기 관찰을 정리한다", async () => {
  const disconnect = vi.fn(),
    add = vi.fn(),
    remove = vi.fn(),
    detach = vi.fn();
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      disconnect = disconnect;
    },
  );
  const point = { getLat: () => 37.56, getLng: () => 126.97 };
  const map = {
    getBounds: () => ({ getSouthWest: () => point, getNorthEast: () => point }),
    getCenter: () => point,
    setCenter: vi.fn(),
    relayout: vi.fn(),
  };
  const sdk = {
    Map: class {
      constructor() {
        return map;
      }
    },
    LatLng: class {},
    Marker: class {
      setMap = detach;
      setZIndex() {}
      setOpacity() {}
    },
    event: { addListener: add, removeListener: remove },
  } as unknown as KakaoMaps;
  vi.mocked(loadMapSdk).mockResolvedValue(sdk);
  const picked = vi.fn();
  const selected = vi.fn(),
    bounds = vi.fn();
  const view = (
    items: { id: string; latitude: number; longitude: number }[],
  ) => (
    <StrictMode>
      <MapCanvas
        center={{ latitude: 37.56, longitude: 126.97 }}
        items={items}
        selected=""
        onBounds={bounds}
        onSelect={selected}
        onPick={picked}
      />
    </StrictMode>
  );
  const { rerender, unmount } = render(
    view([{ id: "first", latitude: 37.56, longitude: 126.97 }]),
  );
  await waitFor(() =>
    expect(add.mock.calls.some((call) => call[1] === "click")).toBe(true),
  );
  const click = add.mock.calls.find(
    (call) => call[0] === map && call[1] === "click",
  )?.[2];
  click({ latLng: point });
  expect(picked).toHaveBeenCalledWith({ latitude: 37.56, longitude: 126.97 });
  rerender(view([{ id: "second", latitude: 37.57, longitude: 126.98 }]));
  expect(detach).toHaveBeenCalledWith(null);
  unmount();
  expect(remove.mock.calls).toHaveLength(add.mock.calls.length);
  expect(disconnect).toHaveBeenCalledTimes(1);
  expect(detach).toHaveBeenCalledTimes(2);
});
