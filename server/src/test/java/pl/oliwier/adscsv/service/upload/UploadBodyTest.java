package pl.oliwier.adscsv.service.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UploadBodyTest {

  @Test
  @DisplayName("The first line is read including its newline and the rest stays in the stream")
  void shouldLeaveBytesAfterFirstLineUnread() throws IOException {
    var in = new ByteArrayInputStream("head\r\nrow1\nrow2\n".getBytes(StandardCharsets.US_ASCII));

    var line = UploadBody.readFirstLine(in, 1024);

    assertThat(new String(line, StandardCharsets.US_ASCII)).isEqualTo("head\r\n");
    assertThat(new String(in.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("row1\nrow2\n");
  }

  @Test
  @DisplayName("Reading stops at the byte limit when no newline arrives")
  void shouldStopAtLimitWhenLineHasNoNewline() throws IOException {
    var in = new ByteArrayInputStream("abcdefgh".getBytes(StandardCharsets.US_ASCII));

    var line = UploadBody.readFirstLine(in, 3);

    assertThat(new String(line, StandardCharsets.US_ASCII)).isEqualTo("abc");
    assertThat(in.available()).isEqualTo(5);
  }

  @Test
  @DisplayName("A stream that ends before a newline returns everything read")
  void shouldReturnAllBytesWhenStreamEndsFirst() throws IOException {
    var in = new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII));

    assertThat(UploadBody.readFirstLine(in, 1024)).hasSize(3);
    assertThat(UploadBody.readFirstLine(in, 1024)).isEmpty();
  }
}
