package jbotsimx.dygraph;

import jbotsim.Node;
import jbotsim.Topology;
import jbotsim.event.ClockListener;

import java.util.Random;

public class EMEGPlayer implements ClockListener{
    protected TVG tvg;
    protected Topology tp;
    protected double birthRate, deathRate, steadyProb;
    /**
     * Plays the specified _demo.J_tvg as an EMEG on the specified topology.
     * @param tvg the time-varying graph
     * @param tp the target topology
     * @param birthRate the desired birth rate
     * @param deathRate the desired death rate
     */
    public EMEGPlayer (TVG tvg, Topology tp, double birthRate, double deathRate){
        this.tvg=tvg;
        this.tp=tp;
        this.birthRate=birthRate;
        this.deathRate=deathRate;
        this.steadyProb = birthRate/(birthRate+deathRate);
        for (Node n : tvg.nodes)
            tp.addNode(n);
    }
    public void start(){
        tp.resetTime();
        tp.addClockListener(this);
        Random r = new Random();
        for (TVLink l : tvg.tvlinks)
            if (r.nextDouble() < steadyProb)
                tp.addLink(l);
    }
    public void onClock(){
        updateLinks();
    }
    protected void updateLinks(){
        Random r = new Random();
        for (TVLink l : tvg.tvlinks){
            if (tp.getLinks().contains(l) && r.nextDouble() < deathRate)
                tp.removeLink(l);
            else if (!tp.getLinks().contains(l) && r.nextDouble() < birthRate)
                tp.addLink(l);
        }        
    }
}
