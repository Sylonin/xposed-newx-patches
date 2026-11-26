package app.xposed.extension;

import android.content.SharedPreferences;

import java.util.Set;

/**
 * The saved filters, like X-Posed's Blocking settings. Readers get the current immutable
 * {@link FilterRules} without locking; edits build a new snapshot, save it as JSON and
 * recompose every hooked post.
 */
final class Filters {
    enum Action {NONE, HIDE, HIGHLIGHT}

    interface Edit {
        void apply(FilterRules.Builder rules);
    }

    private static final String KEY = "filters";
    /** Countries and regions as saved before the other filter types existed. */
    private static final String LEGACY_LOCATIONS = "filter_locations";

    private static volatile FilterRules rules;

    private Filters() {
    }

    /** The current filters; empty until settings are initialized. */
    static FilterRules rules() {
        FilterRules r = rules;
        return r != null ? r : load();
    }

    /** Applies an edit, saves the result and returns it. */
    static synchronized FilterRules edit(Edit edit) {
        FilterRules.Builder builder = rules().toBuilder();
        edit.apply(builder);
        return save(builder.build());
    }

    /** Puts back an earlier snapshot, for undo. */
    static synchronized void restore(FilterRules previous) {
        save(previous);
    }

    private static FilterRules save(FilterRules next) {
        rules = next;
        SharedPreferences prefs = Settings.prefs();
        if (prefs != null) prefs.edit().putString(KEY, next.toJson()).apply();
        States.changedAll();
        return next;
    }

    /** What to do with a post that matched these reasons. */
    static Action action(int reasons) {
        if (reasons == 0) return Action.NONE;
        return Settings.highlightMode() ? Action.HIGHLIGHT : Action.HIDE;
    }

    private static synchronized FilterRules load() {
        if (rules != null) return rules;
        SharedPreferences prefs = Settings.prefs();
        if (prefs == null) return FilterRules.EMPTY;
        String json = prefs.getString(KEY, null);
        FilterRules r;
        if (json != null) {
            r = FilterRules.fromJson(json);
        } else {
            FilterRules.Builder builder = new FilterRules.Builder();
            Set<String> legacy = prefs.getStringSet(LEGACY_LOCATIONS, null);
            if (legacy != null) {
                for (String location : legacy) builder.addLocation(location);
            }
            r = builder.build();
            if (legacy != null) prefs.edit().putString(KEY, r.toJson()).remove(LEGACY_LOCATIONS).apply();
        }
        rules = r;
        return r;
    }
}
