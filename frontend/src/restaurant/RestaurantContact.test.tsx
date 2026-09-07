import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RestaurantContact } from "./RestaurantContact";

describe("업소 연락처", () => {
  it("출처와 원문을 표시하고 정규화된 번호로 연결한다", () => {
    render(
      <RestaurantContact
        contact={{
          display: "02-0000-0000",
          number: "0200000000",
          sourceName: "테스트 출처",
        }}
      />,
    );
    expect(screen.getByText("02-0000-0000")).toBeInTheDocument();
    expect(screen.getByText("출처: 테스트 출처")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "전화 걸기" })).toHaveAttribute(
      "href",
      "tel:0200000000",
    );
  });

  it.each([
    undefined,
    null,
    { number: "javascript:alert(1)" },
    { number: "123" },
  ])(
    "번호 누락 또는 잘못된 값으로 연결 링크를 만들지 않는다: %j",
    (contact) => {
      render(<RestaurantContact contact={contact} />);
      expect(
        screen.getByText("등록된 업소 전화번호가 없습니다."),
      ).toBeInTheDocument();
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
    },
  );
});
