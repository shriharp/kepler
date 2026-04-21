package com.prj.keplerv0;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvStats = findViewById(R.id.tv_stats);
        RecyclerView recyclerView = findViewById(R.id.rv_collection);
        findViewById(R.id.btn_back_profile).setOnClickListener(v -> finish());

        // Derive names from the live card library — new cards appear automatically
        List<Card> library = GameEngine.getLibrary();
        List<String> allNames = new ArrayList<>();
        for (Card c : library) allNames.add(c.name);

        Set<String> collected = CollectionManager.getCollection(this);
        Set<String> partial   = CollectionManager.getPartialCollection(this);

        tvStats.setText("Collected: " + collected.size() + "/" + allNames.size()
                + (partial.isEmpty() ? "" : "  ·  Partial: " + partial.size()));

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(new CollectionAdapter(allNames, collected, partial));
    }

    private static class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {
        private final List<String> allNames;
        private final Set<String>  collectedSet;
        private final Set<String>  partialSet;

        CollectionAdapter(List<String> all, Set<String> collected, Set<String> partial) {
            this.allNames     = all;
            this.collectedSet = collected;
            this.partialSet   = partial;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_constellation_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String name = allNames.get(position);
            holder.tvName.setText(name);

            if (collectedSet.contains(name)) {
                holder.ivIcon.setAlpha(1.0f);
                holder.tvStatus.setText("Collected");
                holder.tvStatus.setTextColor(Color.GREEN);
            } else if (partialSet.contains(name)) {
                holder.ivIcon.setAlpha(0.6f);
                holder.tvStatus.setText("★½ Partial");
                holder.tvStatus.setTextColor(0xFFFFD700); // gold
            } else {
                holder.ivIcon.setAlpha(0.2f);
                holder.tvStatus.setText("Locked");
                holder.tvStatus.setTextColor(Color.GRAY);
            }
        }

        @Override
        public int getItemCount() { return allNames.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvStatus;
            ViewHolder(View v) {
                super(v);
                ivIcon   = v.findViewById(R.id.iv_card_icon);
                tvName   = v.findViewById(R.id.tv_card_name);
                tvStatus = v.findViewById(R.id.tv_card_status);
            }
        }
    }
}
