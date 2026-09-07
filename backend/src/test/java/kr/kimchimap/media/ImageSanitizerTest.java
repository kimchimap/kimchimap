package kr.kimchimap.media;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.media.service.ImageSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest {
  private final ImageSanitizer sanitizer = new ImageSanitizer();

  @AfterEach
  void close() {
    sanitizer.close();
  }

  @Test
  void decodesAndReencodesWithoutPrivateMetadata() throws Exception {
    var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    var writer = ImageIO.getImageWritersByFormatName("png").next();
    var params = writer.getDefaultWriteParam();
    var metadata =
        writer.getDefaultImageMetadata(
            javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), params);
    var root = new IIOMetadataNode("javax_imageio_png_1.0");
    var text = new IIOMetadataNode("tEXt");
    var entry = new IIOMetadataNode("tEXtEntry");
    entry.setAttribute("keyword", "Comment");
    entry.setAttribute("value", "test-private-location");
    text.appendChild(entry);
    root.appendChild(text);
    metadata.mergeTree("javax_imageio_png_1.0", root);
    var bytes = new ByteArrayOutputStream();
    try (var output = new MemoryCacheImageOutputStream(bytes)) {
      writer.setOutput(output);
      writer.write(null, new javax.imageio.IIOImage(image, null, metadata), params);
    } finally {
      writer.dispose();
    }
    assertThat(new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1))
        .contains("test-private-location");
    var clean = sanitizer.sanitize(bytes.toByteArray(), "image/png", "proof.png");
    assertThat(new String(clean.bytes(), StandardCharsets.ISO_8859_1))
        .doesNotContain("test-private-location");
    assertThat(ImageIO.read(new ByteArrayInputStream(clean.bytes())).getWidth()).isEqualTo(16);
    assertThat(clean.contentType()).isEqualTo("image/png");
  }

  @Test
  void rejectsExecutableDisguisedTruncatedAndMismatchedFiles() throws Exception {
    byte[] png = png();
    for (byte[] invalid :
        new byte[][] {
          "<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8),
          "<html>test</html>".getBytes(StandardCharsets.UTF_8),
          new byte[] {(byte) 0xff, (byte) 0xd8, 0}
        })
      assertThatThrownBy(() -> sanitizer.sanitize(invalid, "image/jpeg", "proof.jpg"))
          .isInstanceOf(ApiException.class)
          .extracting("code")
          .isEqualTo("INVALID_IMAGE");
    assertThatThrownBy(() -> sanitizer.sanitize(png, "image/jpeg", "proof.jpg"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> sanitizer.sanitize(png, "image/png", "proof.svg"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                sanitizer.sanitize(
                    new byte[ImageSanitizer.MAX_ORIGINAL_BYTES + 1], "image/png", "proof.png"))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("IMAGE_SIZE_LIMIT");
  }

  @Test
  void rejectsDimensionsAndPixelBombBeforeDecodingPixels() throws Exception {
    for (int[] dimensions : new int[][] {{8193, 1}, {5000, 5000}}) {
      byte[] png = png();
      ByteBuffer.wrap(png).putInt(16, dimensions[0]).putInt(20, dimensions[1]);
      var crc = new CRC32();
      crc.update(png, 12, 17);
      ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
      assertThatThrownBy(() -> sanitizer.sanitize(png, "image/png", "proof.png"))
          .isInstanceOf(ApiException.class)
          .extracting("code")
          .isEqualTo("IMAGE_DIMENSION_LIMIT");
    }
  }

  @Test
  void stripsJpegExifApplicationSegment() throws Exception {
    var jpeg = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
    byte[] header =
        new byte[] {'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 42, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    var metadata = new ByteArrayOutputStream();
    metadata.write(header);
    metadata.write("test-private-exif".getBytes(StandardCharsets.ISO_8859_1));
    byte[] marker = metadata.toByteArray();
    var original = new ByteArrayOutputStream();
    original.write(jpeg.toByteArray(), 0, 2);
    original.write(
        new byte[] {
          (byte) 0xff, (byte) 0xe1, (byte) ((marker.length + 2) >> 8), (byte) (marker.length + 2)
        });
    original.write(marker);
    original.write(jpeg.toByteArray(), 2, jpeg.size() - 2);
    var clean = sanitizer.sanitize(original.toByteArray(), "image/jpeg", "proof.jpg");
    assertThat(new String(clean.bytes(), StandardCharsets.ISO_8859_1))
        .doesNotContain("Exif", "test-private-exif");
    assertThat(ImageIO.read(new ByteArrayInputStream(clean.bytes())).getHeight()).isEqualTo(8);
  }

  private static byte[] png() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return bytes.toByteArray();
  }
}
