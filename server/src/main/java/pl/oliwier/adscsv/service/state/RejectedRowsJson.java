package pl.oliwier.adscsv.service.state;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.oliwier.adscsv.model.RejectedRow;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class RejectedRowsJson {

  private final JsonMapper jsonMapper;

  public String write(List<RejectedRow> rows) {
    return jsonMapper.writeValueAsString(rows);
  }

  public List<RejectedRow> read(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    return jsonMapper.readerForListOf(RejectedRow.class).readValue(json);
  }
}
