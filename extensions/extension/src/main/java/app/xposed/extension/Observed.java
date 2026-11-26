package app.xposed.extension;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What X has already loaded and the filters need later: profile details that timeline users
 * lack, post languages and the signed-in account. Kept in memory only and never sent
 * anywhere, like X-Posed's profile cache.
 */
final class Observed {
    /** Details only a profile page loads. */
    static final class Profile {
        final String location, website;
        /** Posts written, or -1. */
        final long posts;

        Profile(String location, String website, long posts) {
            this.location = location;
            this.website = website;
            this.posts = posts;
        }
    }

    private static final Map<Long, Profile> profiles = lru(2000);
    private static final Map<Long, String> languages = lru(5000);
    private static volatile long viewer;

    private Observed() {
    }

    private static <K, V> Map<K, V> lru(int limit) {
        return new LinkedHashMap<K, V>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > limit;
            }
        };
    }

    /** Called when X creates a ProfileUser, on whichever thread loads it. */
    static void profileUser(Object user) {
        long id = ModelBridge.userId(user);
        if (id == 0) return;
        Profile profile = new Profile(ModelBridge.profileLocation(user), ModelBridge.website(user), ModelBridge.postCount(user));
        synchronized (profiles) {
            profiles.put(id, profile);
        }
    }

    static Profile profile(long userId) {
        synchronized (profiles) {
            return profiles.get(userId);
        }
    }

    /** Called when X creates a CanonicalPost, on whichever thread loads it. */
    static void canonicalPost(Object post) {
        long id = ModelBridge.canonicalPostId(post);
        String language = ModelBridge.language(post);
        if (id == 0 || language == null) return;
        synchronized (languages) {
            languages.put(id, language);
        }
    }

    /** The language X detected for a post, or null. */
    static String language(long postId) {
        if (postId == 0) return null;
        synchronized (languages) {
            return languages.get(postId);
        }
    }

    static void viewer(long userId) {
        if (userId != 0) viewer = userId;
    }

    /** The signed-in account, which no filter applies to; 0 until a post showed it. */
    static long viewer() {
        return viewer;
    }
}
