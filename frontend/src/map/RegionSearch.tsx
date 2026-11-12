import { useEffect, useRef, useState } from "react";
import { findRegions, type Region } from "./sdk";
import type { Center } from "./types";
const regions: { name: string; center: Center }[] = [
  { name: "서울", center: { latitude: 37.5665, longitude: 126.978 } },
  { name: "부산", center: { latitude: 35.1796, longitude: 129.0756 } },
  { name: "대구", center: { latitude: 35.8714, longitude: 128.6014 } },
  { name: "인천", center: { latitude: 37.4563, longitude: 126.7052 } },
  { name: "광주", center: { latitude: 35.1595, longitude: 126.8526 } },
  { name: "대전", center: { latitude: 36.3504, longitude: 127.3845 } },
  { name: "울산", center: { latitude: 35.5395, longitude: 129.3114 } },
  { name: "세종", center: { latitude: 36.4801, longitude: 127.289 } },
  { name: "수원", center: { latitude: 37.2636, longitude: 127.0286 } },
  { name: "춘천", center: { latitude: 37.8813, longitude: 127.73 } },
  { name: "청주", center: { latitude: 36.6424, longitude: 127.489 } },
  { name: "홍성", center: { latitude: 36.6012, longitude: 126.6608 } },
  { name: "전주", center: { latitude: 35.8242, longitude: 127.148 } },
  { name: "무안", center: { latitude: 34.9905, longitude: 126.4817 } },
  { name: "안동", center: { latitude: 36.5684, longitude: 128.7294 } },
  { name: "창원", center: { latitude: 35.2279, longitude: 128.6811 } },
  { name: "제주", center: { latitude: 33.4996, longitude: 126.5312 } },
];
export function RegionSearch({
  onSelect,
}: {
  onSelect: (center: Center) => void;
}) {
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<Region[]>([]);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  useEffect(
    () => () => {
      generation.current++;
    },
    [],
  );
  const key = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY ?? "";
  async function search() {
    const current = ++generation.current;
    setBusy(true);
    setResults([]);
    setNotice("");
    try {
      const found = await findRegions(keyword.trim(), key);
      if (current !== generation.current) return;
      setResults(found);
      if (!found.length)
        setNotice("지역을 찾지 못했습니다. 다른 지명으로 검색해 주세요.");
    } catch {
      if (current === generation.current)
        setNotice(
          "지역 검색을 사용할 수 없습니다. 기본 지역 선택이나 현재 위치를 이용해 주세요.",
        );
    } finally {
      if (current === generation.current) setBusy(false);
    }
  }
  return (
    <div className="region-search">
      <label className="sr-only" htmlFor="region-preset">
        기본 지역 선택
      </label>
      <select
        id="region-preset"
        defaultValue=""
        onChange={(event) => {
          const region = regions.find(
            (region) => region.name === event.target.value,
          );
          if (region) {
            generation.current++;
            setResults([]);
            setBusy(false);
            onSelect(region.center);
          }
        }}
      >
        <option value="" disabled>
          지역 선택
        </option>
        {regions.map((region) => (
          <option key={region.name}>{region.name}</option>
        ))}
      </select>
      {key && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void search();
          }}
        >
          <label className="sr-only" htmlFor="region-keyword">
            지역·장소 찾기
          </label>
          <input
            id="region-keyword"
            type="search"
            required
            maxLength={80}
            value={keyword}
            onChange={(event) => {
              generation.current++;
              setBusy(false);
              setResults([]);
              setNotice("");
              setKeyword(event.target.value);
            }}
            placeholder="지역·장소 찾기"
          />
          <button disabled={busy || !keyword.trim()}>
            {busy ? "검색 중…" : "지역 찾기"}
          </button>
        </form>
      )}
      {notice && <p role="status">{notice}</p>}
      {!!results.length && (
        <div className="region-results">
          <p>카카오 장소 검색 · 지도 이동용</p>
          <ul>
            {results.map((region) => (
              <li key={region.id}>
                <button
                  onClick={() => {
                    onSelect(region.center);
                    setResults([]);
                  }}
                >
                  {region.name}
                  <small>{region.address}</small>
                </button>
              </li>
            ))}
          </ul>
          <p>이 결과는 원산지 확인 업소 목록이 아닙니다.</p>
          <button onClick={() => setResults([])}>지역 결과 닫기</button>
        </div>
      )}
    </div>
  );
}
