import { useSyncExternalStore, type ReactNode } from "react";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "./session";

export function MemberGate({
  admin = false,
  children,
}: {
  admin?: boolean;
  children: ReactNode;
}) {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  if (session.status === "loading" || session.status === "unknown")
    return <p role="status">로그인을 확인하고 있습니다.</p>;
  if (session.status !== "authenticated")
    return (
      <p>
        <Link to="/login">로그인</Link>한 뒤 이용해 주세요.
      </p>
    );
  if (admin && session.member.role !== "ADMIN")
    return <p role="alert">관리자만 이용할 수 있습니다.</p>;
  return children;
}
