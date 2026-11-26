package app.xposed.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filter verdicts per account. A verdict is computed from X's user object and cached until
 * that object, the account's profile details, X's report about it or the rules change, so
 * recomposing a post costs a few identity checks.
 */
final class Verdicts {
    private static final int LIMIT = 2000;

    private static final class Verdict {
        final Object source;
        final Observed.Profile profile;
        final FilterSubject subject;
        final FilterRules rules;
        final AccountInfo info;
        final int reasons;

        Verdict(Object source, Observed.Profile profile, FilterSubject subject, FilterRules rules, AccountInfo info, int reasons) {
            this.source = source;
            this.profile = profile;
            this.subject = subject;
            this.rules = rules;
            this.info = info;
            this.reasons = reasons;
        }
    }

    private static final Map<String, Verdict> byHandle = new LinkedHashMap<String, Verdict>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Verdict> eldest) {
            return size() > LIMIT;
        }
    };

    private Verdicts() {
    }

    /** The signed-in account is exempt from every filter. */
    static boolean isViewer(long userId) {
        return userId != 0 && userId == Observed.viewer();
    }

    /** The account filters matching a user X is showing, scheduling a lookup when its location is unknown. */
    static int forUser(Object user, String handle, long userId) {
        FilterRules rules = Filters.rules();
        if (rules.isEmpty() || isViewer(userId)) return 0;
        AccountInfo info = Lookup.get(handle);
        Observed.Profile profile = userId != 0 ? Observed.profile(userId) : null;
        Verdict e;
        synchronized (byHandle) {
            e = byHandle.get(handle);
        }
        boolean sameAccount = e != null && e.source == user && e.profile == profile;
        if (sameAccount && e.rules == rules && e.info == info) return e.reasons;
        FilterSubject subject = sameAccount ? e.subject : subjectOf(user, handle, userId, profile);
        int reasons = FilterMatcher.account(rules, subject, info);
        synchronized (byHandle) {
            byHandle.put(handle, new Verdict(user, profile, subject, rules, info, reasons));
        }
        return reasons;
    }

    /**
     * The filters a post in a client event matches, from what was shown of its author. Never
     * schedules lookups: events arrive in bulk off the main thread.
     */
    static int forEvent(long authorId, long postId) {
        FilterRules rules = Filters.rules();
        if (rules.isEmpty() || isViewer(authorId)) return 0;
        String handle = Users.handleFor(authorId);
        if (handle == null) return 0;
        AccountInfo info = Lookup.peek(handle);
        Verdict e;
        synchronized (byHandle) {
            e = byHandle.get(handle);
        }
        int reasons = e != null && e.rules == rules && e.info == info
                ? e.reasons
                : FilterMatcher.account(rules, e != null ? e.subject : FilterSubject.minimal(handle, authorId), info);
        return reasons | FilterMatcher.language(rules, Observed.language(postId));
    }

    private static FilterSubject subjectOf(Object user, String handle, long userId, Observed.Profile profile) {
        List<String> website = profile != null && profile.website != null
                ? Collections.singletonList(profile.website) : Collections.emptyList();
        return new FilterSubject(handle, userId, ModelBridge.displayName(user), ModelBridge.bio(user),
                profile != null ? profile.location : null, website, links(ModelBridge.bioLinks(user)),
                ModelBridge.label(user), ModelBridge.isGovernment(user), ModelBridge.affiliation(user),
                ModelBridge.following(user), profile != null ? profile.posts : -1);
    }

    private static List<String> links(List<?> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        List<String> urls = new ArrayList<>(entities.size());
        for (Object entity : entities) {
            String url = ModelBridge.expandedUrl(entity);
            if (url != null) urls.add(url);
        }
        return urls;
    }
}
