package in.dukkan.service;

import static in.dukkan.service.NameRelevance.field;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameRelevanceTest {

    @Test
    void exactNameBeatsADescriptionMention() {
        double named = NameRelevance.score("amul", field("Amul", 1), field("kirana", 0.15));
        double mentioned = NameRelevance.score(
                "amul",
                field("Gupta Store", 1),
                field("We also stock Amul butter", 0.15));
        assertTrue(named > mentioned);
        assertTrue(named >= NameRelevance.MIN_SCORE);
        assertTrue(mentioned < NameRelevance.MIN_SCORE);
    }

    @Test
    void leadingNameBeatsAnAddressHit() {
        double shop = NameRelevance.score("gupta", field("Gupta Kirana", 1), field("Lajpat Nagar", 0.12));
        double address = NameRelevance.score("gupta", field("Daily Needs", 1), field("12 Gupta Road", 0.12));
        assertTrue(shop > address);
        assertTrue(address < NameRelevance.MIN_SCORE);
    }

    @Test
    void bothWordsInTheNameRankAboveASingleWord() {
        double both = NameRelevance.score("amul taaza", field("Amul Taaza Milk", 1));
        double one = NameRelevance.score("amul taaza", field("Taaza Juice", 1));
        assertTrue(both > one);
    }

    @Test
    void unrelatedTextDoesNotMatch() {
        double score = NameRelevance.score("milk", field("Family Pack", 1), field("household", 0.15));
        assertTrue(score < NameRelevance.MIN_SCORE);
    }
}
