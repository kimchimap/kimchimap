-- 기존 개인 목록을 좋아요로 통합하며 중복 기능과 데이터를 만들지 않는다.
ALTER TABLE app.bookmark RENAME TO restaurant_like;
ALTER INDEX app.bookmark_member_page RENAME TO restaurant_like_member_page;
CREATE INDEX restaurant_like_count ON app.restaurant_like(restaurant_id);
