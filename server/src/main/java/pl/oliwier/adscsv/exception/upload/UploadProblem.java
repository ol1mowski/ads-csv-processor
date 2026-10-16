package pl.oliwier.adscsv.exception.upload;

public enum UploadProblem {
  EMPTY("EMPTY_FILE", "File is empty"),
  INTERRUPTED("UPLOAD_INTERRUPTED", "Upload ended before the declared number of bytes arrived"),
  TOO_LARGE("FILE_TOO_LARGE", "File exceeds the upload size limit"),
  INVALID_HEADER("INVALID_CSV_HEADER", "First line must be the expected 8-column header"),
  INSUFFICIENT_STORAGE("INSUFFICIENT_STORAGE", "Not enough storage space to accept the file");

  private final String code;
  private final String message;

  UploadProblem(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
