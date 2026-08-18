package lifeflow.ui;

import java.util.Objects;

/** Typed status message that avoids guessing severity from message text. */
public record UiNotice(NoticeLevel level, String message) {
    public UiNotice {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
    }

    public static UiNotice success(String message) {
        return new UiNotice(NoticeLevel.SUCCESS, message);
    }

    public static UiNotice info(String message) {
        return new UiNotice(NoticeLevel.INFO, message);
    }

    public static UiNotice warning(String message) {
        return new UiNotice(NoticeLevel.WARNING, message);
    }

    public static UiNotice error(String message) {
        return new UiNotice(NoticeLevel.ERROR, message);
    }
}
