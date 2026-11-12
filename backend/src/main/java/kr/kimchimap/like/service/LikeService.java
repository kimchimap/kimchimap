package kr.kimchimap.like.service;

import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.like.dto.LikePage;
import kr.kimchimap.like.repository.LikeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {
  private final LikeRepository likes;

  public LikeService(LikeRepository likes) {
    this.likes = likes;
  }

  @Transactional
  public void add(UUID member, UUID restaurant) {
    if (!likes.published(restaurant))
      throw new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "공개된 업소를 찾을 수 없습니다.");
    likes.add(member, restaurant);
  }

  @Transactional(readOnly = true)
  public long count(UUID restaurant) {
    if (!likes.published(restaurant))
      throw new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "공개된 업소를 찾을 수 없습니다.");
    return likes.count(restaurant);
  }

  @Transactional
  public void remove(UUID member, UUID restaurant) {
    likes.remove(member, restaurant);
  }

  @Transactional(readOnly = true)
  public boolean contains(UUID member, UUID restaurant) {
    return likes.cursorExists(member, restaurant);
  }

  @Transactional(readOnly = true)
  public LikePage list(UUID member, UUID cursor, int limit) {
    if (limit < 1 || limit > 50 || cursor != null && !likes.cursorExists(member, cursor))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "목록 크기와 다음 페이지를 확인해 주세요.");
    var rows = likes.list(member, cursor, limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return new LikePage(items, more ? items.getLast().restaurantId() : null);
  }
}
