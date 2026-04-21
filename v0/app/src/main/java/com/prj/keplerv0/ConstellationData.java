package com.prj.keplerv0;

public class ConstellationData {

    public static class Constellation {
        public final String name;
        public final int[] memberStars;
        public final String subtitle;
        public final String mythology;
        public final boolean isZodiac;

        public Constellation(String name, boolean isZodiac, String subtitle, String mythology, int[] memberStars) {
            this.name = name;
            this.isZodiac = isZodiac;
            this.subtitle = subtitle;
            this.mythology = mythology;
            this.memberStars = memberStars;
        }
    }

    // Zodiac and highly recognizable major constellations
    public static final Constellation[] CONSTELLATIONS = {
        // Zodiac
        new Constellation("Aries", true, "The Ram", "The winged ram whose Golden Fleece was sought by Jason and the Argonauts.", new int[]{9884, 8903, 8832}),
        new Constellation("Taurus", true, "The Bull", "Zeus in disguise, bearing Princess Europa across the sea.", new int[]{21421, 20889, 20894, 20205, 20455, 18724, 15900, 20648, 17847}),
        new Constellation("Gemini", true, "The Twins", "Castor and Pollux, the inseparable twin brothers of Helen of Troy.", new int[]{37826, 36850, 32246, 30883, 30343, 29655, 28734, 31681, 34088, 35550, 35350, 32362, 36962, 37740}),
        new Constellation("Cancer", true, "The Crab", "The giant crab sent by Hera to thwart Hercules, later placed in the stars.", new int[]{43103, 42806, 40843, 42911, 40526, 44066}),
        new Constellation("Leo", true, "The Lion", "The Nemean Lion, defeated by Hercules as his first labor.", new int[]{49669, 50583, 54872, 57632, 54879, 49583, 50335, 48455, 47908}),
        new Constellation("Virgo", true, "The Maiden", "Astrea, the goddess of innocence, justice, and purity.", new int[]{65474, 66249, 68520, 72220, 63090, 63608, 61941, 57380, 60030}),
        new Constellation("Libra", true, "The Scales", "The scales of justice held by Virgo.", new int[]{72622, 73714, 76333, 74785}),
        new Constellation("Scorpius", true, "The Scorpion", "The scorpion that stung Orion the Hunter, placed perpetually opposite him in the sky.", new int[]{80763, 78401, 78265, 78820, 77516, 77622, 77070, 76276, 77233, 78072, 77450}),
        new Constellation("Sagittarius", true, "The Archer", "The centaur archer Chiron, pointing his arrow at the Scorpion.", new int[]{89931, 90496, 89642, 90185, 88635, 87072, 93506, 92041, 89341, 93864, 92855, 93085, 93683, 94820, 95168, 96406, 98688, 98412, 98032, 95347, 95294}),
        new Constellation("Capricornus", true, "The Sea Goat", "Pricus, the father of sea-goats, granted eternal life by the gods.", new int[]{100064, 100345, 104139, 105515, 106985, 107556, 105881, 102485, 102978}),
        new Constellation("Aquarius", true, "The Water Bearer", "Ganymede, cup-bearer to the Olympian gods.", new int[]{109074, 110395, 110960, 111497, 112961, 114855, 115438, 110003, 109139, 111123, 112716, 113136, 114341, 106278}),
        new Constellation("Pisces", true, "The Fishes", "Aphrodite and Eros disguised as fish to escape the monster Typhon.", new int[]{113881, 112158, 109352, 112748, 112440, 109176, 107354, 113963, 112447, 112029, 109427, 107315, 677, 1067}),

        // Major Northern / Famous Constellations
        new Constellation("Orion", false, "The Hunter", "The famed giant hunter, easily recognized by his three-star belt.", new int[]{27989, 24436, 25336, 25930, 26311, 26727, 27366}), // Approximate major stars (Betelgeuse, Rigel, Bellatrix, Mintaka, Alnilam, Alnitak, Saiph)
        new Constellation("Ursa Major", false, "The Great Bear", "Contains the famous Big Dipper asterism, used for navigation for millennia.", new int[]{54061, 53910, 58001, 59774, 62956, 65378, 67301}), // Big Dipper stars broadly
        new Constellation("Cassiopeia", false, "The Seated Queen", "The vain queen who boasted of her beauty, punished by Poseidon.", new int[]{11786, 3179, 4427, 6686, 8886}), // The W shape
        new Constellation("Lyra", false, "The Lyre", "The musical instrument played by Orpheus, holding the brilliant star Vega.", new int[]{91262, 91971, 92420, 93356, 92833}), // Vega etc.
        new Constellation("Cygnus", false, "The Swan", "Contains the Northern Cross, flying down the Milky Way.", new int[]{102098, 97165, 95853, 94779, 100453})  // Deneb etc.
    };
}
