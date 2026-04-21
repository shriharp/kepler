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

        Set<String> collected = CollectionManager.getCollection(this);
        List<Card> allCards = GameEngine.getLibrary();
        List<Card> availableCards = new ArrayList<>();

        for (Card c : allCards) {
            if (collected.contains(c.name)) {
                availableCards.add(c);
            }
        }

        if (availableCards.isEmpty()) {
            Toast.makeText(this, "You haven't collected any cards yet! Draw some constellations first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Button btnConfirm = findViewById(R.id.btn_confirm_deck);
        List<String> selectedCards = new ArrayList<>();

        DeckAdapter adapter = new DeckAdapter(availableCards, selectedCards, () -> {
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
                intent.putExtra("is_host", getIntent().getBooleanExtra("is_host", false));
                intent.putExtra("host_address", getIntent().getStringExtra("host_address"));
            }
            
            startActivity(intent);
            finish();
        });
    }

    private static class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.ViewHolder> {
        interface OnSelectionChangedListener { void onSelectionChanged(); }
        private List<Card> cards;
        private List<String> selectedCards;
        private OnSelectionChangedListener listener;

        DeckAdapter(List<Card> cards, List<String> selectedCards, OnSelectionChangedListener listener) {
            this.cards = cards;
            this.selectedCards = selectedCards;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_constellation_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Card c = cards.get(position);
            holder.tvName.setText(c.name);
            holder.tvStatus.setText("ATK: " + c.attack + " | DEF: " + c.defense);
            
            if (selectedCards.contains(c.name)) {
                holder.itemView.setBackgroundColor(Color.DKGRAY);
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            holder.itemView.setOnClickListener(v -> {
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

        @Override
        public int getItemCount() { return cards.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_card_name);
                tvStatus = v.findViewById(R.id.tv_card_status);
            }
        }
    }
}
