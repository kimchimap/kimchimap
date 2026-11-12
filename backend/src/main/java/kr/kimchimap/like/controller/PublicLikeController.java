package kr.kimchimap.like.controller;

import java.util.UUID;
import kr.kimchimap.like.service.LikeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/likes")
public class PublicLikeController {
  public record LikeCount(long count) {}

  private final LikeService likes;

  public PublicLikeController(LikeService likes) {
    this.likes = likes;
  }

  @GetMapping
  public LikeCount count(@PathVariable UUID restaurantId) {
    return new LikeCount(likes.count(restaurantId));
  }
}
