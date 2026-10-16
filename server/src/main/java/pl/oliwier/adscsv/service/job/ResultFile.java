package pl.oliwier.adscsv.service.job;

import org.springframework.core.io.Resource;

public record ResultFile(Resource content, String downloadName, long sizeBytes) {}
