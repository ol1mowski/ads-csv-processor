package pl.oliwier.adscsv.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import pl.oliwier.adscsv.exception.upload.InvalidUploadException;
import pl.oliwier.adscsv.exception.upload.UploadProblem;

public final class UploadAssertions {

  private UploadAssertions() {}

  public static Consumer<Throwable> uploadProblem(UploadProblem expected) {
    return thrown ->
        assertThat(thrown)
            .isInstanceOfSatisfying(
                InvalidUploadException.class, e -> assertThat(e.problem()).isEqualTo(expected));
  }
}
