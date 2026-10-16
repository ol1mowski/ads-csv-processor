package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;

class JobHistoryIntegrationTest extends ProcessingEnabledTestBase {

  private static final byte[] VALID = SampleData.csv(SampleData.FIRST_ROW);
  private static final byte[] INVALID = SampleData.csv("not;a;valid;row");

  @Autowired private Clock clock;

  @Test
  @DisplayName("History lists uploads newest first with default paging")
  void shouldListUploadsNewestFirst() {
    var first = api.accepted("first.csv", VALID);
    var second = uploadAfter(first, "second.csv");
    var third = uploadAfter(second, "third.csv");

    var page = api.page("");

    assertThat(page.items())
        .extracting(JobResponse::id)
        .containsExactly(third.id(), second.id(), first.id());
    assertThat(page.items())
        .extracting(JobResponse::fileName)
        .containsExactly("third.csv", "second.csv", "first.csv");
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("History filters by one or many statuses and paginates the filtered set")
  void shouldFilterByStatusAndPaginate() {
    var completedA = finish("a.csv", VALID, JobStatus.COMPLETED);
    var completedB = finish("b.csv", VALID, JobStatus.COMPLETED);
    var failed = finish("bad.csv", INVALID, JobStatus.FAILED);

    var firstPage = api.page("?status=COMPLETED&size=1&page=0");
    var secondPage = api.page("?status=COMPLETED&size=1&page=1");
    var failedOnly = api.page("?status=FAILED");
    var both = api.page("?status=COMPLETED&status=FAILED&size=2");

    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.items()).hasSize(1);
    assertThat(secondPage.items()).hasSize(1);
    assertThat(
            Stream.concat(firstPage.items().stream(), secondPage.items().stream())
                .map(JobResponse::id))
        .containsExactlyInAnyOrder(completedA, completedB);
    assertThat(failedOnly.items()).extracting(JobResponse::id).containsExactly(failed);
    assertThat(both.totalElements()).isEqualTo(3);
    assertThat(both.totalPages()).isEqualTo(2);
    assertThat(both.items()).hasSize(2);
  }

  @Test
  @DisplayName("An empty status parameter means no filter")
  void shouldTreatEmptyStatusAsNoFilter() {
    finish("a.csv", VALID, JobStatus.COMPLETED);
    finish("bad.csv", INVALID, JobStatus.FAILED);

    assertThat(api.page("?status=").totalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("A page size above the maximum is clamped to 100")
  void shouldClampPageSizeToMaximum() {
    api.accepted("a.csv", VALID);

    var page = api.page("?size=1000");

    assertThat(page.size()).isEqualTo(100);
    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("The last allowed page is served and the page after it is refused with 400")
  void shouldServeLastAllowedPageWhenOffsetIsLarge() {
    api.accepted("a.csv", VALID);

    var last = api.page("?page=1000000&size=1000");

    assertThat(last.items()).isEmpty();
    assertThat(last.totalElements()).isEqualTo(1);
    api.history("?page=1000001").expectStatus().isBadRequest();
  }

  private JobResponse uploadAfter(JobResponse previous, String fileName) {
    await("clock to move past the previous upload")
        .until(() -> clock.instant().isAfter(previous.createdAt()));
    return api.accepted(fileName, VALID);
  }

  private UUID finish(String fileName, byte[] body, JobStatus expected) {
    var id = api.accepted(fileName, body).id();
    awaitStatus(id, expected);
    return id;
  }
}
