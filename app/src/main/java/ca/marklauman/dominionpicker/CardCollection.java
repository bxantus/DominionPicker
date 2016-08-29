package ca.marklauman.dominionpicker;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of dominion cards. This only stores the card id-s.
 * @author Botond Xantus
 */
public class CardCollection {
    public final List<Long> cards;

    public CardCollection(List<Long> source) {
        cards = new ArrayList<>(source);
    }

    /**
     * creates an empty collection
     */
    public CardCollection() {
        cards = new ArrayList<>();
    }

    public CardCollection subCollection(int fromIndex, int toIndex) {
        return new CardCollection(cards.subList(fromIndex, toIndex));
    }
}
