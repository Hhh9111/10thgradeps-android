package net.tenthgradeps.app;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Two-column grid of file cards, optionally with one full-width header view as the first item. */
final class GridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0, TYPE_CARD = 1;
    private final View header;
    private final Runnable onChanged;
    private List<FileItem> items = new ArrayList<>();

    GridAdapter(View header, Runnable onChanged) {
        this.header = header;
        this.onChanged = onChanged;
    }

    void set(List<FileItem> list) { items = list; notifyDataSetChanged(); }

    private int offset() { return header == null ? 0 : 1; }

    GridLayoutManager.SpanSizeLookup spanLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return header != null && position == 0 ? 2 : 1; }
        };
    }

    @Override public int getItemViewType(int position) { return header != null && position == 0 ? TYPE_HEADER : TYPE_CARD; }

    @Override public int getItemCount() { return items.size() + offset(); }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            if (header.getParent() instanceof ViewGroup) ((ViewGroup) header.getParent()).removeView(header);
            header.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(header) { };
        }
        android.content.Context c = parent.getContext();
        FileCardView v = new FileCardView(c);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        int m = Ui.dp(c, 5);
        lp.setMargins(m, m, m, m);
        v.setLayoutParams(lp);
        return new RecyclerView.ViewHolder(v) { };
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        if (getItemViewType(position) == TYPE_CARD) ((FileCardView) h.itemView).bind(items.get(position - offset()), onChanged);
    }
}
