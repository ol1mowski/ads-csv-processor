package pl.oliwier.adscsv.service.storage;

import org.springframework.core.io.Resource;

public record StoredFile(Resource content, long sizeBytes) {}
