/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.utils;

import androidx.annotation.DrawableRes;

import io.github.mpstudios56.cifra.R;

/**
 * A symbol chosen for a category.
 * <p>
 * Stored the same way an account's is: a tag behind a marker no emoji begins
 * with, so anything without the marker is still plain text and is shown as
 * written. Somebody who would rather type an emoji still can.
 * <p>
 * The symbols carry no names of their own. They are chosen from a grid by
 * looking at them, the way one picks a symbol, and naming forty-four of them
 * in every language the app speaks would be a great deal of words nobody reads.
 */
public enum CategoryIcon {

    // What one eats and drinks
    RESTAURANT("restaurant", R.drawable.category_restaurant),
    GROCERIES("groceries", R.drawable.category_groceries),
    COFFEE("coffee", R.drawable.category_coffee),
    DRINKS("drinks", R.drawable.category_drinks),

    // Getting about
    CAR("car", R.drawable.category_car),
    FUEL("fuel", R.drawable.category_fuel),
    TRANSIT("transit", R.drawable.category_transit),
    FLIGHT("flight", R.drawable.category_flight),
    BIKE("bike", R.drawable.category_bike),

    // Where one lives
    HOME("home", R.drawable.category_home),
    RENT("rent", R.drawable.category_rent),
    TOOLS("tools", R.drawable.category_tools),
    FURNITURE("furniture", R.drawable.category_furniture),

    // The bills
    ELECTRICITY("electricity", R.drawable.category_electricity),
    WATER("water", R.drawable.category_water),
    GAS("gas", R.drawable.category_gas),
    INTERNET("internet", R.drawable.category_internet),
    PHONE("phone", R.drawable.category_phone),
    SUBSCRIPTION("subscription", R.drawable.category_subscription),

    // Spending
    SHOPPING("shopping", R.drawable.category_shopping),
    CLOTHES("clothes", R.drawable.category_clothes),
    GIFT("gift", R.drawable.category_gift),
    DEVICE("device", R.drawable.category_device),

    // Looking after oneself
    HEALTH("health", R.drawable.category_health),
    PHARMACY("pharmacy", R.drawable.category_pharmacy),
    FITNESS("fitness", R.drawable.category_fitness),
    BEAUTY("beauty", R.drawable.category_beauty),

    // Time off
    ENTERTAINMENT("entertainment", R.drawable.category_entertainment),
    MUSIC("music", R.drawable.category_music),
    SPORT("sport", R.drawable.category_sport),
    TRAVEL("travel", R.drawable.category_travel),
    BOOK("book", R.drawable.category_book),
    GAMES("games", R.drawable.category_games),

    // The household
    FAMILY("family", R.drawable.category_family),
    PET("pet", R.drawable.category_pet),
    EDUCATION("education", R.drawable.category_education),

    // Money coming and going
    SALARY("salary", R.drawable.category_salary),
    SAVINGS("savings", R.drawable.category_savings),
    INVESTMENT("investment", R.drawable.category_investment),
    TAXES("taxes", R.drawable.category_taxes),
    INSURANCE("insurance", R.drawable.category_insurance),
    FEES("fees", R.drawable.category_fees),
    CHARITY("charity", R.drawable.category_charity),

    OTHER("other", R.drawable.category_other);

    /** The same marker accounts use, so the two fields read the same way. */
    public static final String MARKER = "@";

    public final String tag;
    @DrawableRes public final int iconId;

    CategoryIcon(String tag, int iconId) {
        this.tag = tag;
        this.iconId = iconId;
    }

    public String toStoredValue() {
        return MARKER + tag;
    }

    /** The symbol this category's icon field names, or null when it holds text. */
    public static CategoryIcon parse(String stored) {
        if (stored == null || !stored.startsWith(MARKER)) {
            return null;
        }
        String tag = stored.substring(MARKER.length());
        for (CategoryIcon icon : values()) {
            if (icon.tag.equals(tag)) {
                return icon;
            }
        }
        // Written by a later version that knows a symbol this one does not.
        return null;
    }
}
