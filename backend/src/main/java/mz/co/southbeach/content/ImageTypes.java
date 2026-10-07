package mz.co.southbeach.content;

/** Recognises JPEG, PNG and WebP from the file's own signature; the client's claimed type is never trusted. */
public final class ImageTypes {
    private ImageTypes() { }

    private static final java.util.regex.Pattern URL = java.util.regex.Pattern.compile(
            "^(https://[^\\s\"'<>()\\\\]{4,480}|/api/media/\\d{1,18})$");

    /** An https address or one of our own uploaded files; anything else (http, data:, javascript:) is refused. */
    public static boolean isAllowedUrl(String value) {
        return value != null && URL.matcher(value).matches();
    }

    public static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
