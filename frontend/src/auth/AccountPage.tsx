import { useState } from "react";
import { Link } from "react-router";
import { ApiError } from "../api/client";
import { MemberGate } from "./MemberGate";
import { logout, withdrawAccount } from "./session";

type Action = "logout-all" | "withdraw";
export function AccountPage() {
  const [completed, setCompleted] = useState<Action | null>(null);
  return (
    <main id="main-content">
      <h1>계정 관리</h1>
      {completed ? (
        <section role="status">
          <h2>
            {completed === "withdraw"
              ? "회원 탈퇴를 완료했습니다"
              : "모든 기기에서 로그아웃했습니다"}
          </h2>
          <Link to="/">지도로 돌아가기</Link>
        </section>
      ) : (
        <MemberGate>
          <AccountControls onComplete={setCompleted} />
        </MemberGate>
      )}
    </main>
  );
}
function AccountControls({
  onComplete,
}: {
  onComplete: (action: Action) => void;
}) {
  const [confirmed, setConfirmed] = useState(false);
  const [pending, setPending] = useState<Action | null>(null);
  const [error, setError] = useState("");
  async function perform(action: Action) {
    if (pending || (action === "withdraw" && !confirmed)) return;
    setPending(action);
    setError("");
    try {
      if (action === "withdraw") await withdrawAccount();
      else await logout(true);
      onComplete(action);
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.status === 401
          ? "로그인이 만료되었습니다. 다시 로그인한 뒤 계정 상태를 확인해 주세요."
          : "서버 처리 결과를 확인하지 못했습니다. 완료된 것으로 간주하지 마세요. 다시 시도하거나 재로그인 후 상태를 확인해 주세요.",
      );
    } finally {
      setPending(null);
    }
  }
  return (
    <>
      <section>
        <h2>로그인 기기 관리</h2>
        <p>현재 기기를 포함한 모든 로그인 세션을 종료합니다.</p>
        <button
          disabled={pending !== null}
          onClick={() => void perform("logout-all")}
        >
          {pending === "logout-all" ? "로그아웃 중…" : "모든 기기에서 로그아웃"}
        </button>
      </section>
      <section>
        <h2>회원 탈퇴</h2>
        <p>
          탈퇴하면 모든 로그인 세션이 종료되고 이 계정으로 다시 로그인할 수
          없습니다. 기존 제보·공개 원산지 근거·검수 이력은 즉시 삭제되지
          않습니다.
        </p>
        <p>
          현재는 개발 중인 서비스입니다. 자료 보유기간과 삭제 요청 절차는 출시
          전에 확정합니다.
        </p>
        <label>
          <input
            type="checkbox"
            checked={confirmed}
            disabled={pending !== null}
            onChange={(event) => setConfirmed(event.target.checked)}
          />
          계정 이용이 중단되며 기존 근거와 검수 이력은 남는다는 내용을
          확인했습니다.
        </label>
        <button
          disabled={!confirmed || pending !== null}
          onClick={() => void perform("withdraw")}
        >
          {pending === "withdraw" ? "탈퇴 처리 중…" : "회원 탈퇴"}
        </button>
      </section>
      {error && <p role="alert">{error}</p>}
    </>
  );
}
