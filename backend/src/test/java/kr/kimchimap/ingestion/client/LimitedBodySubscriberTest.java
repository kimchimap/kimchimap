package kr.kimchimap.ingestion.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("contract")
class LimitedBodySubscriberTest {
  @Test
  void oversizedStreamIsCancelledBeforeUnboundedAllocation() {
    var cancelled = new AtomicBoolean();
    var subscriber = new LimitedBodySubscriber(5);
    subscriber.onSubscribe(
        new Flow.Subscription() {
          public void request(long n) {}

          public void cancel() {
            cancelled.set(true);
          }
        });
    subscriber.onNext(List.of(ByteBuffer.wrap(new byte[4])));
    subscriber.onNext(List.of(ByteBuffer.wrap(new byte[2])));
    assertThat(cancelled).isTrue();
    assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
        .hasRootCauseInstanceOf(SourceFailure.class);
  }
}
