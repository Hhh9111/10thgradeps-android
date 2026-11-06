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

public class FavoritesFragment extends Fragment implements MainActivity.Refreshable {
    private GridAdapter adapter;
    private TextView empty, title;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        android.content.Context ctx = requireContext();
        LinearLayout root = Ui.column(ctx);
        root.setBackgroundColor(Ui.BG);
        title = Ui.largeTitle(ctx, "المفضلة");
        root.addView(title, Ui.lp(-1, -2));
        empty = Ui.tv(ctx, "لم تضف أي ملف إلى المفضلة بعد.\nاضغط على النجمة بجانب أي ملف.", 14, Ui.MUTED, false);
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
        List<FileItem> list = Store.get(requireContext()).favorites();
        adapter.set(list);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
