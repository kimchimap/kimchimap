import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { AccountPage } from "./AccountPage";
import { logout, sessionSnapshot, withdrawAccount } from "./session";
vi.mock("./session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  logout: vi.fn(),
  withdrawAccount: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-member", role: "USER" },
  });
});
function show() {
  render(
    <MemoryRouter>
      <AccountPage />
    </MemoryRouter>,
  );
}
it("확인하지 않은 회원 탈퇴를 차단하고 명시적 확인 뒤 완료를 표시한다", async () => {
  vi.mocked(withdrawAccount).mockResolvedValue();
  show();
  const user = userEvent.setup();
  expect(screen.getByRole("button", { name: "회원 탈퇴" })).toBeDisabled();
  await user.click(screen.getByRole("checkbox"));
  await user.click(screen.getByRole("button", { name: "회원 탈퇴" }));
  expect(await screen.findByRole("status")).toHaveTextContent(
    "회원 탈퇴를 완료했습니다",
  );
  expect(withdrawAccount).toHaveBeenCalledTimes(1);
});
it("전체 로그아웃 실패는 성공 안내를 표시하지 않고 재시도할 수 있다", async () => {
  vi.mocked(logout)
    .mockRejectedValueOnce(new ApiError(503))
    .mockResolvedValueOnce();
  show();
  const user = userEvent.setup();
  await user.click(
    screen.getByRole("button", { name: "모든 기기에서 로그아웃" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "처리 결과를 확인하지 못했습니다",
  );
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  await user.click(
    screen.getByRole("button", { name: "모든 기기에서 로그아웃" }),
  );
  expect(await screen.findByRole("status")).toHaveTextContent(
    "모든 기기에서 로그아웃했습니다",
  );
  expect(logout).toHaveBeenLastCalledWith(true);
});
it("처리 중에는 다른 계정 종료 요청도 차단한다", async () => {
  vi.mocked(logout).mockImplementation(() => new Promise(() => {}));
  show();
  const user = userEvent.setup();
  await user.click(screen.getByRole("checkbox"));
  await user.click(
    screen.getByRole("button", { name: "모든 기기에서 로그아웃" }),
  );
  expect(screen.getByRole("button", { name: "회원 탈퇴" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "로그아웃 중…" })).toBeDisabled();
  expect(withdrawAccount).not.toHaveBeenCalled();
});
