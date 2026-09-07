package kr.kimchimap.origin;

import static kr.kimchimap.origin.entity.OriginValue.Classification.*;
import static kr.kimchimap.origin.service.OriginPublicationPolicy.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.entity.OriginValue.Component;
import kr.kimchimap.origin.service.OriginPublicationPolicy;
import kr.kimchimap.origin.service.OriginPublicationPolicy.Candidate;
import org.junit.jupiter.api.Test;

class OriginPublicationPolicyTest {
  private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
  private static final UUID SCOPE = UUID.randomUUID();
  private static final UUID INGREDIENT = UUID.randomUUID();
  private static final OriginValue DOMESTIC_VALUE =
      new OriginValue(DOMESTIC, List.of(new Component(DOMESTIC, "KR", null)));
  private final OriginPublicationPolicy policy = new OriginPublicationPolicy();

  @Test
  void differentScopesAndIngredientsCannotBeCombined() {
    var first =
        candidate(
            UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, NOW.minusSeconds(10), null, true, false);
    var second =
        candidate(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DOMESTIC_VALUE,
            NOW.minusSeconds(10),
            null,
            true,
            false);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> policy.decide(List.of(first, second), NOW));
  }

  @Test
  void contradictoryApprovedClaimsRemainDisputedRegardlessOfInputOrder() {
    var first =
        candidate(
            UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, NOW.minusSeconds(20), null, true, false);
    var second =
        candidate(
            UUID.randomUUID(),
            SCOPE,
            new OriginValue(
                IMPORTED_SPECIFIED, List.of(new Component(IMPORTED_SPECIFIED, "CN", null))),
            NOW.minusSeconds(10),
            null,
            true,
            false);
    assertThat(policy.decide(List.of(first, second), NOW).status()).isEqualTo(DISPUTED);
    assertThat(policy.decide(List.of(first, second), NOW))
        .isEqualTo(policy.decide(List.of(second, first), NOW));
    assertThat(policy.decide(List.of(first, second), NOW).selectedRecordId()).isNull();
  }

  @Test
  void explicitExpiryBoundaryPendingAndWithdrawalExcludePositiveSelection() {
    var expired =
        candidate(UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, NOW.minusSeconds(20), NOW, true, false);
    assertThat(policy.decide(List.of(expired), NOW).status()).isEqualTo(EXPIRED);
    assertThat(policy.decide(List.of(expired), NOW.minusSeconds(1)).status()).isEqualTo(CURRENT);
    var pending =
        candidate(
            UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, NOW.minusSeconds(20), null, false, false);
    var withdrawn =
        candidate(UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, NOW.minusSeconds(20), null, true, true);
    assertThat(policy.decide(List.of(pending, withdrawn), NOW).status()).isEqualTo(UNAVAILABLE);
  }

  @Test
  void observationDateAndStableIdDetermineRepresentativeWithoutFetchTimestamp() {
    var older =
        candidate(
            UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
            SCOPE,
            DOMESTIC_VALUE,
            NOW.minusSeconds(20),
            null,
            true,
            false);
    var newer =
        candidate(
            UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, NOW.minusSeconds(10), null, true, false);
    var unknown = candidate(UUID.randomUUID(), SCOPE, DOMESTIC_VALUE, null, null, true, false);
    assertThat(policy.decide(List.of(older, newer, unknown), NOW).selectedRecordId())
        .isEqualTo(newer.id());
    assertThat(policy.decide(List.of(unknown, newer, older), NOW))
        .isEqualTo(policy.decide(List.of(older, newer, unknown), NOW));
  }

  @Test
  void uuidTieBreakMatchesPostgresqlUnsignedOrdering() {
    var low =
        candidate(
            UUID.fromString("00000000-0000-4000-8000-000000000001"),
            SCOPE,
            DOMESTIC_VALUE,
            NOW.minusSeconds(10),
            null,
            true,
            false);
    var high =
        candidate(
            UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
            SCOPE,
            DOMESTIC_VALUE,
            NOW.minusSeconds(10),
            null,
            true,
            false);
    assertThat(policy.decide(List.of(low, high), NOW).selectedRecordId()).isEqualTo(high.id());
  }

  private Candidate candidate(
      UUID id,
      UUID scope,
      OriginValue value,
      Instant observed,
      Instant until,
      boolean approved,
      boolean withdrawn) {
    return new Candidate(
        id,
        scope,
        INGREDIENT,
        value,
        observed,
        null,
        NOW.minusSeconds(100),
        null,
        until,
        approved,
        withdrawn,
        1);
  }
}
