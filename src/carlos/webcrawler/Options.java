package carlos.webcrawler;

public enum Options {
    DEBUG_MODE(0),
    SAVE_LINKS(1),
    SAVE_CONTENT(2),
    UNLIMITED(3),
    RESTRICT_LANGUAGE(4);

    final byte position;

    Options(int position) {
        this.position = (byte) position;
    }
}
