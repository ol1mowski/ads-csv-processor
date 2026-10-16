package pl.oliwier.adscsv.service.csv;

import pl.oliwier.adscsv.model.RejectReason;

enum Column {
  ADVERTISER_ID("advertiserId", FieldRule.ID, RejectReason.INVALID_ADVERTISER_ID),
  CAMPAIGN_ID("campaignId", FieldRule.ID, RejectReason.INVALID_CAMPAIGN_ID),
  CREATIVE_ID("creativeId", FieldRule.ID, RejectReason.INVALID_CREATIVE_ID),
  AD_BOX_UUID("adBoxUUID", FieldRule.UUID, RejectReason.INVALID_AD_BOX_UUID),
  IMPRESSIONS("impressions", FieldRule.COUNT, RejectReason.INVALID_IMPRESSIONS),
  CLICKS("clicks", FieldRule.COUNT, RejectReason.INVALID_CLICKS),
  REVENUE("revenue", FieldRule.DECIMAL, RejectReason.INVALID_REVENUE),
  ADVERTISER_MARGIN("advertiserMargin", FieldRule.DECIMAL, RejectReason.INVALID_ADVERTISER_MARGIN);

  private final String headerName;
  private final FieldRule rule;
  private final RejectReason rejectReason;

  Column(String headerName, FieldRule rule, RejectReason rejectReason) {
    this.headerName = headerName;
    this.rule = rule;
    this.rejectReason = rejectReason;
  }

  public String headerName() {
    return headerName;
  }

  public RejectReason rejectReason() {
    return rejectReason;
  }

  public boolean accepts(byte[] b, int from, int to) {
    return rule.accepts(b, from, to);
  }
}
