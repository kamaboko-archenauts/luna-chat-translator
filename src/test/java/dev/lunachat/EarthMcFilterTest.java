package dev.lunachat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EarthMcFilterTest {
    @Test void ignoresScreenshotVoteMessages() {
        assertTrue(EarthMcFilter.ignore("[Byzantium] [Myth++] lehose_ /vote vote"));
        assertTrue(EarthMcFilter.ignore("BPN voted and received a gold crate /vote"));
        assertTrue(EarthMcFilter.ignore("BPN voted and received a gold crate"));
    }
    @Test void preservesOrdinaryChatAndSimilarCommands() {
        assertFalse(EarthMcFilter.ignore("Andrew: it can be rebuilt"));
        assertFalse(EarthMcFilter.ignore("We should vote on the new town name"));
        assertFalse(EarthMcFilter.ignore("/voter list"));
    }
}
