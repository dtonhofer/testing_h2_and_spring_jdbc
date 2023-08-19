package name.heavycarbon.h2_exercises.agents_and_msgs.crud;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

// ---
// Just a hack to get timezone ids
// ---

public class PrintTimezones {

    @Test
    void listingTimeZones() {
        System.out.print("Available TimeZone IDs:");
        for (String a : TimeZone.getAvailableIDs()) {
            System.out.print("\n");
            System.out.print(a);
        }
    }

}
