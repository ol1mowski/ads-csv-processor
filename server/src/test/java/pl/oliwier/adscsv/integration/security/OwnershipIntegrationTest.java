package pl.oliwier.adscsv.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.JobsApi;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;
import pl.oliwier.adscsv.support.TestUsers;

class OwnershipIntegrationTest extends ProcessingDisabledTestBase {

  private static final byte[] CSV = SampleData.csv(SampleData.FIRST_ROW);

  private JobsApi bob;

  @BeforeEach
  void logInBob() {
    bob = apiFor(login(TestUsers.BOB, TestUsers.BOB_PASSWORD));
  }

  @Test
  @DisplayName("Another user's job answers exactly like a job that does not exist")
  void shouldAnswer404WhenJobBelongsToAnotherUser() {
    var aliceJob = api.accepted("alice.csv", CSV).id();
    var unknown = UUID.randomUUID();

    assertSameNotFound(
        bob.job(aliceJob).expectBody(String.class).returnResult().getResponseBody(),
        bob.job(unknown).expectBody(String.class).returnResult().getResponseBody(),
        aliceJob,
        unknown);
    assertSameNotFound(
        bob.cancel(aliceJob).expectBody(String.class).returnResult().getResponseBody(),
        bob.cancel(unknown).expectBody(String.class).returnResult().getResponseBody(),
        aliceJob,
        unknown);
    assertSameNotFound(
        bob.result(aliceJob).expectBody(String.class).returnResult().getResponseBody(),
        bob.result(unknown).expectBody(String.class).returnResult().getResponseBody(),
        aliceJob,
        unknown);
    bob.job(aliceJob)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("JOB_NOT_FOUND");
    bob.cancel(aliceJob).expectStatus().isNotFound();
    bob.result(aliceJob).expectStatus().isNotFound();
    api.result(aliceJob).expectStatus().isEqualTo(409);
  }

  @Test
  @DisplayName("A cancel attempt on another user's job changes nothing")
  void shouldLeaveJobUntouchedWhenAnotherUserCancelsIt() {
    var aliceJob = api.accepted("alice.csv", CSV).id();

    bob.cancel(aliceJob).expectStatus().isNotFound();

    assertThat(rows.status(aliceJob)).isEqualTo(JobStatus.QUEUED);
    assertThat(storage.input(aliceJob)).exists();
    assertThat(api.get(aliceJob).status()).isEqualTo(JobStatus.QUEUED);
  }

  @Test
  @DisplayName("History lists only the caller's own jobs, with and without a status filter")
  void shouldListOnlyOwnJobsInHistory() {
    var aliceJob = api.accepted("alice.csv", CSV).id();
    var bobJob = bob.accepted("bob.csv", CSV).id();

    assertThat(api.page("").items()).extracting(JobResponse::id).containsExactly(aliceJob);
    assertThat(bob.page("").items()).extracting(JobResponse::id).containsExactly(bobJob);
    assertThat(bob.page("?status=QUEUED").items())
        .extracting(JobResponse::id)
        .containsExactly(bobJob);
    assertThat(api.page("?status=QUEUED").totalElements()).isEqualTo(1);
    assertThat(rows.owner(aliceJob)).isEqualTo(TestUsers.ALICE);
    assertThat(rows.owner(bobJob)).isEqualTo(TestUsers.BOB);
  }

  @Test
  @DisplayName("The job document never exposes its owner")
  void shouldNotExposeOwnerInJobDocument() {
    var aliceJob = api.accepted("report.csv", CSV).id();

    var raw =
        new String(
            api.job(aliceJob).expectStatus().isOk().expectBody().returnResult().getResponseBody(),
            StandardCharsets.UTF_8);

    assertThat(raw).doesNotContain("owner").doesNotContain(TestUsers.ALICE);
  }

  private static void assertSameNotFound(
      String forOtherUsersJob, String forUnknownJob, UUID otherUsersJob, UUID unknown) {
    assertThat(forOtherUsersJob.replace(otherUsersJob.toString(), "{id}"))
        .isEqualTo(forUnknownJob.replace(unknown.toString(), "{id}"));
  }
}
