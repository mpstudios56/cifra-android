package io.github.mpstudios56.cifra.model;

import static io.github.mpstudios56.cifra.db.DatabaseHelper.LOCATIONS_TABLE;
import static io.github.mpstudios56.cifra.orb.EntityManager.DEF_SORT_COL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Where a movement happened.
 * <p>
 * A place is kept both as a point on the earth and as an address in words: the
 * point is what the phone knows, the address is what a person reads a year
 * later. The two are written down together because looking the address up again
 * needs the network, and the network is not always there.
 * <p>
 * How many movements have been written at a place is counted, so that the ones
 * used most often can be offered first.
 */
@Entity
@Table(name = LOCATIONS_TABLE)
public class MyLocation extends MyEntity implements SortableEntity {

    /**
     * Wherever the phone happens to be at the moment of writing.
     * <p>
     * Not a place but a standing instruction: it is resolved when the movement
     * is saved, not when this is chosen.
     */
    public static final int CURRENT_LOCATION_ID = 0;

    public static MyLocation currentLocation() {
        MyLocation location = new MyLocation();
        location.id = CURRENT_LOCATION_ID;
        location.title = "<CURRENT_LOCATION>";
        location.provider = location.resolvedAddress = "?";
        return location;
    }

    /** What told the phone where it was: the satellites, or the network. */
    @Column(name = "provider")
    public String provider;

    /** How near the truth that answer claims to be, in metres. */
    @Column(name = "accuracy")
    public float accuracy;

    @Column(name = "longitude")
    public double longitude;

    @Column(name = "latitude")
    public double latitude;

    /** The same point written as a street and a town. */
    @Column(name = "resolved_address")
    public String resolvedAddress;

    /** When the point was taken. */
    @Column(name = "datetime")
    public long dateTime;

    /** How many movements have been written down here. */
    @Column(name = "count")
    public int count;

    /** Where it sits in the list, when the list is arranged by hand. */
    @Column(name = DEF_SORT_COL)
    public long sortOrder;

    @Override
    public long getSortOrder() {
        return sortOrder;
    }
}
