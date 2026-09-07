package kr.kimchimap.media.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.media.dto.MediaItem;
import kr.kimchimap.media.service.ImageSanitizer;
import kr.kimchimap.media.service.MediaService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@SecurityRequirement(name = "serviceBearer")
public class MediaController {
  private final MediaService media;

  public MediaController(MediaService media) {
    this.media = media;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public MediaItem upload(
      @AuthenticationPrincipal AuthenticatedMember member, @RequestPart("file") MultipartFile file)
      throws IOException {
    try (var input = file.getInputStream()) {
      return media.upload(
          member.memberId(),
          input.readNBytes(ImageSanitizer.MAX_ORIGINAL_BYTES + 1),
          file.getContentType(),
          file.getOriginalFilename());
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> download(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean original) {
    var file = media.download(id, member.memberId(), member.role().equals("ADMIN"), original);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"evidence"
                + (file.contentType().equals("image/png") ? ".png" : ".jpg")
                + "\"")
        .header("X-Content-Type-Options", "nosniff")
        .body(file.bytes());
  }
}
