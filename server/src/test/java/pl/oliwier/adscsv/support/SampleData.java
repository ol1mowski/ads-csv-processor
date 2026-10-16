package pl.oliwier.adscsv.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class SampleData {

  public static final String HEADER =
      "advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin";
  public static final String FIRST_ROW =
      "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927";
  public static final String ZERO_IMPRESSIONS_ROW =
      "6038;10621;511568;c0398710-8976-4334-a281-7efdae849217;0;0;0.0000;0.1213";
  public static final long SIZE_BYTES = 77_752;
  public static final long ROWS = 1000;
  public static final long TOTAL_IMPRESSIONS = 6_733_389;
  public static final long TOTAL_CLICKS = 176_755;

  private SampleData() {}

  public static byte[] bytes() {
    try (InputStream in = SampleData.class.getResourceAsStream("/sample_ads_data.csv")) {
      if (in == null) {
        throw new IllegalStateException("sample_ads_data.csv is missing from test resources");
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] wholeLinesUpTo(int maxBytes) {
    var sample = bytes();
    var end = 0;
    for (var i = 0; i < sample.length && i < maxBytes; i++) {
      if (sample[i] == '\n') {
        end = i + 1;
      }
    }
    return Arrays.copyOf(sample, end);
  }

  public static byte[] csv(String... rows) {
    var content = new StringBuilder(HEADER).append('\n');
    for (var row : rows) {
      content.append(row).append('\n');
    }
    return content.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static String row(long impressions, long clicks) {
    return "1009;10119;502452;f143262f-dc5c-4eed-8da0-365bf89897b9;"
        + impressions
        + ";"
        + clicks
        + ";1.8925;0.3246";
  }
}
