package com.prj.keplerv0;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeckSelectionActivity extends AppCompatActivity {

    private RecyclerView rvDeck, rvShop;
    private Button btnTabCollection, btnTabShop, btnConfirm;
    private TextView tvEpBalance;
    private final List<String> selectedCards = new ArrayList<>();
    private DeckAdapter deckAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_selection);

        rvDeck         = findViewById(R.id.rv_deck_selection);
        rvShop         = findViewById(R.id.rv_shop);
        btnTabCollection = findViewById(R.id.btn_tab_collection);
        btnTabShop     = findViewById(R.id.btn_tab_shop);
        btnConfirm     = findViewById(R.id.btn_confirm_deck);
        tvEpBalance    = findViewById(R.id.tv_ep_balance);

        rvDeck.setLayoutManager(new LinearLayoutManager(this));
        rvShop.setLayoutManager(new LinearLayoutManager(this));

        refreshEpBalance();
        setupCollectionTab();
        setupShopTab();

        btnTabCollection.setOnClickListener(v -> showCollectionTab());
        btnTabShop.setOnClickListener(v -> showShopTab());

        btnConfirm.setOnClickListener(v -> {
            Intent intent = new Intent(DeckSelectionActivity.this, GameActivity.class);
            intent.putStringArrayListExtra("selected_cards", new ArrayList<>(selectedCards));
            if (getIntent().hasExtra("is_multiplayer")) {
                intent.putExtra("is_multiplayer", getIntent().getBooleanExtra("is_multiplayer", false));
                intent.putExtra("is_host",        getIntent().getBooleanExtra("is_host", false));
                intent.putExtra("host_address",   getIntent().getStringExtra("host_address"));
            }
            startActivity(intent);
            finish();
        });
    }

    // ─── Tab switching ────────────────────────────────────────────────────────

    private void showCollectionTab() {
        rvDeck.setVisibility(View.VISIBLE);
        rvShop.setVisibility(View.GONE);
        btnTabCollection.setAlpha(1f);
        btnTabShop.setAlpha(0.5f);
    }

    private void showShopTab() {
        rvDeck.setVisibility(View.GONE);
        rvShop.setVisibility(View.VISIBLE);
        btnTabCollection.setAlpha(0.5f);
        btnTabShop.setAlpha(1f);
        // Refresh shop in case a purchase was just made
        setupShopTab();
        refreshEpBalance();
    }

    // ─── Collection tab ───────────────────────────────────────────────────────

    private void setupCollectionTab() {
        Set<String> collected = CollectionManager.getCollection(this);
        Set<String> partial   = CollectionManager.getPartialCollection(this);
        List<Card>  allCards  = GameEngine.getLibrary();

        List<Card> fullCards = new ArrayList<>();
        for (Card c : allCards) {
            if (collected.contains(c.name)) fullCards.add(c);
        }

        List<Card> partialCards = new ArrayList<>();
        for (String name : partial) {
            Card weak = GameEngine.getWeakenedCard(name);
            if (weak != null) partialCards.add(weak);
        }

        if (fullCards.isEmpty() && partialCards.isEmpty()) {
            Toast.makeText(this,
                    "No cards yet! Complete JoinDots puzzles or buy cards in the Shop.",
                    Toast.LENGTH_LONG).show();
            showShopTab();
            return;
        }

        // Flat list: full cards → null sentinel (header) → partial cards
        List<Card> rows = new ArrayList<>(fullCards);
        if (!partialCards.isEmpty()) {
            rows.add(null);
            rows.addAll(partialCards);
        }

        deckAdapter = new DeckAdapter(rows, selectedCards, () -> {
            btnConfirm.setText("Confirm Deck (" + selectedCards.size() + "/2)");
            btnConfirm.setEnabled(selectedCards.size() == 2);
        });
        rvDeck.setAdapter(deckAdapter);
    }

    // ─── Shop tab ─────────────────────────────────────────────────────────────

    private void setupShopTab() {
        List<Card>  allCards  = GameEngine.getLibrary();
        Set<String> collected = CollectionManager.getCollection(this);

        // Show cards not yet fully unlocked — purchasable
        List<Card> shopCards = new ArrayList<>();
        for (Card c : allCards) {
            if (!collected.contains(c.name)) shopCards.add(c);
        }

        ShopAdapter shopAdapter = new ShopAdapter(shopCards, card -> {
            int cost = CardShopManager.getCost(card.name);
            CardShopManager.BuyResult result = CardShopManager.buyCard(this, card.name);
            switch (result) {
                case SUCCESS:
                    Toast.makeText(this,
                            card.name + " purchased for " + cost + " EP!", Toast.LENGTH_SHORT).show();
                    refreshEpBalance();
                    setupCollectionTab();  // Refresh collection
                    showShopTab();         // Re-render shop to remove bought card
                    break;
                case INSUFFICIENT_EP:
                    Toast.makeText(this,
                            "Not enough EP! Need " + cost + " EP.", Toast.LENGTH_SHORT).show();
                    break;
                case ALREADY_OWNED:
                    Toast.makeText(this, "You already own " + card.name + "!", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
        rvShop.setAdapter(shopAdapter);
    }

    private void refreshEpBalance() {
        int ep = PersistenceManager.getInstance(this).getEnergyPoints();
        tvEpBalance.setText("⚡ " + ep + " EP");
    }

    // ─── Collection Adapter ───────────────────────────────────────────────────

    private static class DeckAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface OnSelectionChangedListener { void onSelectionChanged(); }

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_CARD   = 1;

        private final List<Card> rows;
        private final List<String> selectedCards;
        private final OnSelectionChangedListener listener;

        DeckAdapter(List<Card> rows, List<String> selectedCards, OnSelectionChangedListener listener) {
            this.rows          = rows;
            this.selectedCards = selectedCards;
            this.listener      = listener;
        }

        @Override public int getItemViewType(int position) {
            return rows.get(position) == null ? TYPE_HEADER : TYPE_CARD;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView tv = new TextView(parent.getContext());
                tv.setText("⭐ Partial Unlocks  (half-strength — complete JoinDots to upgrade)");
                tv.setTextSize(13f);
                tv.setTextColor(0xFFFFD700);
                tv.setPadding(32, 24, 32, 8);
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerView.ViewHolder(tv) {};
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_constellation_card, parent, false);
            return new CardViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_HEADER) return;
            Card c = rows.get(position);
            CardViewHolder cvh = (CardViewHolder) holder;
            cvh.tvName.setText(c.name);
            cvh.tvStatus.setText("ATK: " + c.attack + " | DEF: " + c.defense);
            boolean isPartial = c.name.startsWith("★½ ");
            cvh.tvName.setAlpha(isPartial ? 0.65f : 1f);
            cvh.tvStatus.setAlpha(isPartial ? 0.65f : 1f);
            cvh.itemView.setBackgroundColor(selectedCards.contains(c.name) ? Color.DKGRAY : Color.TRANSPARENT);
            cvh.itemView.setOnClickListener(v -> {
                if (selectedCards.contains(c.name)) {
                    selectedCards.remove(c.name);
                } else if (selectedCards.size() < 2) {
                    selectedCards.add(c.name);
                } else {
                    Toast.makeText(v.getContext(), "You can only select 2 cards!", Toast.LENGTH_SHORT).show();
                    return;
                }
                notifyItemChanged(position);
                listener.onSelectionChanged();
            });
        }

        @Override public int getItemCount() { return rows.size(); }

        static class CardViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus;
            CardViewHolder(View v) {
                super(v);
                tvName   = v.findViewById(R.id.tv_card_name);
                tvStatus = v.findViewById(R.id.tv_card_status);
            }
        }
    }

    // ─── Shop Adapter ─────────────────────────────────────────────────────────

    private static class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ShopViewHolder> {
        interface OnBuyListener { void onBuy(Card card); }

        private final List<Card> cards;
        private final OnBuyListener listener;

        ShopAdapter(List<Card> cards, OnBuyListener listener) {
            this.cards    = cards;
            this.listener = listener;
        }

        @NonNull @Override
        public ShopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shop_card, parent, false);
            return new ShopViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ShopViewHolder holder, int position) {
            Card c = cards.get(position);
            CardShopManager.Rarity rarity = CardShopManager.getRarity(c.name);
            int cost = CardShopManager.getCost(c.name);

            holder.tvName.setText(c.name);
            holder.tvStats.setText("ATK: " + c.attack + " | DEF: " + c.defense);
            holder.tvRarity.setText(rarity.name());
            holder.tvCost.setText(cost + " EP");

            // Color-code rarity
            int rarityColor;
            switch (rarity) {
                case RARE:   rarityColor = 0xFFAA88FF; break;  // Purple
                case MYTHIC: rarityColor = 0xFFFFAA00; break;  // Orange
                default:     rarityColor = 0xFF88CCFF; break;  // Blue
            }
            holder.tvRarity.setTextColor(rarityColor);

            holder.btnBuy.setOnClickListener(v -> listener.onBuy(c));
        }

        @Override public int getItemCount() { return cards.size(); }

        static class ShopViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStats, tvRarity, tvCost;
            Button btnBuy;
            ShopViewHolder(View v) {
                super(v);
                tvName   = v.findViewById(R.id.tv_shop_card_name);
                tvStats  = v.findViewById(R.id.tv_shop_card_stats);
                tvRarity = v.findViewById(R.id.tv_shop_card_rarity);
                tvCost   = v.findViewById(R.id.tv_shop_card_cost);
                btnBuy   = v.findViewById(R.id.btn_shop_buy);
            }
        }
    }
}
