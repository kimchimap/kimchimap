import { MapPage } from "./map/MapPage";
import { DataGuidePage } from "./map/DataGuidePage";
import { RestaurantDetailPage } from "./restaurant/RestaurantDetail";
import { Link, Route, Routes } from "react-router";
import { AuthNavigation } from "./auth/AuthNavigation";
import { AuthCompletePage, LoginPage } from "./auth/LoginPage";
import { LikesPage } from "./like/LikesPage";
import { PhotoUploadPage } from "./media/PhotoUploadPage";

import {
  NewReportPage,
  ReportsPage,
  ReportDetailPage,
} from "./report/ReportPages";

import { IngestionPage } from "./ingestion/IngestionPage";

import { OriginAdminPage } from "./origin/OriginAdminPage";

import { DesignationsPage } from "./certification/DesignationsPage";
import { MatchesPage } from "./matching/MatchesPage";

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
        <Route path="/" element={<MapPage />} />
        <Route path="/about/data" element={<DataGuidePage />} />
        <Route path="/restaurants/:id" element={<RestaurantDetailPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/complete" element={<AuthCompletePage />} />
        <Route path="/likes" element={<LikesPage />} />
        <Route path="/media/new" element={<PhotoUploadPage />} />
        <Route path="/admin/designations" element={<DesignationsPage />} />
        <Route path="/admin/matches" element={<MatchesPage />} />
        <Route path="/admin/origins" element={<OriginAdminPage />} />
        <Route path="/admin/ingestion" element={<IngestionPage />} />
        <Route path="/reports/new" element={<NewReportPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/reports/:id" element={<ReportDetailPage />} />
        <Route path="/admin/reports" element={<ReportsPage admin />} />
        <Route path="/admin/reports/:id" element={<ReportDetailPage admin />} />
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
