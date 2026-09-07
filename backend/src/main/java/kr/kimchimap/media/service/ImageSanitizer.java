package kr.kimchimap.media.service;

import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import kr.kimchimap.global.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ImageSanitizer {
  public static final int MAX_ORIGINAL_BYTES = 10 * 1024 * 1024;
  private static final int MAX_SANITIZED_BYTES = 20 * 1024 * 1024;
  private final ExecutorService workers =
      new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new SynchronousQueue<>());

  public record Sanitized(byte[] bytes, String contentType, int width, int height) {}

  public Sanitized sanitize(byte[] original, String claimedType, String filename) {
    if (original.length == 0 || original.length > MAX_ORIGINAL_BYTES)
      throw new ApiException(
          HttpStatus.CONTENT_TOO_LARGE, "IMAGE_SIZE_LIMIT", "사진은 10MB 이하로 첨부해 주세요.");
    Future<Sanitized> future;
    try {
      future = workers.submit(() -> decode(original, claimedType, filename));
    } catch (RejectedExecutionException exception) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "IMAGE_PROCESSING_BUSY",
          "사진 처리 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (TimeoutException exception) {
      future.cancel(true);
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "IMAGE_PROCESSING_TIMEOUT",
          "사진 처리 시간이 초과되었습니다. 크기를 줄여 다시 첨부해 주세요.");
    } catch (InterruptedException exception) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_PROCESSING_INTERRUPTED", "사진 처리가 중단되었습니다.");
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof ApiException problem) throw problem;
      throw invalid();
    }
  }

  private Sanitized decode(byte[] original, String claimedType, String filename)
      throws IOException {
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid();
      var reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("jpeg", "png").contains(format)) throw invalid();
        String contentType = "image/" + format;
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!contentType.equals(claimedType)
            || !(format.equals("jpeg")
                ? name.endsWith(".jpg") || name.endsWith(".jpeg")
                : name.endsWith(".png"))) throw invalid();
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 8192
            || height > 8192
            || (long) width * height > 20_000_000)
          throw new ApiException(
              HttpStatus.CONTENT_TOO_LARGE,
              "IMAGE_DIMENSION_LIMIT",
              "사진은 가로·세로 8192픽셀, 총 2천만 픽셀 이하로 첨부해 주세요.");
        var warned = new AtomicBoolean();
        reader.addIIOReadWarningListener((source, warning) -> warned.set(true));
        var decoded = reader.read(0);
        if (decoded == null || warned.get() || Thread.currentThread().isInterrupted())
          throw invalid();
        var clean =
            new BufferedImage(
                width,
                height,
                format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        var graphics = clean.createGraphics();
        try {
          if (format.equals("jpeg")) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
          }
          graphics.drawImage(decoded, 0, 0, null);
        } finally {
          graphics.dispose();
          decoded.flush();
        }
        try {
          var bytes = new BoundedOutput();
          try (var output = new MemoryCacheImageOutputStream(bytes)) {
            if (!ImageIO.write(clean, format, output)) throw invalid();
          }
          return new Sanitized(bytes.toByteArray(), contentType, width, height);
        } finally {
          clean.flush();
        }
      } finally {
        reader.dispose();
      }
    }
  }

  private static ApiException invalid() {
    return new ApiException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "INVALID_IMAGE",
        "파일 내용과 확장자가 일치하는 정상적인 JPEG 또는 PNG 사진을 첨부해 주세요.");
  }

  private static final class BoundedOutput extends ByteArrayOutputStream {
    private void ensure(int length) {
      if ((long) count + length > MAX_SANITIZED_BYTES)
        throw new ApiException(
            HttpStatus.CONTENT_TOO_LARGE, "IMAGE_OUTPUT_LIMIT", "처리된 사진이 너무 큽니다. 해상도를 줄여 주세요.");
    }

    @Override
    public synchronized void write(int value) {
      ensure(1);
      super.write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      ensure(length);
      super.write(bytes, offset, length);
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
