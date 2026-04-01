package com.prj.keplerv0;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity implements GameEngine.GameUpdateListener {

    private GameEngine engine;
    private TextView tvUserHp, tvUserEnergy, tvAiHp, tvAiEnergy, tvAiCard, tvCardName, tvCardStats, tvLog;
    private Button btnAtk1, btnAtk2, btnDef1, btnDef2, btnEndTurn;
    
    private boolean isMultiplayer = false;
    private boolean opponentCardReceived = false;
    private GameSocketManager socketManager;
    private Handler syncHandler = new Handler(Looper.getMainLooper());
    private Runnable syncRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        isMultiplayer = getIntent().getBooleanExtra("is_multiplayer", false);
        
        initUi();
        setupGame();
        
        if (isMultiplayer) {
            setupMultiplayer();
        }
    }

    private void setupMultiplayer() {
        engine.isMultiplayer = true;
        socketManager = GameSocketManager.getInstance();
        socketManager.setListener(msg -> {
            Log.d("KeplerNet", "Game Received: " + msg);
            String[] parts = msg.split(":");
            if (parts[0].equals("MOVE")) {
                int abilityIndex = Integer.parseInt(parts[1]);
                boolean isAttack = Boolean.parseBoolean(parts[2]);
                
                Ability remoteAbility = isAttack ? 
                    engine.ai.activeCard.attackAbilities.get(abilityIndex) : 
                    engine.ai.activeCard.defenseAbilities.get(abilityIndex);
                
                engine.playAbility(remoteAbility);
            } else if (parts[0].equals("CARD")) {
                if (!opponentCardReceived) {
                    setOpponentCard(parts[1]);
                    opponentCardReceived = true;
                    // Send my card back as ACK
                    socketManager.sendMessage("CARD:" + engine.user.activeCard.name);
                    
                    if (getIntent().getBooleanExtra("is_host", false)) {
                        // Host sends start signal
                        socketManager.sendMessage("START_GAME");
                        engine.startTurn();
                    }
                }
            } else if (parts[0].equals("START_GAME")) {
                engine.startTurn();
            } else if (parts[0].equals("END_TURN")) {
                engine.endTurn();
            }
        });

        // Repeatedly send card info until opponent acknowledges
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (!opponentCardReceived) {
                    socketManager.sendMessage("CARD:" + engine.user.activeCard.name);
                    syncHandler.postDelayed(this, 2000);
                }
            }
        };
        syncHandler.post(syncRunnable);
    }

    private void setOpponentCard(String cardName) {
        for (Card c : GameEngine.getLibrary()) {
            if (c.name.equals(cardName)) {
                engine.ai.activeCard = c;
                onUpdate();
                onLog("Opponent selected: " + cardName);
                break;
            }
        }
    }

    private void initUi() {
        tvUserHp = findViewById(R.id.tv_user_hp);
        tvUserEnergy = findViewById(R.id.tv_user_energy);
        tvAiHp = findViewById(R.id.tv_ai_hp);
        tvAiEnergy = findViewById(R.id.tv_ai_energy);
        tvAiCard = findViewById(R.id.tv_ai_card);
        tvCardName = findViewById(R.id.tv_card_name);
        tvCardStats = findViewById(R.id.tv_card_stats);
        tvLog = findViewById(R.id.tv_game_log);

        btnAtk1 = findViewById(R.id.btn_atk1);
        btnAtk2 = findViewById(R.id.btn_atk2);
        btnDef1 = findViewById(R.id.btn_def1);
        btnDef2 = findViewById(R.id.btn_def2);
        btnEndTurn = findViewById(R.id.btn_end_turn);

        btnEndTurn.setOnClickListener(v -> {
            if (isMultiplayer) {
                socketManager.sendMessage("END_TURN");
            }
            engine.endTurn();
        });
    }

    private void setupGame() {
        engine = new GameEngine(this);
        List<Card> library = GameEngine.getLibrary();
        
        String selectedCard = getIntent().getStringExtra("selected_card");
        for (Card c : library) {
            if (c.name.equals(selectedCard)) {
                engine.user.activeCard = c;
                break;
            }
        }
        if (engine.user.activeCard == null) engine.user.activeCard = library.get(0);

        engine.ai.activeCard = library.get(0); // Placeholder

        updateAbilityButtons();

        if (!isMultiplayer) {
            engine.ai.activeCard = library.get(new Random().nextInt(library.size()));
            engine.startTurn();
        } else {
            onUpdate();
            onLog("Waiting for opponent...");
        }
    }

    private void updateAbilityButtons() {
        Card c = engine.user.activeCard;
        setupAbilityButton(btnAtk1, c.attackAbilities.get(0), 0, true);
        setupAbilityButton(btnAtk2, c.attackAbilities.get(1), 1, true);
        setupAbilityButton(btnDef1, c.defenseAbilities.get(0), 0, false);
        setupAbilityButton(btnDef2, c.defenseAbilities.get(1), 1, false);
    }

    private void setupAbilityButton(Button btn, Ability ability, int index, boolean isAttack) {
        btn.setText(ability.name + "\n(" + ability.energyCost + " E)");
        btn.setOnClickListener(v -> {
            if (isMultiplayer) {
                socketManager.sendMessage("MOVE:" + index + ":" + isAttack);
            }
            engine.playAbility(ability);
        });
    }

    @Override
    public void onUpdate() {
        tvUserHp.setText("Your HP: " + engine.user.hp);
        tvUserEnergy.setText("Your Energy: " + engine.user.energy);
        tvAiHp.setText(isMultiplayer ? "Opponent HP: " + engine.ai.hp : "AI HP: " + engine.ai.hp);
        tvAiEnergy.setText(isMultiplayer ? "Opponent Energy: " + engine.ai.energy : "AI Energy: " + engine.ai.energy);
        
        tvCardName.setText(engine.user.activeCard.name);
        tvCardStats.setText("ATK: " + engine.user.activeCard.attack + " | DEF: " + engine.user.activeCard.defense);
        tvAiCard.setText(isMultiplayer ? "Opponent Card: " + engine.ai.activeCard.name : "AI Card: " + engine.ai.activeCard.name);

        boolean isMyTurn = engine.isUserTurn;
        if (isMultiplayer && !opponentCardReceived) isMyTurn = false;

        btnEndTurn.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
        
        btnAtk1.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.attackAbilities.get(0).energyCost);
        btnAtk2.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.attackAbilities.get(1).energyCost);
        btnDef1.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.defenseAbilities.get(0).energyCost);
        btnDef2.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.defenseAbilities.get(1).energyCost);
    }

    @Override
    public void onGameOver(String winner) {
        String displayWinner = winner;
        if (isMultiplayer) {
            if (winner.equals("User")) displayWinner = "You";
            else if (winner.equals("Opponent")) displayWinner = "Opponent";
        }
        Toast.makeText(this, "Game Over! Winner: " + displayWinner, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onLog(String message) {
        tvLog.append(message + "\n");
        findViewById(R.id.sv_log).post(() -> ((android.widget.ScrollView)findViewById(R.id.sv_log)).fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        syncHandler.removeCallbacks(syncRunnable);
    }
}
