package app.xposed.extension;

import java.util.List;

/**
 * Reads X's models: the User interface, ProfileUser, AvailablePost and CanonicalPost. R8
 * renames their members in every release, so each body here is replaced at patch time with
 * bytecode that uses the names found in that APK (see docs/HOOKS.md). Unknown values come
 * back as null, 0 or -1, never as a guess.
 */
@SuppressWarnings("unused")
public final class ModelBridge {
    private ModelBridge() {
    }

    private static UnsupportedOperationException notPatched() {
        return new UnsupportedOperationException("Not patched");
    }

    /** The handle without "@". */
    public static String screenName(Object user) {
        throw notPatched();
    }

    /** X's numeric user id, or 0. */
    public static long userId(Object user) {
        throw notPatched();
    }

    public static String displayName(Object user) {
        throw notPatched();
    }

    public static String bio(Object user) {
        throw notPatched();
    }

    /** True for the grey government or multilateral checkmark. */
    public static boolean isGovernment(Object user) {
        throw notPatched();
    }

    /** "parody", "commentary", "fan", or null without a label. */
    public static String label(Object user) {
        throw notPatched();
    }

    /** Accounts the user follows, or -1. */
    public static long following(Object user) {
        throw notPatched();
    }

    /** The description of the account's highlighted label, usually its affiliated organization, or null. */
    public static String affiliation(Object user) {
        throw notPatched();
    }

    /** The link entities of the bio, or null. Elements are X's UrlEntity. */
    public static List<?> bioLinks(Object user) {
        throw notPatched();
    }

    /** The destination of a UrlEntity, or null. */
    public static String expandedUrl(Object urlEntity) {
        throw notPatched();
    }

    /** ProfileUser: the self-written location, or null. */
    public static String profileLocation(Object profileUser) {
        throw notPatched();
    }

    /** ProfileUser: the website's destination, or null. */
    public static String website(Object profileUser) {
        throw notPatched();
    }

    /** ProfileUser: the number of posts, or -1. */
    public static long postCount(Object profileUser) {
        throw notPatched();
    }

    /** AvailablePost: the post's id, or 0. */
    public static long postId(Object availablePost) {
        throw notPatched();
    }

    /** AvailablePost: the id of the signed-in account viewing it, or 0. */
    public static long viewerId(Object availablePost) {
        throw notPatched();
    }

    /** CanonicalPost: the post's id, or 0. */
    public static long canonicalPostId(Object canonicalPost) {
        throw notPatched();
    }

    /** CanonicalPost: the language X detected for the text, or null. */
    public static String language(Object canonicalPost) {
        throw notPatched();
    }
}
