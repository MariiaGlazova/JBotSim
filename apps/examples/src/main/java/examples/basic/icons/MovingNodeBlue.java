package examples.basic.icons;

public class MovingNodeBlue extends RandomMovingNode {
    @Override
    public void onStart() {
        super.onStart();
        setIcon("/io/jbotsim/ui/icons/circle-blue-32x32.png");
    }

}