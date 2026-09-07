import { useSyncExternalStore } from "react";
import { Link } from "react-router";
import { sessionSnapshot, subscribeSession } from "../auth/session";
import { PhotoUpload } from "./PhotoUpload";

export function PhotoUploadPage() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot);
  return (
    <main id="main-content">
      <h1>원산지 증빙 사진 첨부</h1>
      {session.status === "authenticated" ? (
        <PhotoUpload />
      ) : session.status === "loading" || session.status === "unknown" ? (
        <p role="status">로그인을 확인하고 있습니다.</p>
      ) : (
        <p>
          사진을 첨부하려면 <Link to="/login">로그인</Link>해 주세요.
        </p>
      )}
      <p>제보에 연결되지 않은 사진은 24시간 뒤 정리 대상이 됩니다.</p>
    </main>
  );
}
