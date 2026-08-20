package lifeflow.service;

/** Admin credential check for the LifeFlow desktop application. */
public final class AdminAuth {
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    private AdminAuth() {
    }

    public static boolean authenticate(String username, String password) {
        return DEFAULT_USERNAME.equals(username) && DEFAULT_PASSWORD.equals(password);
    }
}