import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { authenticatedFetch } from "../auth/session";
import { PhotoUpload } from "./PhotoUpload";
vi.mock("../auth/session", () => ({ authenticatedFetch: vi.fn() }));
beforeEach(() => {
  vi.mocked(authenticatedFetch).mockReset();
});

describe("증빙 사진 첨부", () => {
  it("선택한 사진을 전송하고 공개 전 접근 범위를 안내한다", async () => {
    vi.mocked(authenticatedFetch).mockResolvedValue(
      new Response(
        JSON.stringify({ id: "test-media", contentType: "image/png" }),
        { status: 201 },
      ),
    );
    const uploaded = vi.fn();
    render(<PhotoUpload onUploaded={uploaded} />);
    const user = userEvent.setup();
    await user.upload(
      screen.getByLabelText("사진 선택"),
      new File(["test-image"], "proof.png", { type: "image/png" }),
    );
    await user.click(screen.getByRole("button", { name: "사진 첨부" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "본인과 관리자만",
    );
    expect(authenticatedFetch).toHaveBeenCalledWith(
      "/api/v1/media",
      expect.objectContaining({ method: "POST", body: expect.any(FormData) }),
    );
    expect(uploaded).toHaveBeenCalledWith(
      expect.objectContaining({ id: "test-media" }),
    );
  });
  it("지원하지 않는 형식은 전송하지 않는다", async () => {
    render(<PhotoUpload />);
    const user = userEvent.setup({ applyAccept: false });
    await user.upload(
      screen.getByLabelText("사진 선택"),
      new File(["<svg/>"], "proof.svg", { type: "image/svg+xml" }),
    );
    await user.click(screen.getByRole("button", { name: "사진 첨부" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("JPEG 또는 PNG");
    expect(authenticatedFetch).not.toHaveBeenCalled();
  });
  it("실패를 업로드 성공으로 표시하지 않고 사용자가 다시 시도할 수 있다", async () => {
    vi.mocked(authenticatedFetch).mockRejectedValue(new Error("test-network"));
    render(<PhotoUpload />);
    const user = userEvent.setup();
    await user.upload(
      screen.getByLabelText("사진 선택"),
      new File(["test-image"], "proof.jpg", { type: "image/jpeg" }),
    );
    await user.click(screen.getByRole("button", { name: "사진 첨부" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "첨부하지 못했습니다",
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "사진 첨부" })).toBeEnabled();
    expect(authenticatedFetch).toHaveBeenCalledTimes(1);
  });
});
