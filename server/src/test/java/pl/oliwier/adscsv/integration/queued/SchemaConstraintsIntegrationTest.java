package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;

class SchemaConstraintsIntegrationTest extends ProcessingDisabledTestBase {

  private static final String INSERT =
      """
      INSERT INTO processing_job (id, owner, original_file_name, status, size_bytes,
                                  bytes_processed, failure_reason, created_at)
      VALUES (?, 'alice', 'constraint.csv', ?, ?, ?, ?, now())
      """;

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("Flyway created the schema that Hibernate validated at startup")
  void shouldApplyTheMigrationsValidatedByHibernate() {
    var applied =
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
            String.class);

    assertThat(applied).containsExactly("1");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "")
  @DisplayName("A job without an owner is rejected by the database")
  void shouldRejectJobWhenOwnerIsMissing(String owner) {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO processing_job (id, owner, original_file_name, status, size_bytes,
                                                created_at)
                    VALUES (?, ?, 'ownerless.csv', 'QUEUED', 100, now())
                    """,
                    UUID.randomUUID(),
                    owner))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("owner");
  }

  @Test
  @DisplayName("A consistent failed row is accepted by every constraint")
  void shouldAcceptConsistentFailedRow() {
    assertThat(insert("FAILED", 100, 0, "PROCESSING_ERROR")).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}: status={1} size={2} bytes={3} reason={4}")
  @CsvSource(
      delimiter = '|',
      nullValues = "NULL",
      textBlock =
          """
          ck_job_failure  | FAILED    | 100 | 0   | NULL
          ck_job_failure  | COMPLETED | 100 | 0   | PROCESSING_ERROR
          ck_job_size     | QUEUED    | 0   | 0   | NULL
          ck_job_progress | QUEUED    | 100 | 101 | NULL
          ck_job_status   | DONE      | 100 | 0   | NULL
          """)
  @DisplayName("Rows breaking the state machine invariants are rejected by the database")
  void shouldRejectRowViolatingConstraint(
      String constraint, String status, long sizeBytes, long bytesProcessed, String failureReason) {
    assertThatThrownBy(() -> insert(status, sizeBytes, bytesProcessed, failureReason))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  private int insert(String status, long sizeBytes, long bytesProcessed, String failureReason) {
    return jdbc.update(INSERT, UUID.randomUUID(), status, sizeBytes, bytesProcessed, failureReason);
  }
}
