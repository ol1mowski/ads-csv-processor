package pl.oliwier.adscsv.model;

public record ProgressSnapshot(
    long bytesProcessed, long rowsProcessed, long rowsRejected, long impressions, long clicks) {}
