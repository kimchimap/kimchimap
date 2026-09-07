import { useQuery } from "@tanstack/react-query";
import { Link, Route, Routes } from "react-router";
import { getSystemStatus } from "./api/client";
import { RestaurantContactPage } from "./restaurant/RestaurantContactPage";
import { AuthNavigation } from "./auth/AuthNavigation";
import { AuthCompletePage, LoginPage } from "./auth/LoginPage";

function Home() {
  const connection = useQuery({
    queryKey: ["system-status"],
    queryFn: ({ signal }) => getSystemStatus(signal),
  });
  return (
    <main id="main-content">
      <section className="intro" aria-labelledby="intro-title">
        <p className="eyebrow">원산지를 알고 고르는 한 끼</p>
        <h1 id="intro-title">
          우리 동네 김치,
          <br />
          어디에서 왔을까요?
        </h1>
        <p>
          식재료의 원산지와 확인 근거를 함께 살펴보는
          <br className="desktop-break" /> 국산김치맵을 준비하고 있습니다.
        </p>
      </section>
      <section className="notice" aria-labelledby="notice-title">
        <div className="notice-icon" aria-hidden="true">
          준비 중
        </div>
        <div>
          <h2 id="notice-title">확인된 정보를 차근차근 담겠습니다</h2>
          <p>
            원산지 정보는 아직 확보하지 못했습니다. 정보가 없다는 것은
            수입산이라는 뜻이 아닙니다.
          </p>
        </div>
      </section>
      <section className="principles" aria-label="정보 제공 원칙">
        <article>
          <span aria-hidden="true">01</span>
          <h2>메뉴와 용도를 구분해요</h2>
          <p>반찬용 김치와 찌개에 쓰이는 김치의 원산지는 다를 수 있습니다.</p>
        </article>
        <article>
          <span aria-hidden="true">02</span>
          <h2>근거를 함께 보여드려요</h2>
          <p>원산지 표시판 확인과 협회 지정 정보를 구분합니다.</p>
        </article>
        <article>
          <span aria-hidden="true">03</span>
          <h2>확인한 날짜를 알려드려요</h2>
          <p>정보를 가져온 날짜와 실제 원산지를 확인한 날짜는 다릅니다.</p>
        </article>
      </section>
      <div className="connection" role="status">
        {connection.isPending
          ? "서버 연결을 확인하고 있습니다."
          : connection.isError
            ? "서버 연결을 확인하지 못했습니다."
            : "서버가 정상적으로 연결되었습니다."}
        {connection.isError && (
          <button onClick={() => void connection.refetch()}>다시 시도</button>
        )}
      </div>
    </main>
  );
}

export function App() {
  return (
    <>
      <a className="skip-link" href="#main-content">
        본문으로 이동
      </a>
      <header>
        <Link to="/" className="brand">
          <span aria-hidden="true" className="brand-mark">
            김
          </span>
          국산김치맵
        </Link>
        <span className="preview-label">개발 중</span>
        <AuthNavigation />
      </header>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/complete" element={<AuthCompletePage />} />
        <Route
          path="/restaurants/:id/contact"
          element={<RestaurantContactPage />}
        />
        <Route
          path="*"
          element={
            <main id="main-content">
              <h1>페이지를 찾을 수 없습니다</h1>
              <Link to="/">처음으로 돌아가기</Link>
            </main>
          }
        />
      </Routes>
      <footer>
        원산지 정보는 식품의 위생·안전성 인증을 의미하지 않습니다.
      </footer>
    </>
  );
}
