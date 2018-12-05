package examples.basic.moving;

import io.jbotsim.core.Topology;
import io.jbotsim.ui.JViewer;

/**
 * Created by acasteig on 25/08/15.
 */
public class Main {
    public static void main(String[] args) {
        Topology tp = new Topology();
        tp.setDefaultNodeModel(MovingNode.class);
        new JViewer(tp);
    }
}