package kr.kimchimap.ingestion.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PublicDataTransport implements SourceTransport {
  @jakarta.annotation.PreDestroy
  public void close() {
    client.close();
  }

  private static final int MAX_BODY = 10 * 1024 * 1024;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  @Override
  public Response get(URI uri) {
    if (!uri.getScheme().equals("https")
        || !uri.getHost().equals("apis.data.go.kr")
        || uri.getPort() != -1
        || !uri.getPath().equals("/1741000/general_restaurants/info")) {
      throw new SourceFailure("SOURCE_URL_NOT_ALLOWED", false, null);
    }
    try {
      var response =
          client.send(
              HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
              info -> new LimitedBodySubscriber(MAX_BODY));
      return new Response(
          response.statusCode(),
          Map.of("retry-after", response.headers().firstValue("Retry-After").orElse("")),
          response.body());
    } catch (java.net.http.HttpTimeoutException exception) {
      throw new SourceFailure("SOURCE_TIMEOUT", true, null);
    } catch (java.io.IOException exception) {
      if (exception.getCause() instanceof SourceFailure failure) throw failure;
      throw new SourceFailure("SOURCE_NETWORK_ERROR", true, null);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SourceFailure("SOURCE_INTERRUPTED", true, null);
    }
  }
}
