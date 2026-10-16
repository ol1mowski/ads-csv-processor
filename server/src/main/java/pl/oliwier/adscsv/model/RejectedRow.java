package pl.oliwier.adscsv.model;

public record RejectedRow(long line, RejectReason reason) {}
