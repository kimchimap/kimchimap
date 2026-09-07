import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status });
let session: typeof import("./session");
let disconnect: (() => void) | undefined;
beforeEach(async () => {
  vi.resetModules();
  session = await import("./session");
});
afterEach(() => {
  disconnect?.();
  disconnect = undefined;
  vi.unstubAllGlobals();
});

function server() {
  return vi.fn((url: string, options?: RequestInit) => {
    if (url.endsWith("/csrf"))
      return Promise.resolve(
        json({ token: "test-csrf", headerName: "X-CSRF-TOKEN" }),
      );
    if (url.endsWith("/refresh")) {
      expect(new Headers(options?.headers).get("X-CSRF-TOKEN")).toBe(
        "test-csrf",
      );
      return Promise.resolve(
        json({ accessToken: "test-service-access", tokenType: "Bearer" }),
      );
    }
    if (url.endsWith("/logout"))
      return Promise.resolve(new Response(null, { status: 204 }));
    expect(new Headers(options?.headers).get("Authorization")).toBe(
      "Bearer test-service-access",
    );
    return Promise.resolve(json({ id: "test-member", role: "USER" }));
  });
}

describe("브라우저 인증 세션", () => {
  it("동시 요청은 한 번만 갱신하고 토큰을 브라우저 저장소에 쓰지 않는다", async () => {
    const fetch = server();
    vi.stubGlobal("fetch", fetch);
    const local = vi.spyOn(Storage.prototype, "setItem");
    await Promise.all([
      session.refreshSession(),
      session.refreshSession(),
      session.refreshSession(),
    ]);
    expect(
      fetch.mock.calls.filter(([url]) => url.endsWith("/refresh")),
    ).toHaveLength(1);
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });

  it("탭 잠금 안에서 쿠키 갱신을 수행한다", async () => {
    const fetch = server();
    const lock = vi.fn((_name: string, action: () => Promise<void>) =>
      action(),
    );
    vi.stubGlobal("fetch", fetch);
    vi.stubGlobal("navigator", { locks: { request: lock } });
    await session.refreshSession();
    expect(lock).toHaveBeenCalledWith(
      "kimchimap-auth-cookie",
      expect.any(Function),
    );
  });

  it("복구 후 로그아웃하면 사용자 캐시와 메모리 상태를 정리한다", async () => {
    vi.stubGlobal("fetch", server());
    const clear = vi.fn();
    disconnect = session.connectSession(clear);
    await Promise.all([session.restoreSession(), session.restoreSession()]);
    expect(session.sessionSnapshot()).toEqual({
      status: "authenticated",
      member: { id: "test-member", role: "USER" },
    });
    await session.logout();
    expect(clear).toHaveBeenCalled();
    expect(session.sessionSnapshot().status).toBe("anonymous");
  });

  it("쓰기 요청의 인증 실패는 자동으로 재전송하지 않는다", async () => {
    const fetch = server();
    vi.stubGlobal("fetch", fetch);
    await session.refreshSession();
    fetch.mockImplementation(() =>
      Promise.resolve(new Response(null, { status: 401 })),
    );
    await expect(
      session.authenticatedFetch("/api/v1/reports", { method: "POST" }),
    ).rejects.toMatchObject({ status: 401 });
    expect(
      fetch.mock.calls.filter(([url]) => url.endsWith("/reports")),
    ).toHaveLength(1);
    expect(session.sessionSnapshot().status).toBe("expired");
  });

  it("읽기 재전송도 한 번으로 제한하고 권한 부족에는 갱신하지 않는다", async () => {
    const fetch = server();
    vi.stubGlobal("fetch", fetch);
    await session.refreshSession();
    const original = fetch.getMockImplementation()!;
    fetch.mockImplementation((url, options) =>
      url.endsWith("/reports")
        ? Promise.resolve(new Response(null, { status: 401 }))
        : original(url, options),
    );
    await expect(
      session.authenticatedFetch("/api/v1/reports"),
    ).rejects.toMatchObject({ status: 401 });
    expect(
      fetch.mock.calls.filter(([url]) => url.endsWith("/reports")),
    ).toHaveLength(2);
    expect(
      fetch.mock.calls.filter(([url]) => url.endsWith("/refresh")),
    ).toHaveLength(2);
    fetch.mockImplementation(original);
    await session.refreshSession();
    fetch.mockClear();
    fetch.mockImplementation(() =>
      Promise.resolve(new Response(null, { status: 403 })),
    );
    await expect(
      session.authenticatedFetch("/api/v1/admin/reports"),
    ).rejects.toMatchObject({ status: 403 });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("로그아웃 전 시작한 갱신 응답이 인증을 복원하지 못한다", async () => {
    let release!: (response: Response) => void;
    const fetch = server();
    const original = fetch.getMockImplementation()!;
    fetch.mockImplementation((url, options) =>
      url.endsWith("/refresh")
        ? new Promise<Response>((resolve) => {
            release = resolve;
          })
        : original(url, options),
    );
    vi.stubGlobal("fetch", fetch);
    const refreshing = session.refreshSession();
    const rejected = expect(refreshing).rejects.toMatchObject({ status: 401 });
    await vi.waitFor(() => expect(release).toBeTypeOf("function"));
    await session.logout();
    release(json({ accessToken: "test-late-access", tokenType: "Bearer" }));
    await rejected;
    expect(session.sessionSnapshot().status).toBe("anonymous");
  });

  it("갱신 장애는 무한 재시도하지 않고 공개 화면을 위한 상태로 남긴다", async () => {
    const fetch = vi.fn(() =>
      Promise.resolve(new Response(null, { status: 503 })),
    );
    vi.stubGlobal("fetch", fetch);
    await session.restoreSession();
    expect(session.sessionSnapshot().status).toBe("unavailable");
    expect(fetch).toHaveBeenCalledTimes(1);
    await session.restoreSession();
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
