export type JobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type FailureReason =
  | 'INVALID_HEADER'
  | 'NO_VALID_ROWS'
  | 'TOO_MANY_INVALID_ROWS'
  | 'INPUT_MISSING'
  | 'INTERRUPTED'
  | 'PROCESSING_ERROR';

export interface User {
  readonly username: string;
}

export type RejectReason =
  | 'LINE_TOO_LONG'
  | 'FIELD_COUNT'
  | 'INVALID_ADVERTISER_ID'
  | 'INVALID_CAMPAIGN_ID'
  | 'INVALID_CREATIVE_ID'
  | 'INVALID_AD_BOX_UUID'
  | 'INVALID_IMPRESSIONS'
  | 'INVALID_CLICKS'
  | 'INVALID_REVENUE'
  | 'INVALID_ADVERTISER_MARGIN'
  | 'CLICKS_EXCEED_IMPRESSIONS';

export interface RejectedSample {
  readonly line: number;
  readonly reason: RejectReason;
}

export interface JobTotals {
  readonly impressions: string;
  readonly clicks: string;
  readonly ctr: string | null;
}

export interface Job {
  readonly id: string;
  readonly fileName: string;
  readonly status: JobStatus;
  readonly sizeBytes: number;
  readonly bytesProcessed: number;
  readonly progressPercent: number;
  readonly rowsProcessed: number;
  readonly rowsRejected: number;
  readonly rejectedSamples: readonly RejectedSample[];
  readonly totals: JobTotals;
  readonly failureReason: FailureReason | null;
  readonly attempts: number;
  readonly createdAt: string;
  readonly startedAt: string | null;
  readonly finishedAt: string | null;
  readonly resultUrl: string | null;
}

export interface JobPage {
  readonly items: readonly Job[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}
