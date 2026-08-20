package io.github.mpstudios56.cifra.report;

import io.github.mpstudios56.cifra.graph.GraphUnit;
import io.github.mpstudios56.cifra.model.Total;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 2/28/11 9:16 PM
 */
public class ReportData {

    public final List<GraphUnit> units;
    public final Total total;

    public ReportData(List<GraphUnit> units, Total total) {
        this.units = units;
        this.total = total;
    }

}
