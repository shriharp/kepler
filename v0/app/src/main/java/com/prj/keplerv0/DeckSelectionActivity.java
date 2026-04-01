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

        rv.setAdapter(new DeckAdapter(availableCards, card -> {
            Intent intent = new Intent(DeckSelectionActivity.this, GameActivity.class);
            intent.putExtra("selected_card", card.name);
            
            // Forward multiplayer info if present
            if (getIntent().hasExtra("is_multiplayer")) {
                intent.putExtra("is_multiplayer", getIntent().getBooleanExtra("is_multiplayer", false));
                intent.putExtra("is_host", getIntent().getBooleanExtra("is_host", false));
                intent.putExtra("host_address", getIntent().getStringExtra("host_address"));
            }
            
            startActivity(intent);
            finish();
        }));
    }

    private static class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.ViewHolder> {
        interface OnCardClickListener { void onCardClick(Card card); }
        private List<Card> cards;
        private OnCardClickListener listener;

        DeckAdapter(List<Card> cards, OnCardClickListener listener) {
            this.cards = cards;
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
            holder.itemView.setOnClickListener(v -> listener.onCardClick(c));
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
