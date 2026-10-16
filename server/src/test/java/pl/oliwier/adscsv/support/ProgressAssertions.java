package pl.oliwier.adscsv.support;

import static org.assertj.core.api.Assertions.assertThat;

import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.Totals;

public final class ProgressAssertions {

  private ProgressAssertions() {}

  public static void assertNoProgress(JobResponse job) {
    assertThat(job.bytesProcessed()).isZero();
    assertThat(job.progressPercent()).isZero();
    assertThat(job.rowsProcessed()).isZero();
    assertThat(job.rowsRejected()).isZero();
    assertThat(job.rejectedSamples()).isEmpty();
    assertThat(job.totals()).isEqualTo(new Totals("0", "0", null));
  }
}
