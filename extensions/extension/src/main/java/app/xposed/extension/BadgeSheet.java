package app.xposed.extension;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The account sheet shown when a badge is tapped: X-Posed's account card, its quick filters and
 * the card as an image.
 */
final class BadgeSheet implements Lookup.Listener {
    /** How long the sheet says it is checking X before it stops waiting. */
    private static final long FETCH_TIMEOUT_MS = 10_000;
    /** How long Copy and Save show their check mark and message. */
    private static final long DONE_MS = 3500;
    /** Material's widest bottom sheet, for unfolded and large screens. */
    private static final int MAX_WIDTH_DP = 640;
    /** Handles the sheet asked X about in this session, so reopening it doesn't ask again. Main thread. */
    private static final Set<String> fetched = new HashSet<>();

    private final Context context;
    private final String handle;
    private final Ui ui;
    private final Dialog dialog;
    private final LinearLayout content;
    private final int widthPx;
    private AccountCard card;

    private boolean fetching;
    private TextView fetchStatus, refresh;
    private final Runnable fetchTimeout = () -> {
        fetching = false;
        updateFetchViews();
    };

    // The card image's state outlives renders; its views are rebuilt with the sheet.
    private boolean imageBusy, imageFailed;
    private CardImage.Action imageDone;
    private String imageMessage;
    private final Map<CardImage.Action, TextView> imagePills = new EnumMap<>(CardImage.Action.class);
    private TextView imageStatus;
    private final Runnable imageReset = () -> {
        imageDone = null;
        imageMessage = null;
        updateImageViews();
    };

    private BadgeSheet(Context context, String handle) {
        this.context = context;
        this.handle = handle;
        this.ui = new Ui(context);
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        // TalkBack announces it when the sheet opens.
        dialog.setTitle("Account details for @" + handle);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(20));
        int maxHeight = ui.dp(context.getResources().getConfiguration().screenHeightDp) * 9 / 10;
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                // Leave the top of the screen showing, as a sheet does, and scroll the rest.
                int limit = maxHeight;
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) limit = Math.min(limit, MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
            }
        };
        GradientDrawable sheet = new GradientDrawable();
        sheet.setColor(ui.background);
        float r = ui.dp(16);
        sheet.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        scroll.setBackground(sheet);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);
        // Keep the buttons clear of the gesture bar.
        int bottom = ui.dp(20);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });

        widthPx = Math.min(ui.dp(context.getResources().getConfiguration().screenWidthDp), ui.dp(MAX_WIDTH_DP));
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.4f);

        dialog.setOnDismissListener(d -> {
            Lookup.removeListener(this);
            content.removeCallbacks(fetchTimeout);
            content.removeCallbacks(imageReset);
        });
        Lookup.addListener(this);
    }

    static void show(Context context, String handle) {
        AccountInfo info = Lookup.peek(handle);
        if (info == null) return;
        BadgeSheet sheet = new BadgeSheet(context, handle);
        // X-Posed's card asks X for the account's history when it opens without it, once per account per session.
        if ((!info.details.fromAbout() || Observed.person(handle) == null) && fetched.add(handle)) sheet.fetch();
        sheet.render();
        sheet.dialog.show();
    }

    @Override
    public void onAccountUpdated(String updated) {
        if (!handle.equals(updated) || !dialog.isShowing()) return;
        fetching = false;
        content.removeCallbacks(fetchTimeout);
        render();
    }

    /** Asks X's "About this account" again. The sheet redraws when the answer is stored. */
    private void fetch() {
        fetching = true;
        Lookup.refresh(handle);
        // X may not answer, for example while signed out; stop saying it is checking.
        content.removeCallbacks(fetchTimeout);
        content.postDelayed(fetchTimeout, FETCH_TIMEOUT_MS);
        updateFetchViews();
    }

    private void render() {
        AccountInfo info = Lookup.peek(handle);
        if (info == null) return;
        content.removeAllViews();
        addGrabber();

        AccountCard.Person person = Observed.person(handle);
        long knownId = Users.idFor(handle);
        long userId = info.userId != 0 ? info.userId : person != null && person.userId != 0 ? person.userId : knownId;
        // Activity counts follow "Use profile details", as X-Posed's do.
        boolean counts = Settings.sources().profileDetails;
        Observed.Profile profile = counts && userId != 0 ? Observed.profile(userId) : null;
        card = AccountCard.of(handle, info, person, counts, profile != null ? profile.posts : -1, knownId);
        content.addView(AccountCardView.build(ui, card, true), Ui.matchWidth());

        fetchStatus = ui.text("Checking X for this account's details…", 13, false, ui.secondary);
        fetchStatus.setPadding(0, ui.dp(8), 0, 0);
        fetchStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(fetchStatus, Ui.matchWidth());

        View divider = ui.divider();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) divider.getLayoutParams();
        lp.topMargin = ui.dp(16);
        lp.bottomMargin = ui.dp(16);
        content.addView(divider);

        if (!Verdicts.isViewer(card.userId)) addQuickFilters(info, card.userId);
        addImageActions();
        addActions();
        updateFetchViews();
        updateImageViews();
    }

    private void addGrabber() {
        View grabber = new View(context);
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(ui.divider);
        bar.setCornerRadius(ui.dp(2));
        grabber.setBackground(bar);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = ui.dp(16);
        content.addView(grabber, lp);
    }

    /**
     * Hide or highlight the account's country or region or the account itself, and Always Show
     * for the account, as X-Posed's card does.
     */
    private void addQuickFilters(AccountInfo info, long userId) {
        FilterRules rules = Filters.rules();
        boolean highlight = rules.highlight;
        String start = highlight ? "Highlight " : "Hide ";
        String stop = highlight ? "Stop highlighting " : "Stop hiding ";
        String location = info.country(Settings.sources().deviceCountry);
        if (location != null) {
            boolean filtered = rules.locationKeys.contains(Countries.canonicalKey(location));
            TextView toggle = ui.pill(filtered ? stop + location : start + "posts from " + location, !filtered, v -> {
                Filters.edit(b -> {
                    if (filtered) b.removeLocation(location);
                    else b.addLocation(location);
                });
                render();
            });
            content.addView(wrapping(toggle), Ui.matchWidth());
        }
        if (userId != 0) {
            boolean filtered = rules.accounts.containsKey(userId);
            TextView toggle = ui.pill(filtered ? stop + "@" + handle : start + "@" + handle, false, v -> {
                Filters.edit(b -> {
                    if (filtered) b.removeAccount(userId);
                    else b.putAccount(userId, handle);
                });
                render();
            });
            addSpaced(wrapping(toggle));
        }
        boolean alwaysShown = rules.alwaysShow.contains(handle);
        TextView always = ui.pill(alwaysShown ? "Remove @" + handle + " from Always Show" : "Always show @" + handle, false, v -> {
            Filters.edit(b -> {
                if (alwaysShown) b.removeAlwaysShow(handle);
                else b.addAlwaysShow(handle);
            });
            render();
        });
        addSpaced(wrapping(always));
        String notice = alwaysShown ? "Always Show takes priority over your filters for this account."
                : rules.alwaysShowFollowing && Verdicts.followedByMe(handle)
                ? "You follow this account, so your setting to always show followed accounts takes priority over your filters."
                : null;
        if (notice != null) {
            TextView text = ui.text(notice, 13, false, ui.secondary);
            text.setPadding(0, ui.dp(8), 0, 0);
            text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            content.addView(text, Ui.matchWidth());
        }
    }

    /** Lets a long label wrap at large text sizes instead of being cut off. */
    private TextView wrapping(TextView pill) {
        pill.setSingleLine(false);
        pill.setMaxLines(3);
        pill.setPadding(pill.getPaddingLeft(), ui.dp(10), pill.getPaddingRight(), ui.dp(10));
        return pill;
    }

    private void addSpaced(View view) {
        LinearLayout.LayoutParams lp = Ui.matchWidth();
        lp.topMargin = ui.dp(12);
        content.addView(view, lp);
    }

    /** Copy, Save and Share the card as an image, as X-Posed's card offers. */
    private void addImageActions() {
        TextView heading = ui.text("Card image", 15, true, ui.text);
        heading.setAccessibilityHeading(true);
        heading.setPadding(0, ui.dp(20), 0, ui.dp(8));
        content.addView(heading);
        imagePills.clear();
        CardImage.Action[] actions = CardImage.Action.values();
        TextView[] pills = new TextView[actions.length];
        for (int i = 0; i < actions.length; i++) {
            CardImage.Action action = actions[i];
            TextView pill = ui.pill(action.label, false, v -> exportImage(action));
            pill.setText(imageLabel(action, pill));
            pill.setContentDescription(action.description);
            imagePills.put(action, pill);
            pills[i] = pill;
        }
        content.addView(buttonRow(pills), Ui.matchWidth());
        imageStatus = ui.text("", 13, false, ui.secondary);
        imageStatus.setPadding(0, ui.dp(8), 0, 0);
        imageStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(imageStatus, Ui.matchWidth());
    }

    private CharSequence imageLabel(CardImage.Action action, TextView pill) {
        boolean done = action == imageDone;
        return new SpannableStringBuilder(Ui.inlineIcon(done ? Icons.CHECK : action.icon, done ? Ui.ACCENT : ui.text,
                pill.getTextSize() * 1.2f)).append("\u00A0\u00A0").append(action.label);
    }

    private void exportImage(CardImage.Action action) {
        if (imageBusy || card == null) return;
        imageBusy = true;
        imageDone = null;
        imageFailed = false;
        imageMessage = "Creating card image…";
        content.removeCallbacks(imageReset);
        updateImageViews();
        CardImage.export(context, card, action, (message, failed) -> {
            imageBusy = false;
            imageFailed = failed;
            imageMessage = message;
            // The share sheet is its own confirmation.
            imageDone = failed || action == CardImage.Action.SHARE ? null : action;
            updateImageViews();
            if (!failed) content.postDelayed(imageReset, DONE_MS);
        });
    }

    private void updateImageViews() {
        for (Map.Entry<CardImage.Action, TextView> e : imagePills.entrySet()) {
            TextView pill = e.getValue();
            pill.setText(imageLabel(e.getKey(), pill));
            pill.setEnabled(!imageBusy);
            pill.setAlpha(imageBusy ? 0.6f : 1f);
        }
        if (imageStatus == null) return;
        imageStatus.setText(imageMessage);
        imageStatus.setTextColor(imageFailed ? ui.alert : ui.secondary);
        imageStatus.setVisibility(imageMessage != null ? View.VISIBLE : View.GONE);
    }

    private void addActions() {
        refresh = ui.pill("Refresh from X", false, v -> fetch());
        TextView settings = ui.pill("Settings", false, v -> {
            dialog.dismiss();
            SettingsScreen.show(context);
        });
        LinearLayout.LayoutParams lp = Ui.matchWidth();
        lp.topMargin = ui.dp(20);
        content.addView(buttonRow(refresh, settings), lp);
    }

    private void updateFetchViews() {
        if (fetchStatus != null) fetchStatus.setVisibility(fetching ? View.VISIBLE : View.GONE);
        if (refresh != null) {
            refresh.setText(fetching ? "Refreshing…" : "Refresh from X");
            refresh.setEnabled(!fetching);
        }
    }

    /** Buttons side by side in equal widths, or stacked when a label wouldn't fit, as with large text. */
    private LinearLayout buttonRow(TextView... buttons) {
        int gap = ui.dp(12);
        int available = widthPx - content.getPaddingLeft() - content.getPaddingRight();
        int widest = 0;
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (TextView b : buttons) {
            b.measure(unspecified, unspecified);
            widest = Math.max(widest, b.getMeasuredWidth());
        }
        boolean stack = widest * buttons.length + gap * (buttons.length - 1) > available;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = stack ? Ui.matchWidth() : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0 && stack) lp.topMargin = gap;
            else if (i > 0) lp.setMarginStart(gap);
            row.addView(buttons[i], lp);
        }
        return row;
    }
}
