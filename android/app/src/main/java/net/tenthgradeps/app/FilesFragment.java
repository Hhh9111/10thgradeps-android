package net.tenthgradeps.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The website-style home: header, search, subject chips (with book covers), type filter and a two-column grid of files.
 * The same screen, with premium = true, is the Premium tab (only usable with a subscription confirmed by the server).
 */
public class FilesFragment extends Fragment implements MainActivity.Refreshable {
    static FilesFragment newInstance(boolean premium) {
        FilesFragment f = new FilesFragment();
        Bundle b = new Bundle();
        b.putBoolean("premium", premium);
        f.setArguments(b);
        return f;
    }

    private boolean premium;
    private String subject = "", type = "", query = "";
    private LinearLayout header, chips, locked, filterRow;
    private TextView countTv, typePill, emptyTv, subTitle;
    private EditText search;
    private HorizontalScrollView chipScroll;
    private SwipeRefreshLayout swipe;
    private GridAdapter adapter;
    private boolean loadedRemote;
    private final Map<String, String> hits = new HashMap<>();
    private String hitsQuery = "";
    private final Runnable searchTask = new Runnable() { @Override public void run() { reload(); remoteSearch(); } };

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        premium = getArguments() != null && getArguments().getBoolean("premium");
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        android.content.Context ctx = requireContext();
        header = buildHeader(ctx);
        swipe = new SwipeRefreshLayout(ctx);
        swipe.setBackgroundColor(Ui.BG);
        swipe.setColorSchemeColors(Ui.accent());
        swipe.setProgressBackgroundColorSchemeColor(Ui.SURFACE2);
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() { @Override public void onRefresh() { fetchRemote(); } });
        RecyclerView rv = new RecyclerView(ctx);
        GridLayoutManager glm = new GridLayoutManager(ctx, 2);
        adapter = new GridAdapter(header, null);
        glm.setSpanSizeLookup(adapter.spanLookup());
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(ctx, 11), 0, Ui.dp(ctx, 11), Ui.dp(ctx, 110));
        swipe.addView(rv, new ViewGroup.LayoutParams(-1, -1));
        refreshScreen();
        return swipe;
    }

    private LinearLayout buildHeader(final android.content.Context c) {
        LinearLayout h = Ui.column(c);
        h.setPadding(Ui.dp(c, 5), Ui.dp(c, 14), Ui.dp(c, 5), Ui.dp(c, 4));

        LinearLayout top = Ui.row(c);
        if (!premium) {
            ImageView logo = new ImageView(c);
            logo.setImageResource(R.drawable.logo);
            logo.setBackground(Ui.round(c, Ui.SURFACE, 23));
            logo.setClipToOutline(true);
            top.addView(logo, Ui.lpMargin(c, Ui.dp(c, 46), Ui.dp(c, 46), 0, 0, 12, 0));
        }
        LinearLayout names = Ui.column(c);
        TextView title = Ui.tv(c, premium ? "Premium" : "10thgradeps", 26, Ui.TEXT, true);
        Ui.font(title, 2);
        title.setTextDirection(View.TEXT_DIRECTION_LTR);
        title.setGravity(Gravity.START);
        names.addView(title);
        subTitle = Ui.tv(c, premium ? "ملفاتك المحمية" : "لطلاب الصف العاشر في فلسطين", 13, Ui.MUTED, false);
        names.addView(subTitle);
        top.addView(names, Ui.lpWeight(0, -2, 1f));
        if (!premium) {
            ImageView gear = Ui.iconButton(c, R.drawable.ic_settings, Ui.TEXT);
            gear.setBackground(Ui.round(c, Ui.SURFACE, 21));
            gear.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
            gear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(c, SettingsActivity.class)); }
            });
            Ui.pressable(gear);
            top.addView(gear);
        }
        h.addView(top, Ui.lp(-1, -2));

        search = Ui.input(c, "ابحث باسم الملف أو المادة…", InputType.TYPE_CLASS_TEXT);
        search.setBackground(Ui.round(c, Ui.SURFACE, 14));
        search.setCompoundDrawablePadding(Ui.dp(c, 8));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.tintedDrawable(c, R.drawable.ic_search, Ui.MUTED), null, null, null);
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                query = e.toString();
                Async.MAIN.removeCallbacks(searchTask);
                Async.MAIN.postDelayed(searchTask, 300);
            }
        });
        h.addView(search, Ui.lpMargin(c, -1, -2, 0, 14, 0, 0));

        chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = Ui.row(c);
        chipScroll.addView(chips);
        h.addView(chipScroll, Ui.lpMargin(c, -1, -2, 0, 10, 0, 0));

        filterRow = Ui.row(c);
        countTv = Ui.tv(c, "", 17, Ui.TEXT, true);
        filterRow.addView(countTv, Ui.lpWeight(0, -2, 1f));
        typePill = Ui.tv(c, "كل الأنواع ▾", 12, Ui.TEXT, true);
        typePill.setPadding(Ui.dp(c, 13), Ui.dp(c, 7), Ui.dp(c, 13), Ui.dp(c, 7));
        typePill.setBackground(Ui.round(c, Ui.SURFACE, 16));
        typePill.setClickable(true);
        Ui.pressable(typePill);
        typePill.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pickType(); } });
        filterRow.addView(typePill);
        h.addView(filterRow, Ui.lpMargin(c, -1, -2, 2, 16, 2, 6));

        emptyTv = Ui.tv(c, "لا توجد نتائج مطابقة.", 14, Ui.MUTED, false);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setVisibility(View.GONE);
        h.addView(emptyTv, Ui.lpMargin(c, -1, -2, 0, 30, 0, 30));

        locked = Ui.column(c);
        locked.setGravity(Gravity.CENTER_HORIZONTAL);
        locked.setPadding(Ui.dp(c, 20), Ui.dp(c, 50), Ui.dp(c, 20), Ui.dp(c, 20));
        locked.setVisibility(View.GONE);
        h.addView(locked, Ui.lp(-1, -2));
        return h;
    }

    @Override public void refreshScreen() {
        if (!isAdded() || header == null) return;
        if (premium && Session.premiumUsable() && !loadedRemote) { loadedRemote = true; fetchRemote(); }
        buildChips();
        reload();
    }

    private void buildChips() {
        final android.content.Context c = requireContext();
        Catalog cat = Catalog.get(c);
        chips.removeAllViews();
        chips.addView(subjectChip(c, "الكل", "", null), Ui.lpMargin(c, -2, -2, 0, 0, 8, 0));
        for (Catalog.Subject s : cat.subjects) {
            if (cat.countFor(s.name, premium) == 0) continue;
            chips.addView(subjectChip(c, s.name, s.name, premium ? null : cat.coverFor(s.name)), Ui.lpMargin(c, -2, -2, 0, 0, 8, 0));
        }
    }

    private View subjectChip(android.content.Context c, String label, final String value, String coverPath) {
        boolean on = subject.equals(value);
        LinearLayout cl = Ui.row(c);
        cl.setPadding(Ui.dp(c, coverPath == null ? 14 : 6), Ui.dp(c, 5), Ui.dp(c, 14), Ui.dp(c, 5));
        cl.setBackground(Ui.round(c, on ? Ui.accent() : Ui.SURFACE, 22));
        if (coverPath != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.round(c, Ui.SURFACE2, 5));
            iv.setClipToOutline(true);
            ImageLoader.load(c, iv, coverPath, Ui.dp(c, 36), Ui.dp(c, 48));
            cl.addView(iv, Ui.lpMargin(c, Ui.dp(c, 24), Ui.dp(c, 32), 0, 0, 8, 0));
        }
        cl.addView(Ui.tv(c, label, 13, on ? Ui.ON_ACCENT : Ui.TEXT, true));
        cl.setClickable(true);
        Ui.pressable(cl);
        cl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { subject = value; buildChips(); reload(); }
        });
        return cl;
    }

    private void pickType() {
        final String[] opts = new String[Catalog.TYPES.length + 1];
        opts[0] = "كل الأنواع";
        System.arraycopy(Catalog.TYPES, 0, opts, 1, Catalog.TYPES.length);
        Ui.show(new AlertDialog.Builder(requireContext()).setTitle("التصنيف حسب النوع")
                .setItems(opts, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        type = which == 0 ? "" : opts[which];
                        typePill.setText((which == 0 ? "كل الأنواع" : opts[which]) + " ▾");
                        reload();
                    }
                }));
    }

    private void showLocked(boolean on) {
        final android.content.Context c = requireContext();
        locked.removeAllViews();
        locked.setVisibility(on ? View.VISIBLE : View.GONE);
        search.setVisibility(on ? View.GONE : View.VISIBLE);
        chipScroll.setVisibility(on ? View.GONE : View.VISIBLE);
        filterRow.setVisibility(on ? View.GONE : View.VISIBLE);
        if (!on) return;
        emptyTv.setVisibility(View.GONE);
        final boolean in = Session.loggedIn();
        locked.addView(Ui.icon(c, R.drawable.ic_lock, Ui.TEXT, 44));
        locked.addView(Ui.tv(c, in ? "حسابك لا يملك اشتراك Premium مفعّلًا" : "ملفات Premium تحتاج حسابًا مشتركًا", 17, Ui.TEXT, true), Ui.lpMargin(c, -2, -2, 0, 16, 0, 6));
        String m = Session.premiumMessage();
        TextView note = Ui.tv(c, in && !m.isEmpty() ? m : "يتحقق الخادم من اشتراكك قبل فتح أي ملف محمي.", 13, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        locked.addView(note);
        TextView go = Ui.button(c, in ? "الاشتراك Premium" : "تسجيل الدخول", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(c, in ? SubscriptionActivity.class : LoginActivity.class)); }
        });
        locked.addView(go, Ui.lpMargin(c, -2, -2, 0, 20, 0, 0));
    }

    private void reload() {
        if (!isAdded() || adapter == null) return;
        Catalog cat = Catalog.get(requireContext());
        if (premium && !Session.premiumUsable()) {
            showLocked(true);
            adapter.set(new ArrayList<FileItem>());
            return;
        }
        showLocked(false);
        List<FileItem> base = premium ? cat.visiblePremium() : cat.visibleFree();
        List<FileItem> out = Catalog.filter(base, subject, type, query);
        if (premium && !hits.isEmpty() && query.trim().equals(hitsQuery)) {
            List<FileItem> extra = new ArrayList<>();
            for (FileItem f : Catalog.filter(base, subject, type, "")) {
                String pages = hits.get(f.id);
                if (pages == null || out.contains(f)) continue;
                FileItem copy = FileItem.fromJson(f.toJson());
                copy.desc = "ورد داخل الملف — صفحة " + pages;
                extra.add(copy);
            }
            out.addAll(extra);
        }
        adapter.set(out);
        countTv.setText((premium ? "ملفات Premium" : "كل الملفات") + " (" + out.size() + ")");
        emptyTv.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Content search inside Premium PDFs goes through the site's premium-search function (server-side). */
    private void remoteSearch() {
        final String q = query.trim();
        if (!premium || q.length() < 3 || !Session.premiumUsable() || !Net.online(requireContext())) return;
        Async.run(new Async.Work<JSONArray>() {
            @Override public JSONArray run() throws Exception { return Api.premiumSearch(q); }
        }, new Async.Done<JSONArray>() {
            @Override public void done(JSONArray r, Exception e) {
                if (!isAdded() || e != null || !q.equals(query.trim())) return;
                hits.clear();
                hitsQuery = q;
                for (int i = 0; i < r.length(); i++) {
                    JSONObject o = r.optJSONObject(i);
                    if (o == null) continue;
                    JSONArray pg = o.optJSONArray("pages");
                    StringBuilder sb = new StringBuilder();
                    if (pg != null) for (int k = 0; k < pg.length() && k < 6; k++) { if (sb.length() > 0) sb.append("، "); sb.append(pg.optInt(k)); }
                    hits.put(o.optString("id"), sb.toString());
                }
                reload();
            }
        });
    }

    private void fetchRemote() {
        if (!isAdded()) return;
        final android.content.Context c = requireContext().getApplicationContext();
        Async.run(new Async.Work<Boolean>() {
            @Override public Boolean run() {
                try { Api.loadStatus(c); } catch (Exception ignored) { }
                try { Api.loadSiteFiles(c); } catch (Exception ignored) { }
                if (Session.premiumUsable()) try { Api.loadPremiumList(c); } catch (Exception ignored) { }
                return Boolean.TRUE;
            }
        }, new Async.Done<Boolean>() {
            @Override public void done(Boolean r, Exception e) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                buildChips();
                reload();
            }
        });
    }

    @Override public void onDestroyView() {
        Async.MAIN.removeCallbacks(searchTask);
        super.onDestroyView();
    }
}
