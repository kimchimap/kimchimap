package kr.kimchimap.restaurant.entity;

public record BusinessPhone(String display, String number) {
  public static BusinessPhone parse(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > 64) return null;
    String display = raw.strip();
    if (!display.matches("[+0-9()\\-\\s]+")) return null;
    String number = display.replaceAll("[()\\-\\s]", "");
    if (!number.matches("\\+?[0-9]{8,15}")) return null;
    return new BusinessPhone(display, number);
  }
}
