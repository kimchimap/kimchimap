import type { components } from "../api/generated";
import { ApiError } from "../api/client";

type Member = components["schemas"]["MemberResponse"];
type SessionState =
  | {
      status: "unknown" | "loading" | "anonymous" | "expired" | "unavailable";
      member: null;
    }
  | { status: "authenticated"; member: Member };

let accessToken: string | null = null;
let state: SessionState = { status: "unknown", member: null };
let refreshFlight: Promise<void> | null = null;
let restoreFlight: Promise<void> | null = null;
let generation = 0;
let channel: BroadcastChannel | null = null;
const listeners = new Set<() => void>();
const clearListeners = new Set<() => void>();

export const sessionSnapshot = () => state;
export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function publish(next: SessionState) {
  state = next;
  listeners.forEach((listener) => listener());
}

function clear(
  status: "anonymous" | "expired" | "unavailable",
  broadcast = false,
) {
  generation += 1;
  accessToken = null;
  clearListeners.forEach((listener) => listener());
  publish({ status, member: null });
  if (broadcast) channel?.postMessage({ type: "session-cleared" });
}

export function connectSession(clearPrivateCache: () => void) {
  clearListeners.add(clearPrivateCache);
  if (!channel && typeof BroadcastChannel !== "undefined") {
    channel = new BroadcastChannel("kimchimap-session");
    channel.onmessage = (event) => {
      if (event.data?.type === "session-cleared") clear("anonymous");
    };
  }
  return () => {
    clearListeners.delete(clearPrivateCache);
    if (clearListeners.size === 0) {
      channel?.close();
      channel = null;
    }
  };
}

async function exclusive<T>(action: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return navigator.locks.request("kimchimap-auth-cookie", action);
  }
  return action();
}

async function csrfHeaders(): Promise<Headers> {
  const response = await fetch("/api/v1/auth/csrf", {
    credentials: "same-origin",
    cache: "no-store",
    redirect: "error",
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new ApiError(response.status);
  const csrf = (await response.json()) as components["schemas"]["CsrfResponse"];
  if (!csrf.token || csrf.headerName !== "X-CSRF-TOKEN")
    throw new ApiError(502);
  return new Headers({ "X-CSRF-TOKEN": csrf.token });
}

export function refreshSession(): Promise<void> {
  if (refreshFlight) return refreshFlight;
  const epoch = generation;
  refreshFlight = exclusive(async () => {
    if (epoch !== generation) throw new ApiError(401);
    const headers = await csrfHeaders();
    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) throw new ApiError(response.status);
    const body =
      (await response.json()) as components["schemas"]["AccessTokenResponse"];
    if (!body.accessToken || body.tokenType !== "Bearer")
      throw new ApiError(502);
    // 로그아웃 전에 시작한 응답으로 인증 상태를 되살리지 않는다.
    if (epoch !== generation) throw new ApiError(401);
    accessToken = body.accessToken;
  })
    .catch((error: unknown) => {
      if (epoch === generation)
        clear(
          error instanceof ApiError && error.status === 401
            ? "expired"
            : "unavailable",
          true,
        );
      throw error;
    })
    .finally(() => {
      refreshFlight = null;
    });
  return refreshFlight;
}

export async function authenticatedFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  if (!path.startsWith("/api/v1/") || path.includes("\\"))
    throw new Error("허용되지 않은 요청 주소입니다.");
  if (!accessToken) await refreshSession();
  const send = () => {
    const headers = new Headers(options.headers);
    headers.set("Authorization", `Bearer ${accessToken}`);
    return fetch(path, {
      ...options,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      signal: options.signal
        ? AbortSignal.any([options.signal, AbortSignal.timeout(15_000)])
        : AbortSignal.timeout(15_000),
    });
  };
  let response = await send();
  const method = (options.method ?? "GET").toUpperCase();
  // 쓰기 요청은 처리 여부를 알 수 없으므로 자동 재전송하지 않는다.
  if (response.status === 401 && (method === "GET" || method === "HEAD")) {
    await refreshSession();
    response = await send();
  }
  if (response.status === 401) clear("expired", true);
  if (!response.ok) throw new ApiError(response.status);
  return response;
}

export function restoreSession(): Promise<void> {
  if (restoreFlight) return restoreFlight;
  if (state.status !== "unknown") return Promise.resolve();
  publish({ status: "loading", member: null });
  const epoch = generation;
  restoreFlight = (async () => {
    try {
      const response = await authenticatedFetch("/api/v1/members/me");
      const member = (await response.json()) as Member;
      if (!member.id || (member.role !== "USER" && member.role !== "ADMIN"))
        throw new ApiError(502);
      if (epoch === generation) publish({ status: "authenticated", member });
    } catch (error) {
      if (state.status === "expired")
        publish({ status: "anonymous", member: null });
      else if (epoch === generation)
        clear(
          error instanceof ApiError && error.status === 401
            ? "anonymous"
            : "unavailable",
        );
    }
  })().finally(() => {
    restoreFlight = null;
  });
  return restoreFlight;
}

export async function logout(all = false): Promise<void> {
  const token = accessToken;
  clear("anonymous", true);
  await exclusive(async () => {
    const headers = await csrfHeaders();
    if (all && token) headers.set("Authorization", `Bearer ${token}`);
    const response = await fetch(
      `/api/v1/auth/${all ? "logout-all" : "logout"}`,
      {
        method: "POST",
        headers,
        credentials: "same-origin",
        cache: "no-store",
        redirect: "error",
        signal: AbortSignal.timeout(15_000),
      },
    );
    if (!response.ok) throw new ApiError(response.status);
  });
}
