package pl.oliwier.adscsv.service.csv;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.service.transformation.ColumnTransformation;

@Component
@RequiredArgsConstructor
public class RowTransformer {

  private static final Column[] COLUMNS = Column.values();
  private static final int[] WRONG_FIELD_COUNT = new int[0];

  private final ColumnTransformation transformation;

  public byte[] outputHeader() {
    var header = AdsCsvFormat.HEADER + AdsCsvFormat.SEPARATOR + transformation.columnName() + "\n";
    return header.getBytes(StandardCharsets.US_ASCII);
  }

  public RowResult transform(Line line) {
    if (line.isTooLong()) {
      return new RowResult.Rejected(RejectReason.LINE_TOO_LONG);
    }
    var fieldStarts = splitFields(line);
    if (fieldStarts == WRONG_FIELD_COUNT) {
      return new RowResult.Rejected(RejectReason.FIELD_COUNT);
    }
    for (var column : COLUMNS) {
      var accepted =
          column.accepts(
              line.buffer(),
              ParsedRow.from(fieldStarts, column),
              ParsedRow.to(fieldStarts, column));
      if (!accepted) {
        return new RowResult.Rejected(column.rejectReason());
      }
    }
    var row = new ParsedRow(line.buffer(), fieldStarts);
    if (row.clicks() > row.impressions()) {
      return new RowResult.Rejected(RejectReason.CLICKS_EXCEED_IMPRESSIONS);
    }
    return new RowResult.Transformed(transformation.apply(row), row.impressions(), row.clicks());
  }

  private static int[] splitFields(Line line) {
    var buffer = line.buffer();
    var fieldStarts = new int[AdsCsvFormat.FIELD_COUNT + 1];
    var field = 0;
    fieldStarts[0] = line.start();
    for (var i = line.start(); i < line.end(); i++) {
      if (buffer[i] != AdsCsvFormat.SEPARATOR) {
        continue;
      }
      if (++field == AdsCsvFormat.FIELD_COUNT) {
        return WRONG_FIELD_COUNT;
      }
      fieldStarts[field] = i + 1;
    }
    if (field != AdsCsvFormat.FIELD_COUNT - 1) {
      return WRONG_FIELD_COUNT;
    }
    fieldStarts[AdsCsvFormat.FIELD_COUNT] = line.end() + 1;
    return fieldStarts;
  }
}
