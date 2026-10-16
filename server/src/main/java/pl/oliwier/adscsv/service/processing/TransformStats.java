package pl.oliwier.adscsv.service.processing;

import java.util.ArrayList;
import java.util.List;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;

final class TransformStats {

  private final int maxSamples;
  private final long minRejectedToAbort;
  private final double maxRejectedRatio;
  private final List<RejectedRow> samples = new ArrayList<>();
  private long accepted;
  private long rejected;
  private long impressions;
  private long clicks;
  private long bytesConsumed;

  TransformStats(int maxSamples, long minRejectedToAbort, double maxRejectedRatio) {
    this.maxSamples = maxSamples;
    this.minRejectedToAbort = minRejectedToAbort;
    this.maxRejectedRatio = maxRejectedRatio;
  }

  void accept(long rowImpressions, long rowClicks) {
    accepted++;
    impressions = Math.addExact(impressions, rowImpressions);
    clicks = Math.addExact(clicks, rowClicks);
  }

  void reject(long line, RejectReason reason) {
    rejected++;
    if (samples.size() < maxSamples) {
      samples.add(new RejectedRow(line, reason));
    }
  }

  boolean rejectedTooMany() {
    return rejected > minRejectedToAbort && rejectedAboveRatio();
  }

  boolean rejectedAboveRatio() {
    return rejected > maxRejectedRatio * (accepted + rejected);
  }

  boolean hasOnlyRejected() {
    return accepted == 0 && rejected > 0;
  }

  void consumed(long bytes) {
    bytesConsumed = bytes;
  }

  ProgressSnapshot snapshot() {
    return new ProgressSnapshot(bytesConsumed, accepted, rejected, impressions, clicks);
  }

  List<RejectedRow> samples() {
    return List.copyOf(samples);
  }
}
