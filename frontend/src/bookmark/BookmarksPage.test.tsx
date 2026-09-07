import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { getBookmarks, getBookmarkStatus, setBookmark } from "./api";
import { BookmarksPage } from "./BookmarksPage";
import { BookmarkToggle } from "./BookmarkToggle";
const member = {
  status: "authenticated",
  member: { id: "test-member", role: "USER" },
};
vi.mock("../auth/session", () => ({
  sessionSnapshot: () => member,
  subscribeSession: () => () => {},
}));
vi.mock("./api", () => ({
  getBookmarks: vi.fn(),
  getBookmarkStatus: vi.fn(),
  setBookmark: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(getBookmarks).mockReset();
  vi.mocked(getBookmarkStatus).mockReset();
  vi.mocked(setBookmark).mockReset();
});
function show(element: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{element}</MemoryRouter>
    </QueryClientProvider>,
  );
}
it("저장한 업소와 빈 상태를 구분해 표시한다", async () => {
  vi.mocked(getBookmarks).mockResolvedValue({ items: [] });
  show(<BookmarksPage />);
  expect(
    await screen.findByText(/아직 저장한 업소가 없습니다/),
  ).toBeInTheDocument();
});
it("즐겨찾기 변경 후 서버 상태를 다시 확인한다", async () => {
  vi.mocked(getBookmarkStatus)
    .mockResolvedValueOnce({ saved: false })
    .mockResolvedValue({ saved: true });
  vi.mocked(setBookmark).mockResolvedValue();
  show(<BookmarkToggle restaurantId="test-restaurant" />);
  const user = userEvent.setup();
  await screen.findByRole("button", { name: "즐겨찾기 추가" });
  await vi.waitFor(() =>
    expect(screen.getByRole("button", { name: "즐겨찾기 추가" })).toBeEnabled(),
  );
  await user.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
  expect(
    await screen.findByRole("button", { name: "즐겨찾기 해제" }),
  ).toHaveAttribute("aria-pressed", "true");
  expect(setBookmark).toHaveBeenCalledWith("test-restaurant", true);
});
it("목록 장애를 빈 결과로 표시하지 않는다", async () => {
  vi.mocked(getBookmarks).mockRejectedValue(new Error("test-network"));
  show(<BookmarksPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "불러오지 못했습니다",
  );
  expect(
    screen.queryByText(/아직 저장한 업소가 없습니다/),
  ).not.toBeInTheDocument();
});
