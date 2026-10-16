package pl.oliwier.adscsv.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.processing")
public record ProcessingProperties(
    @Min(1) @Max(16) int workers,
    @NotNull Duration pollInterval,
    @NotNull Duration progressInterval,
    @NotNull DataSize checkEvery,
    @Min(1) int maxAttempts,
    @Min(0) long minRejectedToAbort,
    @DecimalMin("0.0") @DecimalMax("1.0") double maxRejectedRatio,
    @Min(0) @Max(15) int rejectedSamples,
    @NotNull Duration shutdownWait) {}
