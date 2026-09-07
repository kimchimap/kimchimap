import { useEffect, useSyncExternalStore } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { restoreSession, sessionSnapshot, subscribeSession } from "./session";

export function LoginPage() {
  const [parameters] = useSearchParams();
  return (
    <main id="main-content">
      <h1>국산김치맵 로그인</h1>
      <p>
        카카오 계정으로 로그인하면 즐겨찾기와 원산지 제보를 이용할 수 있습니다.
      </p>
      {parameters.has("error") && (
        <p role="alert">로그인을 완료하지 못했습니다. 다시 시도해 주세요.</p>
      )}
      <a className="login-button" href="/api/v1/auth/login/kakao">
        카카오로 로그인
      </a>
      <p>로그인에는 카카오 회원 식별 정보만 사용합니다.</p>
      <Link to="/">로그인 없이 둘러보기</Link>
    </main>
  );
}

export function AuthCompletePage() {
  const navigate = useNavigate();
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  useEffect(() => {
    void restoreSession();
  }, []);
  useEffect(() => {
    if (session.status === "authenticated")
      void navigate("/", { replace: true });
  }, [session.status, navigate]);
  const failed = ["anonymous", "expired", "unavailable"].includes(
    session.status,
  );
  return (
    <main id="main-content">
      <h1>로그인 확인</h1>
      <p role="status">
        {failed
          ? "로그인 상태를 확인하지 못했습니다. 다시 로그인해 주세요."
          : "로그인을 안전하게 연결하고 있습니다."}
      </p>
      {failed && <Link to="/login">로그인으로 돌아가기</Link>}
    </main>
  );
}
