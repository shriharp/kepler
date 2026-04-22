package com.prj.keplerv0;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvEpBalance;
    private TextView tvStats;
    private RecyclerView recyclerView;
    private CollectionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvStats = findViewById(R.id.tv_stats);
        tvEpBalance = findViewById(R.id.tv_ep_balance);
        recyclerView = findViewById(R.id.rv_collection);
        findViewById(R.id.btn_back_profile).setOnClickListener(v -> finish());

        refreshCollection();
    }

    private void refreshCollection() {
        int ep = PersistenceManager.getInstance(this).getEnergyPoints();
        tvEpBalance.setText("EP: " + ep);

        List<Card> library = GameEngine.getLibrary();
        List<Card> allCards = new ArrayList<>(library);

        Set<String> collected = CollectionManager.getCollection(this);
        Set<String> partial   = CollectionManager.getPartialCollection(this);

        tvStats.setText("Collected: " + collected.size() + "/" + allCards.size()
                + (partial.isEmpty() ? "" : "  ·  Partial: " + partial.size()));

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new CollectionAdapter(allCards, collected, partial);
        recyclerView.setAdapter(adapter);
    }

    private int getCardRarityCost(Card c) {
        int stats = c.attack + c.defense;
        if (stats > 15) return 600; // Mythic
        if (stats >= 14) return 250; // Rare
        return 100; // Common
    }

    private void promptPurchaseCard(Card c) {
        int cost = getCardRarityCost(c);
        int currentEp = PersistenceManager.getInstance(this).getEnergyPoints();

        new AlertDialog.Builder(this)
                .setTitle("Unlock " + c.name)
                .setMessage("Purchase this card for " + cost + " EP?\n\nYou have " + currentEp + " EP.")
                .setPositiveButton("Buy", (dialog, which) -> {
                    if (PersistenceManager.getInstance(ProfileActivity.this).spendEnergyPoints(cost)) {
                        PersistenceManager.getInstance(ProfileActivity.this).unlockCardFully(c.name, "PURCHASE");
                        Toast.makeText(this, "Unlocked " + c.name + "!", Toast.LENGTH_SHORT).show();
                        refreshCollection();
                    } else {
                        Toast.makeText(this, "Not enough EP!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {
        private final List<Card> allCards;
        private final Set<String>  collectedSet;
        private final Set<String>  partialSet;

        CollectionAdapter(List<Card> all, Set<String> collected, Set<String> partial) {
            this.allCards     = all;
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
            Card c = allCards.get(position);
            String name = c.name;
            holder.tvName.setText(name);

            if (collectedSet.contains(name)) {
                holder.ivIcon.setAlpha(1.0f);
                holder.tvStatus.setText("Collected");
                holder.tvStatus.setTextColor(Color.GREEN);
                holder.itemView.setOnClickListener(null);
            } else if (partialSet.contains(name)) {
                holder.ivIcon.setAlpha(0.6f);
                holder.tvStatus.setText("★½ Partial");
                holder.tvStatus.setTextColor(0xFFFFD700); // gold
                holder.itemView.setOnClickListener(v -> promptPurchaseCard(c));
            } else {
                holder.ivIcon.setAlpha(0.2f);
                holder.tvStatus.setText("Locked (" + getCardRarityCost(c) + " EP)");
                holder.tvStatus.setTextColor(Color.GRAY);
                holder.itemView.setOnClickListener(v -> promptPurchaseCard(c));
            }
        }

        @Override
        public int getItemCount() { return allCards.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
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
