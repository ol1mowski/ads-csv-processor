package pl.oliwier.adscsv.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.integration.support.ApiSession;
import pl.oliwier.adscsv.integration.support.JobRows;
import pl.oliwier.adscsv.integration.support.JobsApi;
import pl.oliwier.adscsv.integration.support.LatchingColumnTransformation;
import pl.oliwier.adscsv.integration.support.RequestThreadRecorder;
import pl.oliwier.adscsv.integration.support.StorageInspector;
import pl.oliwier.adscsv.integration.support.StreamingHttp;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.TestUsers;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "app.processing.poll-interval=100ms",
      "app.processing.progress-interval=50ms",
      "app.processing.check-every=4KB",
      "app.storage.root=${java.io.tmpdir}/adscsv-it-processing-${random.uuid}"
    })
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({IntegrationTestConfiguration.class, ProcessingTestConfiguration.class})
public abstract class ProcessingEnabledTestBase {

  protected static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);

  private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  @Autowired protected RestTestClient restTestClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired protected StorageInspector storage;
  @Autowired protected LatchingColumnTransformation transformation;
  @Autowired protected RequestThreadRecorder requestThreads;

  @LocalServerPort protected int port;

  protected ApiSession session;
  protected JobsApi api;
  protected JobRows rows;

  protected ApiSession login(String username, String password) {
    return ApiSession.login(restTestClient, username, password);
  }

  protected ApiSession anonymous() {
    return ApiSession.anonymous(restTestClient);
  }

  protected JobsApi apiFor(ApiSession other) {
    return new JobsApi(other.bind(restTestClient));
  }

  protected StreamingHttp streaming() {
    return new StreamingHttp(port, session);
  }

  @BeforeEach
  void isolateFromOtherTests() throws IOException {
    session = ApiSession.login(restTestClient, TestUsers.ALICE, TestUsers.ALICE_PASSWORD);
    api = new JobsApi(session.bind(restTestClient));
    rows = new JobRows(jdbcTemplate);
    transformation.disarm();
    await("no queued or processing jobs left by earlier tests")
        .atMost(IDLE_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> rows.activeCount() == 0);
    rows.truncate();
    storage.clear();
    requestThreads.clear();
  }

  protected JobResponse awaitFinished(UUID id) {
    return awaitFinished(id, PROCESSING_TIMEOUT);
  }

  protected JobResponse awaitFinished(UUID id, Duration timeout) {
    return await("job " + id + " to finish")
        .atMost(timeout)
        .pollInterval(POLL_INTERVAL)
        .until(() -> api.get(id), job -> !job.status().isActive());
  }

  protected JobResponse awaitStatus(UUID id, JobStatus expected) {
    var job = awaitFinished(id);
    assertThat(job.status()).as("final status of job %s", id).isEqualTo(expected);
    return job;
  }
}
