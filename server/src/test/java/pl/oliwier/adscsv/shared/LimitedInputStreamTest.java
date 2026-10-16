package pl.oliwier.adscsv.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitedInputStreamTest {

  @Test
  @DisplayName("Reading up to the limit counts every byte")
  void shouldCountBytesWhenReadingWithinLimit() throws IOException {
    var in = new LimitedInputStream(new ByteArrayInputStream(new byte[10]), 10);

    var read = in.readAllBytes();

    assertThat(read).hasSize(10);
    assertThat(in.count()).isEqualTo(10);
  }

  @Test
  @DisplayName("A single-byte read past the limit throws SizeLimitExceededException")
  void shouldThrowWhenSingleByteExceedsLimit() throws IOException {
    var in = new LimitedInputStream(new ByteArrayInputStream(new byte[3]), 2);
    in.read();
    in.read();

    assertThatThrownBy(in::read).isInstanceOf(SizeLimitExceededException.class);
  }

  @Test
  @DisplayName("A bulk read crossing the limit throws SizeLimitExceededException")
  void shouldThrowWhenBulkReadExceedsLimit() {
    var in = new LimitedInputStream(new ByteArrayInputStream(new byte[100]), 99);

    assertThatThrownBy(() -> in.read(new byte[100], 0, 100))
        .isInstanceOf(SizeLimitExceededException.class)
        .hasMessageContaining("99");
  }

  @Test
  @DisplayName("Skipped bytes count towards the limit")
  void shouldCountSkippedBytes() throws IOException {
    var in = new LimitedInputStream(new ByteArrayInputStream(new byte[10]), 5);

    assertThat(in.skip(4)).isEqualTo(4);
    assertThat(in.count()).isEqualTo(4);
    assertThatThrownBy(() -> in.skip(2)).isInstanceOf(SizeLimitExceededException.class);
  }

  @Test
  @DisplayName("End of stream does not change the count")
  void shouldNotCountEndOfStream() throws IOException {
    var in = new LimitedInputStream(new ByteArrayInputStream(new byte[0]), 5);

    assertThat(in.read()).isEqualTo(-1);
    assertThat(in.read(new byte[4], 0, 4)).isEqualTo(-1);
    assertThat(in.count()).isZero();
    assertThat(in.markSupported()).isFalse();
  }
}
