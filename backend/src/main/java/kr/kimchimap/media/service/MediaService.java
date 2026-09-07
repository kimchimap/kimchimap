package kr.kimchimap.media.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.media.dto.MediaItem;
import kr.kimchimap.media.repository.MediaRateRepository;
import kr.kimchimap.media.repository.MediaRepository;
import kr.kimchimap.media.repository.MediaRepository.Stored;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaService {
  public record Download(byte[] bytes, String contentType) {}

  private final MediaRepository media;
  private final MediaStorage storage;
  private final ImageSanitizer images;
  private final MediaRateRepository limiter;

  public MediaService(
      MediaRepository media,
      MediaStorage storage,
      ImageSanitizer images,
      MediaRateRepository limiter) {
    this.media = media;
    this.storage = storage;
    this.images = images;
    this.limiter = limiter;
  }

  public MediaItem upload(UUID owner, byte[] original, String contentType, String filename) {
    if (!limiter.allow(owner))
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_RATE_LIMITED", "사진은 한 시간에 10장까지 첨부할 수 있습니다.");
    var clean = images.sanitize(original, contentType, filename);
    UUID id = UUID.randomUUID(), key = UUID.randomUUID();
    try {
      storage.write(key, original, clean.bytes());
      // DB 응답 유실 때 커밋 여부가 불명확하므로 파일은 참조 재확인 정리 작업에 맡긴다.
      media.insert(
          id,
          owner,
          key,
          clean.contentType(),
          original.length,
          clean.bytes().length,
          clean.width(),
          clean.height(),
          hash(clean.bytes()));
      var row = media.find(id).orElseThrow();
      return new MediaItem(
          id,
          clean.contentType(),
          clean.bytes().length,
          clean.width(),
          clean.height(),
          row.createdAt());
    } catch (IOException exception) {
      throw unavailable();
    }
  }

  public Download download(UUID id, UUID requester, boolean admin, boolean original) {
    Stored row = media.find(id).orElseThrow(MediaService::missing);
    if (row.state().equals("DELETING") || !admin && !row.ownerId().equals(requester))
      throw missing();
    try {
      return new Download(storage.read(row.storageKey(), original), row.contentType());
    } catch (IOException exception) {
      throw unavailable();
    }
  }

  @Transactional
  public void attach(UUID owner, List<UUID> ids) {
    var rows = media.lockOwned(owner, ids);
    if (rows.size() != ids.size() || rows.stream().anyMatch(row -> row.state().equals("DELETING")))
      throw missing();
    rows.forEach(row -> media.attach(row.id()));
  }

  @Transactional
  public void publishReviewed(UUID owner, List<UUID> ids, UUID administrator) {
    var rows = media.lockOwned(owner, ids);
    if (rows.size() != ids.size() || rows.stream().anyMatch(row -> !row.state().equals("ATTACHED")))
      throw missing();
    rows.forEach(row -> media.publish(row.id(), administrator));
  }

  public Download publicImage(UUID id) {
    var row = media.find(id).orElseThrow(MediaService::missing);
    if (!row.publiclyVisible() || !row.state().equals("ATTACHED") || !media.hasPublicRestaurant(id))
      throw missing();
    try {
      return new Download(storage.read(row.storageKey(), false), row.contentType());
    } catch (IOException exception) {
      throw unavailable();
    }
  }

  private static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("필수 해시 알고리즘을 사용할 수 없습니다.", exception);
    }
  }

  private static ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "첨부 사진을 찾을 수 없습니다.");
  }

  private static ApiException unavailable() {
    return new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "MEDIA_STORAGE_UNAVAILABLE",
        "사진 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
  }
}
