package com.prj.keplerv0;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.widget.Button;
import android.graphics.Color;

public class DeckSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_selection);

        RecyclerView rv = findViewById(R.id.rv_deck_selection);
        rv.setLayoutManager(new LinearLayoutManager(this));

        Set<String>  collected    = CollectionManager.getCollection(this);
        Set<String>  partial      = CollectionManager.getPartialCollection(this);
        List<Card>   allCards     = GameEngine.getLibrary();

        // Full unlocks
        List<Card> fullCards = new ArrayList<>();
        for (Card c : allCards) {
            if (collected.contains(c.name)) fullCards.add(c);
        }

        // Partial unlocks (weakened)
        List<Card> partialCards = new ArrayList<>();
        for (String name : partial) {
            Card weak = GameEngine.getWeakenedCard(name);
            if (weak != null) partialCards.add(weak);
        }

        if (fullCards.isEmpty() && partialCards.isEmpty()) {
            Toast.makeText(this,
                    "You haven't collected any cards yet!\nTap a constellation in the sky viewer or complete a JoinDots puzzle.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Flat list: full cards → null sentinel (header) → partial cards
        List<Card> rows = new ArrayList<>(fullCards);
        if (!partialCards.isEmpty()) {
            rows.add(null);
            rows.addAll(partialCards);
        }

        Button btnConfirm = findViewById(R.id.btn_confirm_deck);
        List<String> selectedCards = new ArrayList<>();

        DeckAdapter adapter = new DeckAdapter(rows, selectedCards, () -> {
            btnConfirm.setText("Confirm Deck (" + selectedCards.size() + "/2)");
            btnConfirm.setEnabled(selectedCards.size() == 2);
        });
        rv.setAdapter(adapter);

        btnConfirm.setOnClickListener(v -> {
            Intent intent = new Intent(DeckSelectionActivity.this, GameActivity.class);
            intent.putStringArrayListExtra("selected_cards", new ArrayList<>(selectedCards));

            // Forward multiplayer info if present
            if (getIntent().hasExtra("is_multiplayer")) {
                intent.putExtra("is_multiplayer", getIntent().getBooleanExtra("is_multiplayer", false));
                intent.putExtra("is_host",        getIntent().getBooleanExtra("is_host", false));
                intent.putExtra("host_address",   getIntent().getStringExtra("host_address"));
            }

            startActivity(intent);
            finish();
        });
    }

    private static class DeckAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface OnSelectionChangedListener { void onSelectionChanged(); }

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_CARD   = 1;

        private final List<Card>                rows;
        private final List<String>              selectedCards;
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

            if (selectedCards.contains(c.name)) {
                cvh.itemView.setBackgroundColor(Color.DKGRAY);
            } else {
                cvh.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

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
}
