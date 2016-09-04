package ca.marklauman.dominionpicker;

import android.content.Context;
import android.support.v4.content.CursorLoader;
import ca.marklauman.dominionpicker.database.Provider;
import ca.marklauman.dominionpicker.database.TableCard;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.dominionpicker.userinterface.recyclerview.AdapterCardsDismiss;
import ca.marklauman.tools.Utils;

import java.util.ArrayList;
import java.util.Arrays;
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
    public CardCollection(long[] source) {
        cards = new ArrayList<>(source.length);
        for (long card : source) cards.add(card);
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

    // some utility methods with collections
    public static CursorLoader createLoader(CardCollection coll, Context ctx) {
        // Basic loader
        CursorLoader c = new CursorLoader(ctx);
        c.setUri(Provider.URI_CARD_ALL);
        c.setProjection(AdapterCardsDismiss.COLS_USED);
        c.setSortOrder(Pref.cardSort(ctx));

        // Selection string (sql WHERE clause)
        // _id IN (1,2,3,4)
        String cards = TableCard._ID+" IN ("+ Utils.join(",",coll.cards)+")";
        c.setSelection("("+cards+") AND "+ Pref.languageFilter(ctx));

        return c;
    }
}
