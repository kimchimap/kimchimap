import { useId, useRef, useState } from "react";
import type { components } from "../api/generated";
import { ApiError } from "../api/client";
import { authenticatedFetch } from "../auth/session";

type MediaItem = components["schemas"]["MediaItem"];
export function PhotoUpload({
  onUploaded,
}: {
  onUploaded?: (item: MediaItem) => void;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [uploaded, setUploaded] = useState(false);
  async function upload() {
    if (!file || busy) return;
    setError("");
    setUploaded(false);
    if (
      !new Set(["image/jpeg", "image/png"]).has(file.type) ||
      file.size > 10 * 1024 * 1024 ||
      file.size === 0
    ) {
      setError("10MB 이하의 JPEG 또는 PNG 사진을 선택해 주세요.");
      return;
    }
    setBusy(true);
    try {
      const form = new FormData();
      form.append("file", file);
      const response = await authenticatedFetch("/api/v1/media", {
        method: "POST",
        body: form,
      });
      const media = (await response.json()) as MediaItem;
      if (!media.id) throw new Error("사진 응답 식별자 누락");
      onUploaded?.(media);
      setUploaded(true);
      setFile(null);
      if (input.current) input.current.value = "";
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.status === 401
          ? "로그인이 만료되었습니다. 다시 로그인한 뒤 첨부해 주세요."
          : cause instanceof ApiError && cause.status === 429
            ? "업로드 요청이 많습니다. 잠시 후 다시 시도해 주세요."
            : "사진을 첨부하지 못했습니다. 파일 형식·크기·해상도를 확인하고 다시 시도해 주세요.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`}>원산지 표시판 사진</h2>
      <p id={`${id}-help`}>
        JPEG·PNG, 10MB 이하, 가로·세로 8192픽셀·총 2천만 픽셀 이하의 사진을
        첨부해 주세요. 얼굴과 개인 연락처가 보이지 않도록 촬영해 주세요.
      </p>
      <label htmlFor={id}>사진 선택</label>
      <input
        ref={input}
        id={id}
        type="file"
        accept="image/jpeg,image/png"
        disabled={busy}
        aria-describedby={`${id}-help`}
        onChange={(event) => {
          setFile(event.target.files?.[0] ?? null);
          setError("");
          setUploaded(false);
        }}
      />
      <button
        type="button"
        disabled={!file || busy}
        onClick={() => void upload()}
      >
        {busy ? "사진 확인 중…" : "사진 첨부"}
      </button>
      {error && <p role="alert">{error}</p>}
      {uploaded && (
        <p role="status">
          사진을 첨부했습니다. 검수 전에는 본인과 관리자만 볼 수 있습니다.
        </p>
      )}
    </section>
  );
}
