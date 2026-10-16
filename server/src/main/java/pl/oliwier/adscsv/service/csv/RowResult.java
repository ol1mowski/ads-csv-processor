package pl.oliwier.adscsv.service.csv;

import pl.oliwier.adscsv.model.RejectReason;

public sealed interface RowResult {

  record Transformed(String derivedValue, long impressions, long clicks) implements RowResult {}

  record Rejected(RejectReason reason) implements RowResult {}
}
