import { useEffect, useState, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import {
  connectSession,
  logout,
  restoreSession,
  sessionSnapshot,
  subscribeSession,
} from "./session";

export function AuthNavigation() {
  const client = useQueryClient();
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  const [error, setError] = useState("");
  useEffect(() => {
    const disconnect = connectSession(() => {
      void client.cancelQueries({ queryKey: ["private"] });
      client.removeQueries({ queryKey: ["private"] });
    });
    void restoreSession();
    return disconnect;
  }, [client]);
  async function signOut() {
    setError("");
    try {
      await logout();
    } catch {
      setError(
        "기기 내 로그인 정보는 지웠지만 서버 로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.",
      );
    }
  }
  return (
    <nav aria-label="회원 메뉴">
      {session.status === "authenticated" ? (
        <>
          <Link to="/reports">내 제보</Link>
          {session.member?.role === "ADMIN" && (
            <>
              <Link to="/admin/reports">제보 검수</Link>
              <Link to="/admin/ingestion">수집 관리</Link>
              <Link to="/admin/origins">원산지 정정</Link>
            </>
          )}
          <button onClick={() => void signOut()}>로그아웃</button>
        </>
      ) : (
        <Link to="/login">로그인</Link>
      )}
      {error && (
        <div role="alert">
          {error}
          <button onClick={() => void signOut()}>로그아웃 다시 시도</button>
        </div>
      )}
    </nav>
  );
}
