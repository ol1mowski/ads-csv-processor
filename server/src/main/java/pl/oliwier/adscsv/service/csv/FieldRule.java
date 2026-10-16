package pl.oliwier.adscsv.service.csv;

enum FieldRule {
  ID {
    @Override
    public boolean accepts(byte[] b, int from, int to) {
      return digits(b, from, to, 18);
    }
  },
  COUNT {
    @Override
    public boolean accepts(byte[] b, int from, int to) {
      return digits(b, from, to, 10);
    }
  },
  UUID {
    @Override
    public boolean accepts(byte[] b, int from, int to) {
      if (to - from != 36) {
        return false;
      }
      for (var i = 0; i < 36; i++) {
        var c = b[from + i];
        var hyphen = i == 8 || i == 13 || i == 18 || i == 23;
        if (hyphen ? c != '-' : !isHex(c)) {
          return false;
        }
      }
      return true;
    }
  },
  DECIMAL {
    @Override
    public boolean accepts(byte[] b, int from, int to) {
      var dot = Bytes.indexOf(b, from, to, (byte) '.');
      return dot < 0
          ? digits(b, from, to, 15)
          : digits(b, from, dot, 15) && digits(b, dot + 1, to, 8);
    }
  };

  public abstract boolean accepts(byte[] b, int from, int to);

  private static boolean digits(byte[] b, int from, int to, int maxLength) {
    var length = to - from;
    if (length < 1 || length > maxLength) {
      return false;
    }
    for (var i = from; i < to; i++) {
      if (b[i] < '0' || b[i] > '9') {
        return false;
      }
    }
    return true;
  }

  private static boolean isHex(byte c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }
}
