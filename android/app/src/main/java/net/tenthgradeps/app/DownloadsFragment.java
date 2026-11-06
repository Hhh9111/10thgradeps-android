package net.tenthgradeps.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Files saved on the device. They open without an internet connection (Premium ones within the offline grace period). */
public class DownloadsFragment extends Fragment implements MainActivity.Refreshable {
    private GridAdapter adapter;
    private TextView empty, info;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        android.content.Context ctx = requireContext();
        LinearLayout root = Ui.column(ctx);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.largeTitle(ctx, "التنزيلات"), Ui.lp(-1, -2));
        info = Ui.tv(ctx, "", 12, Ui.MUTED, false);
        root.addView(info, Ui.lpMargin(ctx, -2, -2, 20, 0, 20, 10));
        empty = Ui.tv(ctx, "لا توجد ملفات منزّلة.\nاضغط على سهم التنزيل بجانب أي ملف لتقرأه دون إنترنت.", 14, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, Ui.lpMargin(ctx, -1, -2, 20, 40, 20, 0));
        RecyclerView rv = new RecyclerView(ctx);
        rv.setLayoutManager(new GridLayoutManager(ctx, 2));
        adapter = new GridAdapter(null, new Runnable() { @Override public void run() { refreshScreen(); } });
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(ctx, 11), 0, Ui.dp(ctx, 11), Ui.dp(ctx, 110));
        root.addView(rv, Ui.lpWeight(-1, 0, 1f));
        refreshScreen();
        return root;
    }

    @Override public void refreshScreen() {
        if (adapter == null || !isAdded()) return;
        Store s = Store.get(requireContext());
        List<FileItem> list = s.downloads();
        adapter.set(list);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        info.setText(list.isEmpty() ? "" : list.size() + " ملف — " + Ui.formatBytes(s.usedBytes()));
    }
}
