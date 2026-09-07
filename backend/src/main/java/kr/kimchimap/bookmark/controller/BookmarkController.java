package kr.kimchimap.bookmark.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.bookmark.dto.BookmarkPage;
import kr.kimchimap.bookmark.service.BookmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookmarks")
@SecurityRequirement(name = "serviceBearer")
public class BookmarkController {
  public record BookmarkStatus(boolean saved) {}

  private final BookmarkService bookmarks;

  public BookmarkController(BookmarkService bookmarks) {
    this.bookmarks = bookmarks;
  }

  @GetMapping
  public BookmarkPage list(
      @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return bookmarks.list(member.memberId(), cursor, limit);
  }

  @PutMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void add(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID restaurantId) {
    bookmarks.add(member.memberId(), restaurantId);
  }

  @GetMapping("/{restaurantId}")
  public BookmarkStatus status(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID restaurantId) {
    return new BookmarkStatus(bookmarks.contains(member.memberId(), restaurantId));
  }

  @DeleteMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID restaurantId) {
    bookmarks.remove(member.memberId(), restaurantId);
  }
}
