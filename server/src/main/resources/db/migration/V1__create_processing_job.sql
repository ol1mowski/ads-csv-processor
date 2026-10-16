CREATE TABLE processing_job (
    id                 uuid          PRIMARY KEY,
    owner              varchar(64)   NOT NULL,
    original_file_name varchar(255)  NOT NULL,
    status             varchar(16)   NOT NULL,
    size_bytes         bigint        NOT NULL,
    bytes_processed    bigint        NOT NULL DEFAULT 0,
    rows_processed     bigint        NOT NULL DEFAULT 0,
    rows_rejected      bigint        NOT NULL DEFAULT 0,
    total_impressions  bigint        NOT NULL DEFAULT 0,
    total_clicks       bigint        NOT NULL DEFAULT 0,
    rejected_samples   varchar(1024),
    failure_reason     varchar(32),
    attempts           integer       NOT NULL DEFAULT 0,
    created_at         timestamptz   NOT NULL,
    started_at         timestamptz,
    finished_at        timestamptz,
    CONSTRAINT ck_job_owner     CHECK (owner <> ''),
    CONSTRAINT ck_job_status    CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED','CANCELLED')),
    CONSTRAINT ck_job_size      CHECK (size_bytes > 0),
    CONSTRAINT ck_job_progress  CHECK (bytes_processed BETWEEN 0 AND size_bytes),
    CONSTRAINT ck_job_counters  CHECK (rows_processed >= 0 AND rows_rejected >= 0
                                       AND total_impressions >= 0 AND total_clicks >= 0 AND attempts >= 0),
    CONSTRAINT ck_job_failure   CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL))
);

CREATE INDEX ix_job_owner_history ON processing_job (owner, created_at DESC, id DESC);
CREATE INDEX ix_job_queue         ON processing_job (created_at, id) WHERE status = 'QUEUED';
