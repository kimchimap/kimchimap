package kr.kimchimap.like.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.like.dto.LikePage;
import kr.kimchimap.like.service.LikeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/likes")
@SecurityRequirement(name = "serviceBearer")
public class LikeController {
  public record LikeStatus(boolean saved) {}

  private final LikeService likes;

  public LikeController(LikeService likes) {
    this.likes = likes;
  }

  @GetMapping
  public LikePage list(
      @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return likes.list(member.memberId(), cursor, limit);
  }

  @PutMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void add(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID restaurantId) {
    likes.add(member.memberId(), restaurantId);
  }

  @GetMapping("/{restaurantId}")
  public LikeStatus status(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID restaurantId) {
    return new LikeStatus(likes.contains(member.memberId(), restaurantId));
  }

  @DeleteMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID restaurantId) {
    likes.remove(member.memberId(), restaurantId);
  }
}
