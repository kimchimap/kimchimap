import { useId } from "react";
import type { RestaurantCorrection } from "./admin-api";
export function RestaurantCorrectionFields({
  value,
  onChange,
}: {
  value: RestaurantCorrection;
  onChange: (next: RestaurantCorrection) => void;
}) {
  const id = useId();
  function coordinate(field: "latitude" | "longitude", input: string) {
    const next = { ...value };
    if (input === "") delete next[field];
    else next[field] = Number(input);
    onChange(next);
  }
  return (
    <fieldset>
      <legend>업소 정보 정정</legend>
      <label htmlFor={`${id}-name`}>가게 이름</label>
      <input
        id={`${id}-name`}
        required
        maxLength={200}
        value={value.name}
        onChange={(e) => onChange({ ...value, name: e.target.value })}
      />
      <label htmlFor={`${id}-address`}>가게 주소</label>
      <input
        id={`${id}-address`}
        required
        maxLength={2000}
        value={value.address}
        onChange={(e) => onChange({ ...value, address: e.target.value })}
      />
      <label htmlFor={`${id}-status`}>영업 상태</label>
      <select
        id={`${id}-status`}
        value={value.businessStatus}
        onChange={(e) => onChange({ ...value, businessStatus: e.target.value })}
      >
        <option value="OPEN">영업 중</option>
        <option value="CLOSED">폐업</option>
        <option value="UNKNOWN">미확인</option>
      </select>
      <label htmlFor={`${id}-latitude`}>위도</label>
      <input
        id={`${id}-latitude`}
        type="number"
        step="any"
        min={32}
        max={39.5}
        value={value.latitude ?? ""}
        onChange={(e) => coordinate("latitude", e.target.value)}
      />
      <label htmlFor={`${id}-longitude`}>경도</label>
      <input
        id={`${id}-longitude`}
        type="number"
        step="any"
        min={124}
        max={132}
        value={value.longitude ?? ""}
        onChange={(e) => coordinate("longitude", e.target.value)}
      />
      <p>
        좌표를 바꾸면 위도와 경도를 함께 확인하세요. 폐업은 공개에서 제외하며
        원산지 근거와 변경 이력은 보존합니다.
      </p>
      <label>
        <input
          type="checkbox"
          checked={value.locationReviewed ?? false}
          onChange={(e) =>
            onChange({ ...value, locationReviewed: e.target.checked })
          }
        />
        이름·주소·위치가 같은 가게의 정보임을 확인했습니다.
      </label>
      <label htmlFor={`${id}-phone`}>공개할 업소 전화번호</label>
      <input
        id={`${id}-phone`}
        type="tel"
        maxLength={64}
        value={value.phone ?? ""}
        onChange={(e) => onChange({ ...value, phone: e.target.value })}
      />
      <p>빈 전화번호는 공개 연락처를 제거합니다.</p>
      <label>
        <input
          type="checkbox"
          checked={value.businessPhoneReviewed ?? false}
          onChange={(e) =>
            onChange({ ...value, businessPhoneReviewed: e.target.checked })
          }
        />
        개인 연락처가 아닌 공개 가능한 업소 전화번호임을 확인했습니다.
      </label>
    </fieldset>
  );
}
