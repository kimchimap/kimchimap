import { useEffect, useState } from "react";
import { authenticatedFetch } from "../auth/session";

export function PrivatePhoto({ id, index }: { id: string; index: number }) {
  const [url, setUrl] = useState("");
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let objectUrl = "";
    void authenticatedFetch(`/api/v1/media/${encodeURIComponent(id)}`, {
      signal: controller.signal,
    })
      .then((response) => response.blob())
      .then((blob) => {
        if (!controller.signal.aborted) {
          objectUrl = URL.createObjectURL(blob);
          setUrl(objectUrl);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [id]);
  return failed ? (
    <p role="alert">
      사진 {index + 1}을 불러오지 못했습니다. 페이지를 다시 열어 주세요.
    </p>
  ) : url ? (
    <img
      className="evidence-photo"
      src={url}
      alt={`제출한 원산지 표시판 ${index + 1}`}
    />
  ) : (
    <p role="status">사진 {index + 1}을 불러오고 있습니다.</p>
  );
}
