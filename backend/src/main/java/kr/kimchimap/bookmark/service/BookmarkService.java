package kr.kimchimap.bookmark.service;

import java.util.UUID;
import kr.kimchimap.bookmark.dto.BookmarkPage;
import kr.kimchimap.bookmark.repository.BookmarkRepository;
import kr.kimchimap.global.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {
  private final BookmarkRepository bookmarks;

  public BookmarkService(BookmarkRepository bookmarks) {
    this.bookmarks = bookmarks;
  }

  @Transactional
  public void add(UUID member, UUID restaurant) {
    if (!bookmarks.published(restaurant))
      throw new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "공개된 업소를 찾을 수 없습니다.");
    bookmarks.add(member, restaurant);
  }

  @Transactional
  public void remove(UUID member, UUID restaurant) {
    bookmarks.remove(member, restaurant);
  }

  @Transactional(readOnly = true)
  public boolean contains(UUID member, UUID restaurant) {
    return bookmarks.cursorExists(member, restaurant);
  }

  @Transactional(readOnly = true)
  public BookmarkPage list(UUID member, UUID cursor, int limit) {
    if (limit < 1 || limit > 50 || cursor != null && !bookmarks.cursorExists(member, cursor))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "목록 크기와 다음 페이지를 확인해 주세요.");
    var rows = bookmarks.list(member, cursor, limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return new BookmarkPage(items, more ? items.getLast().restaurantId() : null);
  }
}
