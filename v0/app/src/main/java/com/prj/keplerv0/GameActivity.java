package com.prj.keplerv0;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.wifi.p2p.WifiP2pManager;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity implements GameEngine.GameUpdateListener {

    private GameEngine engine;
    private TextView tvUserHp, tvUserEnergy, tvAiHp, tvAiEnergy, tvAiCard, tvCardName, tvCardStats, tvTurnIndicator;
    private TextView tvEffectAnim;
    private ProgressBar pbUserHp, pbUserEnergy, pbAiHp, pbAiEnergy;
    private Button btnAtk1, btnAtk2, btnDef1, btnDef2, btnEndTurn, btnSwapCard;
    
    private boolean isMultiplayer = false;
    private boolean opponentCardReceived = false;
    private boolean gameStarted = false;
    private boolean isGameOver = false;
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
        
        // Turn will be randomized by host and synced during START_GAME
        boolean isHost = getIntent().getBooleanExtra("is_host", false);

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
                    setOpponentDeck(parts[1]);
                    opponentCardReceived = true;
                    
                    StringBuilder myDeckStr = new StringBuilder();
                    for(int i=0; i<engine.user.deck.size(); i++) {
                        myDeckStr.append(engine.user.deck.get(i).name);
                        if(i < engine.user.deck.size() - 1) myDeckStr.append(",");
                    }
                    socketManager.sendMessage("CARD:" + myDeckStr.toString());
                    
                    if (isHost) {
                        boolean hostGoesFirst = new Random().nextBoolean();
                        engine.isUserTurn = hostGoesFirst;
                        socketManager.sendMessage("START_GAME:" + !hostGoesFirst);
                        startGameFlow();
                    }
                }
            } else if (parts[0].equals("START_GAME")) {
                if (parts.length > 1) {
                    engine.isUserTurn = Boolean.parseBoolean(parts[1]);
                } else {
                    engine.isUserTurn = false;
                }
                startGameFlow();
            } else if (parts[0].equals("END_TURN")) {
                engine.endTurn();
            } else if (parts[0].equals("SYNC")) {
                engine.ai.hp = Integer.parseInt(parts[1]);
                engine.ai.energy = Integer.parseInt(parts[2]);
                engine.user.hp = Integer.parseInt(parts[3]);
                engine.user.energy = Integer.parseInt(parts[4]);
                if (parts.length > 5) {
                    engine.ai.elementalStatus = GameEngine.ElementalStatus.valueOf(parts[5]);
                    engine.user.elementalStatus = GameEngine.ElementalStatus.valueOf(parts[6]);
                }
                onUpdate();
            } else if (parts[0].equals("SWAP")) {
                for (Card c : engine.ai.deck) {
                    if (c.name.equals(parts[1])) {
                        engine.ai.activeCard = c;
                        engine.ai.nextAttackBonus = 0;
                        engine.ai.tempDefenseBonus = 0;
                        engine.ai.hasShield = false;
                        engine.ai.dodgeNext = false;
                        onLog("Opponent swapped to " + c.name);
                        onUpdate();
                        break;
                    }
                }
            }
        });

        syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (!opponentCardReceived) {
                    StringBuilder myDeckStr = new StringBuilder();
                    for(int i=0; i<engine.user.deck.size(); i++) {
                        myDeckStr.append(engine.user.deck.get(i).name);
                        if(i < engine.user.deck.size() - 1) myDeckStr.append(",");
                    }
                    socketManager.sendMessage("CARD:" + myDeckStr.toString());
                    syncHandler.postDelayed(this, 2000);
                }
            }
        };
        syncHandler.post(syncRunnable);
    }

    private void startGameFlow() {
        gameStarted = true;
        engine.startTurn();
        onLog(engine.isUserTurn ? "You go first!" : "Opponent goes first!");
    }

    private void setOpponentDeck(String deckStr) {
        String[] cardNames = deckStr.split(",");
        for (String cName : cardNames) {
            for (Card c : GameEngine.getLibrary()) {
                if (c.name.equals(cName)) {
                    engine.ai.deck.add(c);
                    break;
                }
            }
        }
        if (!engine.ai.deck.isEmpty()) {
            engine.ai.activeCard = engine.ai.deck.get(0);
        }
        onUpdate();
        onLog("Opponent brought: " + deckStr.replace(",", " & "));
    }

    private void initUi() {
        tvUserHp = findViewById(R.id.tv_user_hp);
        tvUserEnergy = findViewById(R.id.tv_user_energy);
        tvAiHp = findViewById(R.id.tv_ai_hp);
        tvAiEnergy = findViewById(R.id.tv_ai_energy);
        tvAiCard = findViewById(R.id.tv_ai_card);
        tvCardName = findViewById(R.id.tv_card_name);
        tvCardStats = findViewById(R.id.tv_card_stats);
        tvTurnIndicator = findViewById(R.id.tv_turn_indicator);
        tvEffectAnim = findViewById(R.id.tv_effect_anim);
        pbUserHp = findViewById(R.id.pb_user_hp);
        pbUserEnergy = findViewById(R.id.pb_user_energy);
        pbAiHp = findViewById(R.id.pb_ai_hp);
        pbAiEnergy = findViewById(R.id.pb_ai_energy);
        btnAtk1 = findViewById(R.id.btn_atk1);
        btnAtk2 = findViewById(R.id.btn_atk2);
        btnDef1 = findViewById(R.id.btn_def1);
        btnDef2 = findViewById(R.id.btn_def2);
        btnEndTurn = findViewById(R.id.btn_end_turn);
        btnSwapCard = findViewById(R.id.btn_swap_card);

        btnEndTurn.setOnClickListener(v -> {
            engine.endTurn();
            if (isMultiplayer) {
                socketManager.sendMessage("END_TURN");
                socketManager.sendMessage("SYNC:" + engine.user.hp + ":" + engine.user.energy + ":" + engine.ai.hp + ":" + engine.ai.energy + ":" + engine.user.elementalStatus.name() + ":" + engine.ai.elementalStatus.name());
            }
        });

        btnSwapCard.setOnClickListener(v -> {
            if (!engine.isUserTurn || engine.user.deck.size() < 2) return;
            String otherCard = "";
            for (Card c : engine.user.deck) {
                if (!c.name.equals(engine.user.activeCard.name)) {
                    otherCard = c.name;
                    break;
                }
            }
            if (!otherCard.isEmpty() && engine.swapCard(otherCard)) {
                updateAbilityButtons();
                if (isMultiplayer) {
                    socketManager.sendMessage("SWAP:" + otherCard);
                    socketManager.sendMessage("SYNC:" + engine.user.hp + ":" + engine.user.energy + ":" + engine.ai.hp + ":" + engine.ai.energy + ":" + engine.user.elementalStatus.name() + ":" + engine.ai.elementalStatus.name());
                }
            }
        });
    }

    private void setupGame() {
        engine = new GameEngine(this);
        List<Card> library = GameEngine.getLibrary();
        
        List<String> selectedCards = getIntent().getStringArrayListExtra("selected_cards");
        if (selectedCards != null && !selectedCards.isEmpty()) {
            for (String sName : selectedCards) {
                for (Card c : library) {
                    if (c.name.equals(sName)) engine.user.deck.add(c);
                }
            }
        } else {
            // Fallback for single card
            String selectedCard = getIntent().getStringExtra("selected_card");
            for (Card c : library) {
                if (c.name.equals(selectedCard)) {
                    engine.user.deck.add(c);
                    break;
                }
            }
        }
        
        if (engine.user.deck.isEmpty()) engine.user.deck.add(library.get(0));
        engine.user.activeCard = engine.user.deck.get(0);
        
        // Setup AI deck
        if (!isMultiplayer) {
            engine.initSinglePlayerMatch(engine.user.deck, 2); // Pass deck and difficulty 2
            gameStarted = true; // Fix: Set UI state to started so user can press buttons
            onLog("You go first!");
            onUpdate(); // Force UI refresh so buttons unlock
        } else {
            // Assign a placeholder card until the opponent syncs their deck
            engine.ai.activeCard = library.get(0);
        }

        updateAbilityButtons();

        if (isMultiplayer) {
            onUpdate();
            onLog("Waiting for opponent connection...");
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
        String elementStr = ability.element != Ability.Element.NEUTRAL ? ability.element.name() : "N/A";
        String cdStr = ability.currentCooldown > 0 ? "\n(CD: " + ability.currentCooldown + ")" : "";
        btn.setText(ability.name + "\n(" + elementStr + ")\nCost: " + ability.energyCost + "E" + cdStr);
        
        // Color coding
        if (isAttack) {
            btn.setTextColor(0xFFFF7777); // Light Red
        } else {
            btn.setTextColor(0xFF77AAFF); // Light Blue
        }
        
        btn.setOnClickListener(v -> {
            if (!engine.isUserTurn) return;
            if (isMultiplayer) {
                socketManager.sendMessage("MOVE:" + index + ":" + isAttack);
            }
            engine.playAbility(ability);
            updateAbilityButtons();
            if (isMultiplayer) {
                socketManager.sendMessage("SYNC:" + engine.user.hp + ":" + engine.user.energy + ":" + engine.ai.hp + ":" + engine.ai.energy + ":" + engine.user.elementalStatus.name() + ":" + engine.ai.elementalStatus.name());
            }
        });
    }

    @Override
    public void onUpdate() {
        tvUserHp.setText(engine.user.hp + "/20");
        pbUserHp.setProgress(engine.user.hp);
        
        tvUserEnergy.setText(engine.user.energy + "/10");
        pbUserEnergy.setProgress(engine.user.energy);
        
        tvAiHp.setText(engine.ai.hp + "/20");
        pbAiHp.setProgress(engine.ai.hp);
        
        tvAiEnergy.setText(engine.ai.energy + "/10");
        pbAiEnergy.setProgress(engine.ai.energy);

        tvCardName.setText(engine.user.activeCard.name);
        tvCardStats.setText("ATK: " + engine.user.activeCard.attack + " | DEF: " + engine.user.activeCard.defense + "\nStatus: " + engine.user.elementalStatus.name());
        tvAiCard.setText(engine.ai.activeCard.name + " (" + engine.ai.elementalStatus.name() + ")");
        
        // Centralized game over check ensures both players see game over
        if (engine.user.hp <= 0) {
            onGameOver(isMultiplayer ? "Opponent" : "AI");
            return;
        } else if (engine.ai.hp <= 0) {
            onGameOver("User");
            return;
        }

        boolean isMyTurn = engine.isUserTurn && gameStarted;
        if (isMultiplayer && !opponentCardReceived) isMyTurn = false;

        tvTurnIndicator.setText(isMyTurn ? "YOUR TURN" : "OPPONENT'S TURN");
        tvTurnIndicator.setTextColor(isMyTurn ? 0xFF00FF00 : 0xFFFF0000);
        btnEndTurn.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
        btnSwapCard.setVisibility(engine.user.deck.size() > 1 ? View.VISIBLE : View.GONE);
        btnSwapCard.setEnabled(isMyTurn && engine.user.energy >= 1);
        
        updateAbilityButtons(); // Refresh CD texts

        btnAtk1.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.attackAbilities.get(0).energyCost && engine.user.activeCard.attackAbilities.get(0).currentCooldown == 0);
        btnAtk2.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.attackAbilities.get(1).energyCost && engine.user.activeCard.attackAbilities.get(1).currentCooldown == 0);
        btnDef1.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.defenseAbilities.get(0).energyCost && engine.user.activeCard.defenseAbilities.get(0).currentCooldown == 0);
        btnDef2.setEnabled(isMyTurn && engine.user.energy >= engine.user.activeCard.defenseAbilities.get(1).energyCost && engine.user.activeCard.defenseAbilities.get(1).currentCooldown == 0);
    }

    @Override
    public void onGameOver(String winner) {
        if (isGameOver) return;
        isGameOver = true;
        
        String displayWinner = winner;
        if (isMultiplayer) {
            if (winner.equals("User")) displayWinner = "You";
            else displayWinner = "Opponent";
        }
        
        onLog("Game Over! Winner: " + displayWinner);
        Toast.makeText(this, "Game Over! Winner: " + displayWinner, Toast.LENGTH_LONG).show();
        
        // Delay finish to allow final socket messages (SYNC, MOVE) to flush
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 3500);
    }

    @Override
    public void onLog(String message) {
        TextView tvLog = findViewById(R.id.tv_game_log);
        if (tvLog != null) {
            tvLog.append(message + "\n");
        }
        
        // Floating text animation for effects
        tvEffectAnim.setText(message);
        tvEffectAnim.setVisibility(View.VISIBLE);
        tvEffectAnim.setAlpha(1f);
        tvEffectAnim.setScaleX(0.5f);
        tvEffectAnim.setScaleY(0.5f);
        tvEffectAnim.setTranslationY(100f);
        
        tvEffectAnim.animate()
            .translationY(-100f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .alpha(0f)
            .setDuration(1500)
            .withEndAction(() -> tvEffectAnim.setVisibility(View.INVISIBLE))
            .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        syncHandler.removeCallbacks(syncRunnable);
        
        if (isMultiplayer) {
            // Close the socket to free the port
            if (socketManager != null) {
                socketManager.close();
            }
            
            // Tear down the Wi-Fi Direct group so future games don't get stuck
            try {
                WifiP2pManager manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
                if (manager != null) {
                    WifiP2pManager.Channel channel = manager.initialize(this, getMainLooper(), null);
                    manager.removeGroup(channel, null);
                }
            } catch (Exception e) {
                Log.e("KeplerNet", "Error cleaning up P2P group: " + e.getMessage());
            }
        }
    }
}
