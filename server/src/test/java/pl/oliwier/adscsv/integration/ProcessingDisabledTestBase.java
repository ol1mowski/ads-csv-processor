package pl.oliwier.adscsv.integration;

import java.io.IOException;
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
import pl.oliwier.adscsv.integration.support.ApiSession;
import pl.oliwier.adscsv.integration.support.JobRows;
import pl.oliwier.adscsv.integration.support.JobsApi;
import pl.oliwier.adscsv.integration.support.StorageInspector;
import pl.oliwier.adscsv.integration.support.StreamingHttp;
import pl.oliwier.adscsv.support.TestUsers;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "app.processing.enabled=false",
      "app.upload.max-size=64KB",
      "app.storage.root=${java.io.tmpdir}/adscsv-it-queued-${random.uuid}"
    })
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(IntegrationTestConfiguration.class)
public abstract class ProcessingDisabledTestBase {

  protected static final int MAX_UPLOAD_BYTES = 64 * 1024;

  @Autowired protected RestTestClient restTestClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired protected StorageInspector storage;

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
    rows.truncate();
    storage.clear();
  }
}
