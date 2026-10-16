package pl.oliwier.adscsv.service.csv;

final class Bytes {

  private Bytes() {}

  static int indexOf(byte[] bytes, int from, int to, byte value) {
    for (var i = from; i < to; i++) {
      if (bytes[i] == value) {
        return i;
      }
    }
    return -1;
  }
}
