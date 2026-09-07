package kr.kimchimap.ingestion.client;

import java.net.URI;
import java.util.Map;

public interface SourceTransport {
  record Response(int status, Map<String, String> headers, byte[] body) {}

  Response get(URI uri);
}
