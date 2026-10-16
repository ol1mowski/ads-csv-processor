package pl.oliwier.adscsv.service.processing;

import pl.oliwier.adscsv.model.FailureReason;

sealed interface ProcessingOutcome {

  record Completed() implements ProcessingOutcome {}

  record Failed(FailureReason reason) implements ProcessingOutcome {}

  record Cancelled() implements ProcessingOutcome {}

  record Released() implements ProcessingOutcome {}
}
