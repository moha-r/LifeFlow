package lifeflow.model;

import java.util.List;
import java.util.Locale;

/**
 * A generic collection utility for domain entities that have string IDs.
 * Replaces duplicated lookup loops throughout the service layer.
 *
 * @param <T> the type of identifiable entity
 */
public final class Repository<T extends Identifiable> {
    private final List<T> items;

    /**
     * @param items the underlying collection to search
     */
    public Repository(List<T> items) {
        this.items = items;
    }

    /**
     * Finds an entity by its ID (case-insensitive).
     *
     * @param id the identifier to look up
     * @return the matching entity, or null if not found
     */
    public T findById(String id) {
        if (id == null) {
            return null;
        }
        return items.stream()
                .filter(item -> item.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Generates the next sequential ID with the given prefix.
     * Looks for the highest numeric suffix and increments it.
     *
     * @param prefix the prefix (e.g. "D" for donors)
     * @return the next ID (e.g. "D000001")
     */
    public String nextId(String prefix) {
        long highest = 0;
        for (T item : items) {
            String id = item.getId();
            if (id == null || id.length() <= prefix.length()
                    || !id.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String number = id.substring(prefix.length());
            if (!number.chars().allMatch(Character::isDigit)) {
                continue;
            }
            try {
                highest = Math.max(highest, Long.parseLong(number));
            } catch (NumberFormatException ignored) {
            }
        }
        if (highest == Long.MAX_VALUE) {
            throw new IllegalStateException("No more automatic IDs are available.");
        }
        return String.format(Locale.ROOT, "%s%06d", prefix, highest + 1);
    }
}
