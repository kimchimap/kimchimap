package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.media.service.MediaCleanup;
import kr.kimchimap.media.service.MediaStorage;
import kr.kimchimap.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

class BookmarksMediaIntegrationTest extends ApplicationIntegrationSupport {
  private static final Path MEDIA_ROOT;

  static {
    try {
      MEDIA_ROOT = Files.createTempDirectory("kimchimap-test-media-");
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @DynamicPropertySource
  static void mediaRoot(DynamicPropertyRegistry properties) {
    properties.add("app.media.root", MEDIA_ROOT::toString);
  }

  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  @Autowired MediaCleanup cleanup;
  @Autowired MediaStorage storage;
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void bookmarksAreIdempotentPrivateAndHaveStableLimitedPages() throws Exception {
    String first = token(), other = token();
    UUID restaurant = restaurant(true), second = restaurant(true), hidden = restaurant(false);
    assertThat(request("PUT", "/api/v1/bookmarks/" + restaurant, first).statusCode())
        .isEqualTo(204);
    assertThat(request("PUT", "/api/v1/bookmarks/" + restaurant, first).statusCode())
        .isEqualTo(204);
    assertThat(request("PUT", "/api/v1/bookmarks/" + second, first).statusCode()).isEqualTo(204);
    assertThat(request("PUT", "/api/v1/bookmarks/" + hidden, first).statusCode()).isEqualTo(404);
    assertThat(request("GET", "/api/v1/bookmarks", other).body())
        .doesNotContain(restaurant.toString());
    var page = request("GET", "/api/v1/bookmarks?limit=1", first);
    assertThat(page.statusCode()).isEqualTo(200);
    var body = json.readTree(page.body());
    assertThat(body.path("items").size()).isEqualTo(1);
    String cursor = body.path("nextCursor").asString();
    var next =
        json.readTree(request("GET", "/api/v1/bookmarks?limit=1&cursor=" + cursor, first).body());
    assertThat(next.path("items").size()).isEqualTo(1);
    assertThat(next.path("items").get(0).path("restaurantId").asString()).isNotEqualTo(cursor);
    assertThat(request("GET", "/api/v1/bookmarks?cursor=" + cursor, other).statusCode())
        .isEqualTo(400);
    assertThat(request("GET", "/api/v1/bookmarks?limit=51", first).statusCode()).isEqualTo(400);
    assertThat(request("DELETE", "/api/v1/bookmarks/" + restaurant, other).statusCode())
        .isEqualTo(204);
    assertThat(request("GET", "/api/v1/bookmarks", first).body()).contains(restaurant.toString());
    assertThat(request("DELETE", "/api/v1/bookmarks/" + restaurant, first).statusCode())
        .isEqualTo(204);
    assertThat(request("DELETE", "/api/v1/bookmarks/" + restaurant, first).statusCode())
        .isEqualTo(204);
    assertThat(request("GET", "/api/v1/bookmarks", first).body())
        .doesNotContain(restaurant.toString());
    assertThat(get("/api/v1/bookmarks").statusCode()).isEqualTo(401);
  }

  @Test
  void validatesUploadAndProtectsOriginalAndSanitizedEvidence() throws Exception {
    String owner = token(), other = token();
    byte[] image = png();
    var upload = upload(owner, image, "image/png", "../../proof.png");
    assertThat(upload.statusCode()).isEqualTo(201);
    String id = json.readTree(upload.body()).path("id").asString();
    assertThat(upload.body()).doesNotContain("storageKey", "ownerId", "original");
    for (String suffix : new String[] {"", "?original=true"}) {
      assertThat(request("GET", "/api/v1/media/" + id + suffix, owner).statusCode()).isEqualTo(200);
      assertThat(request("GET", "/api/v1/media/" + id + suffix, other).statusCode()).isEqualTo(404);
      assertThat(get("/api/v1/media/" + id + suffix).statusCode()).isEqualTo(401);
    }
    var file = request("GET", "/api/v1/media/" + id, owner);
    var administrator =
        members.loginWithVerifiedKakaoSubject("test-admin-media-" + UUID.randomUUID());
    members.changeSecurity(administrator.id(), "ADMIN", "ACTIVE", "테스트 관리자 지정", "test-operator");
    String admin = jwt.issue(sessions.create(members.requireActive(administrator.id())).session());
    assertThat(request("GET", "/api/v1/media/" + id + "?original=true", admin).statusCode())
        .isEqualTo(200);
    assertThat(file.headers().firstValue("cache-control")).contains("no-store");
    assertThat(file.headers().firstValue("x-content-type-options")).contains("nosniff");
    assertThat(
            jdbc.queryForObject(
                "SELECT publicly_visible FROM app.media WHERE id=?",
                Boolean.class,
                UUID.fromString(id)))
        .isFalse();
    assertThat(
            upload(owner, "<svg/>".getBytes(StandardCharsets.UTF_8), "image/png", "proof.png")
                .statusCode())
        .isEqualTo(415);
    assertThat(upload(owner, image, "image/jpeg", "proof.jpg").statusCode()).isEqualTo(415);
    assertThat(upload(owner, new byte[10 * 1024 * 1024 + 1], "image/png", "proof.png").statusCode())
        .isEqualTo(413);
  }

  @Test
  void expiredTemporaryAndOrphanFilesAreCleanedButAttachedFilesArePreserved() throws Exception {
    String owner = token();
    UUID temporary =
        UUID.fromString(
            json.readTree(upload(owner, png(), "image/png", "proof.png").body())
                .path("id")
                .asString());
    UUID attached =
        UUID.fromString(
            json.readTree(upload(owner, png(), "image/png", "proof.png").body())
                .path("id")
                .asString());
    jdbc.update(
        "UPDATE app.media SET created_at=CURRENT_TIMESTAMP-interval '2 days' WHERE id IN (?,?)",
        temporary,
        attached);
    jdbc.update("UPDATE app.media SET state='ATTACHED' WHERE id=?", attached);
    UUID orphan = UUID.randomUUID();
    storage.write(orphan, new byte[] {1}, new byte[] {2});
    Files.setLastModifiedTime(
        MEDIA_ROOT.resolve(orphan + ".original"), FileTime.from(Instant.EPOCH));
    Files.setLastModifiedTime(MEDIA_ROOT.resolve(orphan + ".clean"), FileTime.from(Instant.EPOCH));
    cleanup.clean();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.media WHERE id=?", Integer.class, temporary))
        .isZero();
    assertThat(request("GET", "/api/v1/media/" + attached, owner).statusCode()).isEqualTo(200);
    assertThat(Files.exists(MEDIA_ROOT.resolve(orphan + ".original"))).isFalse();
  }

  @Test
  void uploadRateIsEnforcedPerMember() throws Exception {
    String owner = token();
    byte[] invalid = "invalid".getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < 10; i++)
      assertThat(upload(owner, invalid, "image/png", "proof.png").statusCode()).isEqualTo(415);
    assertThat(upload(owner, invalid, "image/png", "proof.png").statusCode()).isEqualTo(429);
    assertThat(upload(token(), png(), "image/png", "proof.png").statusCode()).isEqualTo(201);
  }

  private String token() {
    return jwt.issue(
        sessions
            .create(members.loginWithVerifiedKakaoSubject("test-media-" + UUID.randomUUID()))
            .session());
  }

  private UUID restaurant(boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,published) VALUES (?,?,'테스트 주소','OPEN','MISSING',?)",
        id,
        "가상 테스트 즐겨찾기 " + id,
        published);
    return id;
  }

  private HttpResponse<String> request(String method, String path, String token) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + token)
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> upload(String token, byte[] bytes, String type, String filename)
      throws Exception {
    String boundary = "test-boundary-" + UUID.randomUUID();
    var body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename
                + "\"\r\nContent-Type: "
                + type
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(bytes);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return client.send(
        HttpRequest.newBuilder(URI.create(base() + "/api/v1/media"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static byte[] png() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return bytes.toByteArray();
  }
}
