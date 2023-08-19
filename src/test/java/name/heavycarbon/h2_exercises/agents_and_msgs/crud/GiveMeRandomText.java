package name.heavycarbon.h2_exercises.agents_and_msgs.crud;

import org.jetbrains.annotations.NotNull;

import java.util.Random;

// ---
// Obtain some random text to send
// ---

public abstract class GiveMeRandomText {

    public static String getRandomText(@NotNull Random rand) {
        final String[] texts = {
                "Bronze Charger",
                "Bronze Dragon",
                "Clean Sweep",
                "Cliffhanger",
                "Desert Eclipse",
                "Desert Eye",
                "Desert Tornado",
                "Drum And Brass",
                "Enigma",
                "Fallout",
                "Golden Cobra",
                "Hidden Vengeance",
                "Orange Gate",
                "Red Lilly",
                "Silver Charger",
                "Silver Moon",
                "Urban Tornado",
                "White Citadel"
        };
        final int textIndex = rand.nextInt(texts.length);
        final int anInteger = rand.nextInt(100);
        return texts[textIndex] + " " + anInteger;
    }
}
