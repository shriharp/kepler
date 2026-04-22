# Kepler Project Overview

This document provides a comprehensive technical breakdown of the Kepler application. It is designed to act as the single source of truth for understanding the system architecture, internal logic, and user flows of every completed feature in the codebase.

Everything documented below is fully implemented and functioning in the current codebase.

---

## Architecture & Code Structure

The Kepler app is built natively for Android in Java. It separates logic into core feature domains: rendering, constellation unlocking, dynamic quizzing, and card battling.

* **Rendering Engine:** Handles OpenGL contexts and math conversions (`StarGLSurfaceView.java`, `StarRenderer.java`, `SkyCalculator.java`).
* **Game Engine:** Controls card battle rules and state machine (`GameEngine.java`, `Card.java`, `Ability.java`).
* **Data Layer:** Assets are stored locally in the APK. Star data is loaded from `StarCatalog.java` and `ConstellationLoader.java`. Quiz questions are parsed from `quiz_questions.json`.
* **Persistence System:** Managed by `CollectionManager.java` which securely saves user progress using `SharedPreferences` (tracking "partial" and "full" card unlocks).
* **Multiplayer Infrastructure:** `GameSocketManager.java` implements native TCP Socket networking with dedicated threads via `ExecutorService` for concurrent non-blocking IO.

---

## 1. Star Rendering System

### What it does
It provides a 3D, physics-accurate, 360-degree virtual night sky rendered in real time. It reacts to device orientation (gyroscope) or touch gestures to determine where the user is looking.

### Why it exists
To give users an immersive, accurate astronomical view. Without this environment, exploration lacks the required spatial context.

### Exactly how it is implemented

* **Data Loading:** The `StarCatalog.java` loads statically bundled star data extracted from the Hipparcos (HYG) catalogue. It provides 3D vertices, Right Ascension (RA) / Declination (Dec) data, and magnitude scores.
* **Coordinate Projection:** Mathematical coordinate transformation is handled by `SkyCalculator.java`. It converts static Equatorial Coordinates (RA/Dec) into dynamic Topocentric Coordinates (Local Altitude/Azimuth) utilizing the user's geographic location (Lat/Lon) and the device's system time.
* **OpenGL Rendering:** Stars are rendered using OpenGL ES 2.0 inside `StarRenderer.java`. The `GL_POINTS` primitive handles the stars array. `gl_PointSize` is calculated inversely relative to the star's magnitude (brighter = larger point size).
* **Orientation Tracking:** Sensor data determines a rotation matrix, mapped mathematically from Device space to World space. This binds directly to the Model-View-Projection (MVP) matrix inside the GL thread.

### Resource Saving & Procedural Horizon
A **Resource Saving Mode** is heavily integrated for performance optimizations. If enabled:
1. **Horizon Culling:** A mathematical terrain altitude formula (using a composite of sine and cosine waves based on the local azimuth angle) dynamically draws a procedural ground mesh (`GL_TRIANGLE_STRIP`) and horizon glow (`GL_LINE_STRIP`).
2. **Invisible Star Culling:** Stars whose mathematical Z-coordinate drops below the dynamically created local terrain boundary `tz < Math.sin(getTerrainAltitudeRad(azRad))` are skipped during the vertex load.
3. **Dim Star Culling:** Any star with a magnitude > 4.5 is preemptively filtered out from the rendered buffer to save geometry processing.

---

## 2. Label Rendering System

### What it does
Overlays dynamic, readable UI labels (constellation names and star names) onto the corresponding OpenGL entities in the night sky.

### Why it exists
Raw stars visually blend together. Labels are strictly required for an educational app identifying the celestial bodies. 

### Exactly how it is implemented

* **Projection Algorithm:** `StarRenderer.java` intercepts the 3D world vectors `(x, y, z)` of all core stars and constellation centroids, multiplies them by the current MVP matrix, and converts the output to Normalized Device Coordinates (NDC) to pinpoint exact 2D Screen `(X, Y)` coordinates.
* **The Canvas Overlay (`SkyOverlayView.java`):** Rendering text cleanly inside standard OpenGL ES is intensive. Instead, a custom transparent Android `View` sits above the GLSurfaceView. It receives an array of `SkyLabel` models containing screen coordinates, text, and categories.

### Smart Visibility & Anti-Clutter Rules
1. **Priority Sorting:** Labels are evaluated by their magnitude. A priority system guarantees that the brightest bodies are placed first. Constellations (`Type.CONSTELLATION`) bypass this to secure the highest absolute rendering priority.
2. **AABB Collision (Overlap Prevention):** Inside `onDraw`, bounding boxes (`RectF`) are calculated based on the font sizing required for each string. The view maintains an array of all currently drawn bounds. Before drawing the next label, `RectF.intersects` runs. If overlapping an existing label, the incoming label is discarded entirely for that frame.
3. **Occlusion Checks:** Labels corresponding to objects physically located behind the procedural terrain mesh are rejected inside the renderer before they ever reach the Canvas overlay.

---

## 3. Join-the-Stars Constellation Unlock System

### What it does
An interactive mapping puzzle where players connect the dots of real star constellations with their fingers to officially "unlock" them within their digital collection. 

### Why it exists
Instead of merely tapping a button, physically tracing the actual shape of a constellation provides tactile memory, boosting the educational aspect. 

### Exactly how it is implemented

* **Architecture:** Executed within the custom `JoinDotsView.java`. It takes a set of target line connections based on the structural skeleton of the constellation.
* **Touch Gesture Recognition:** `onTouchEvent` captures `ACTION_DOWN`, `ACTION_MOVE`, and `ACTION_UP`. It uses a Euclidean distance rule to snap the user's cursor to the closest `StarPoint` mathematically (with an interaction radius of 80px).
* **Magnifier Glass Mechanism:** As the user drags across the screen covering lines with their finger, a real-time clipping mask (`Path.addCircle`) scales and renders the scene 2.5x dynamically 250px above the touch-point to preserve visibility for small star connections.
* **Line Validation System:** When a stroke completes (`ACTION_UP`), the engine checks the starting node against the ending node. If the connection strictly matches an undrawn `targetLine`, it is snapped visually and committed.
* **Completion State & Persistence:** When `drawnLines.size() >= targetLines.size()`, completion triggers `onConstellationCompleted()`. `CollectionManager.java` manages state:
   - Tapping an unfound constellation in `StarRenderer` grants a **"Partial Unlock"** (`KEY_PARTIAL`).
   - Finishing the tracing game escalates it to a **"Full Unlock"** (`KEY_CARDS`).

---

## 4. Dynamic Quiz System (Astronomy + Mythology)

### What it does
A multi-difficulty question engine presenting users with lore and scientific facts about constellations. Users interact with the quiz to test knowledge potentially paired with gameplay loops.

### Why it exists
Enforces retention of facts without alienating the user by statically keeping tasks too easy or brutally difficult.

### Exactly how it is implemented

* **Question Storage:** `quiz_questions.json` holds structured definitions (questions, multiple choices, correct indexing, and categorical explanations). `QuizManager.java` loads this into memory upon initialization and segments questions into Easy, Medium, and Hard lists dynamically shuffled.
* **The Adaptive Difficulty Engine (`DifficultyManager.java`):**
  This system evaluates real-time telemetry from the user's sessions to dynamically select the next difficulty.
  
  Three metrics build a combined `confidenceScore`:
  1. **Accuracy (%):** General correct vs complete ratio.
  2. **Speed Score:** Calculated mathematically: `1.0 - (Math.max(0, avgTime - 3000) / 7000.0)`. Fast answers (<3s) score 1.0, trailing off to 0.0 at (>10s).
  3. **Streak Score:** `currentStreak / 5.0` (capped at 5).
  
  The formula weights them:
  ```java
  confidence = (accuracy * 0.5) 
             + (speedScore * 0.2) 
             + (streakScore * 0.3);
  ```
* **Thresholds for Transition:**
  - `< 0.40`: Drops to **EASY** mode.
  - `0.40 - 0.74`: Stabilizes at **MEDIUM** mode.
  - `>= 0.75`: Rises to **HARD** mode.
* Why lightweight? Heavy ML was explicitly avoided to ensure the game runs locally on all mobile hardware securely and instantly, without required network calls.

---

## 5. Multiplayer Card Battle Game 

### What it does
An intensive, turn-based celestial battle system where users fight AI or live players using decks constructed from the constellations they've unlocked in the night sky.

### Why it exists
To gamify astronomy collection. This provides intrinsic motivation for the user to explore the sky, trace constellations, and master mythology. 

### Exactly how it is implemented

* **Card State:** Every single card uses the `Card.java` definition featuring base HP, base Defense, and holds specific offensive and defensive implementations of `Ability.java`. 
* **Cost & Economy System:** Implemented inside `GameEngine.java`. Players start with `0` Energy. On the first two turns of the game, players generate `2` Energy. From turn 3 onward, generation increases to `3` Energy per turn. Maximum energy halts at `10`.

### Action Phase & Damage Mechanics
During an attack phase, calculations utilize absolute and multiplier values:

```java
int totalAtk = attacker.activeCard.attack + attacker.nextAttackBonus + ability.value;
int targetDef = Math.max(0, defender.activeCard.defense + defender.tempDefenseBonus - defender.defenseReduction);

// If ability effect == IGNORE_DEF, targetDef bypasses directly to 0.

float damageMultiplier = 10.0f / (10.0f + targetDef);
if (defender.hasShield) {
    damageMultiplier *= 0.5f; // Shield mitigates 50%
}

int damage = Math.max(1, (int)(totalAtk * damageMultiplier));
```

### Complex Interactions & Mechanics
1. **Status Effects:** Handles Poison (`hp -= 2` at start-of-turn for 3 cycles), Dodge (boolean evades 100% of the next attack), Shields (50% reduction).
2. **Dynamic Defense Cleansing:** Elemental defense abilities act as cleanses. Casting a `WATER` or `EARTH` defense naturally strips off a `BURNING` debuff while refunding `1 HP` on a successful cleanse.
3. **Rich Elemental Reactions System:**
   - `FIRE + WATER` (or vice versa): **Vaporize** (Flat 1.5x damage multiplier applied to `damage`).
   - `EARTH + AIR` (or vice versa): **Sandstorm** (Inflicts target with `dodgeNext = true` granting them temporary blindness to user hits).
   - `WATER + EARTH` (or vice versa): **Mud** (Drains 1 energy point and refunds it to the attacker).
   - `FIRE + AIR` (or vice versa): **Firestorm** (+4 flat damage on top of formula outcome).
   - `LIGHT + DARK`: **Eclipse** (Converts the total attack explicitly to True Damage bypassing all armor entirely: `damage = totalAtk`).

### Network Multiplayer Architecture
Handled via `GameSocketManager.java`. The host creates a `ServerSocket(8888)` to listen dynamically while the opponent builds an absolute TCP `Socket` configuration toward the host IP. 
To bypass UI freezes, Network streams (`PrintWriter` / `BufferedReader`) utilize separate `ExecutorService` instances for concurrent send and receive behavior, pushing resolved states exclusively through `Handler(Looper.getMainLooper())`.

*Note: For cards NOT fully unlocked via the constellation trace game, a `getWeakenedCard()` routine dynamically mathematically divides the card's offensive values and buffs in half (★½).*

---

## User Experience Flow (End-to-End Loop)

1. **User Opens App:** Drops directly into the immersive stargazing renderer (`StarGLSurfaceView`).
2. **Explores Stars:** Users tilt their device utilizing gyroscope coordinates to explore mathematically accurate positions of real-life constellations.
3. **Engages Discovery:** The user taps a highlighted label, shifting the celestial body into a **Partially Unlocked** state. 
4. **Traces Constellation:** A UI prompt allows them to launch the Join Dots Minigame to carefully trace the skeletal structure of the respective constellation.
5. **Registers Unlock:** Successfully hooking all lines transitions the card into a **Full Unlock** allowing structural inclusion into an active battle deck.
6. **Dynamic Study System:** The user can launch the Adaptive Quiz interface, tackling history/science trivia that adapts difficulty dynamically based on real-time streak performance.
7. **Multiplayer Battle Setup:** The player accesses the Deck menu, constructs a layout with newly earned high-power valid cards, and accesses the local multiplayer to battle locally hosted games.
8. **Card Combat Phase:** Engaging the complex multi-turn mathematical battle involving elemental combinations, tactical swaps, and elemental cleanses. 

---

## Novelty & Unique Technical Implementations

While astronomy apps and card games exist independently, Kepler introduces several highly novel, bespoke implementations not typically standard in the ecosystem:

1. **Mathematical Topocentric Rotational Matrix Integration:** Instead of relying on typical third-party 3D engine physics (like Unity or Unreal Euler angles), Kepler achieves mathematically pure synchronization between raw device Gyroscope sensor orientation matrices and celestial localized Topocentric coordinate frames (RA/Dec mapped to altitude/azimuth) natively within pure Java OpenGL ES.
2. **Dynamic Harmonic Horizon Procedural Generation:** Instead of loading heavy static 3D meshes for the ground, Kepler calculates procedural terrain meshes at load-time using a composite of trigonometric sine/cosine waves mapped to the viewing azimuth. This achieves a highly performant, custom-tailored horizon while strictly keeping the memory footprint nearly zero.
3. **Real-time AABB Font Bound Collision via Hardware Canvas Overlay:** Solving OpenGL text rendering complications natively without external libraries, Kepler calculates exact font boundaries (`RectF.intersects`) dynamically at 60 FPS in a secondary transparent Android Canvas overlay. It systematically rejects overlapping localized star labels through a real-time magnitude priority sort.
4. **Constellation Geometric Tracing as an Unlock Mechanism:** Turning celestial mechanics into a physical mapping validation game (`JoinDotsView`). The user is required to physically trace coordinate skeletons utilizing dynamic screen-clipping magnifiers (`Path.addCircle`), shifting basic UI button interactions into actual spatial geometry tests.
5. **Telemetry-based "No-ML" Adaptive Difficulty Engine:** Operating without heavy un-bundled models or remote network calls, the Quiz engine processes a custom heuristic algorithm mathematically using response speed averages, historical accuracy %, and dynamic answer streaks to yield a `confidence` metric, seamlessly and autonomously adjusting question pools on the local thread.
6. **Elemental Defensive Cleansing Mechanics:** Implementing a reactive elemental system, where executing specific defensive abilities (e.g., Earth / Water) dynamically tests for and strips active DoT debuffs (e.g., Burning / Drenched) from the player state, actively altering the status-effect architecture interdependently rather than through static armor increments.

---
