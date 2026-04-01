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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ProfileActivity extends AppCompatActivity {

    private final String[] ZODIAC_NAMES = {
        "Aries", "Taurus", "Gemini", "Cancer",
        "Leo", "Virgo", "Libra", "Scorpius",
        "Sagittarius", "Capricornus", "Aquarius", "Pisces"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvStats = findViewById(R.id.tv_stats);
        RecyclerView recyclerView = findViewById(R.id.rv_collection);
        findViewById(R.id.btn_back_profile).setOnClickListener(v -> finish());

        Set<String> collected = CollectionManager.getCollection(this);
        tvStats.setText("Collected: " + collected.size() + "/" + ZODIAC_NAMES.length);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(new CollectionAdapter(new ArrayList<>(Arrays.asList(ZODIAC_NAMES)), collected));
    }

    private static class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {
        private final List<String> allNames;
        private final Set<String> collectedSet;

        CollectionAdapter(List<String> all, Set<String> collected) {
            this.allNames = all;
            this.collectedSet = collected;
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
            
            boolean isCollected = collectedSet.contains(name);
            if (isCollected) {
                holder.ivIcon.setAlpha(1.0f);
                holder.tvStatus.setText("Collected");
                holder.tvStatus.setTextColor(Color.GREEN);
            } else {
                holder.ivIcon.setAlpha(0.2f);
                holder.tvStatus.setText("Locked");
                holder.tvStatus.setTextColor(Color.GRAY);
            }
        }

        @Override
        public int getItemCount() {
            return allNames.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvStatus;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.iv_card_icon);
                tvName = v.findViewById(R.id.tv_card_name);
                tvStatus = v.findViewById(R.id.tv_card_status);
            }
        }
    }
}
