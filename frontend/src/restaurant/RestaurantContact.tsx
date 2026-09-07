import type { components } from "../api/generated";

type Contact = components["schemas"]["Contact"];

export function RestaurantContact({
  contact,
}: {
  contact?: Contact | null | undefined;
}) {
  const number = contact?.number;
  if (!number || !/^\+?[0-9]{8,15}$/.test(number)) {
    return <p>등록된 업소 전화번호가 없습니다.</p>;
  }
  return (
    <section aria-labelledby="restaurant-contact-title">
      <h2 id="restaurant-contact-title">업소 전화번호</h2>
      <p>{contact.display || number}</p>
      <a className="call-button" href={`tel:${number}`}>
        전화 걸기
      </a>
      <p className="contact-source">출처: {contact.sourceName}</p>
      <p>등록된 번호가 변경되었거나 연결되지 않을 수 있습니다.</p>
    </section>
  );
}
