/*
 * Copyright 2008 - 2020, Arnaud Casteigts and the JBotSim contributors <contact@jbotsim.io>
 *
 *
 * This file is part of JBotSim.
 *
 * JBotSim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JBotSim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JBotSim.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package examples.misc.randomwalks;

import io.jbotsim.core.Node;
import io.jbotsim.core.Topology;
import io.jbotsim.core.event.ClockListener;
import io.jbotsim.ui.JViewer;
import io.jbotsim.gen.basic.TopologyGenerators;

import io.jbotsim.core.Color;
import java.util.List;
import java.util.Random;

/**
 * Created by acasteig on 17/06/15.
 */
public class CentralizedRW1 implements ClockListener {
    Random random = new Random();
    Node current;

    public CentralizedRW1(Topology tp) {
        tp.addClockListener(this);
        current = tp.getNodes().get(0);
        current.setColor(Color.black);
    }

    @Override
    public void onClock() {
        List<Node> neighbors = current.getNeighbors();
        current.setColor(null);
        current = neighbors.get(random.nextInt(neighbors.size()));
        current.setColor(Color.black);
    }

    public static void main(String[] args) {
        Topology tp = new Topology();
        TopologyGenerators.generateLine(tp, 10);
        new CentralizedRW1(tp);
        new JViewer(tp);
        tp.start();
    }
}
