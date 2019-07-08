/*
 * Copyright 2008 - 2019, Arnaud Casteigts and the JBotSim contributors <contact@jbotsim.io>
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
package io.jbotsim.core;

import io.jbotsim.core.Link.Mode;
import io.jbotsim.core.Link.Type;
import io.jbotsim.core.event.CommandListener;
import io.jbotsim.core.event.*;
import io.jbotsim.io.FileManager;
import io.jbotsim.io.TopologySerializer;
import io.jbotsim.io.format.plain.PlainTopologySerializer;

import java.util.*;

/**
 * <p>The {@link Topology} object is the main entry point of JBotSim.</p>
 *
 * It provides several features and convenience accessors, but at its core, it contains a set of {@link Node} objects
 * which can be linked two by two with a set of {@link Link} objects.
 */
public class Topology extends Properties implements ClockListener {
    public static final int DEFAULT_WIDTH = 600;
    public static final int DEFAULT_HEIGHT = 400;
    public static final double DEFAULT_COMMUNICATION_RANGE = 100;
    public static final double DEFAULT_SENSING_RANGE = 0;

    /**
     * The name under which the default Node model is stored.
     */
    public static final String DEFAULT_NODE_MODEL_NAME = "default";

    ClockManager clockManager;
    List<ConnectivityListener> cxUndirectedListeners = new ArrayList<>();
    List<ConnectivityListener> cxDirectedListeners = new ArrayList<>();
    List<TopologyListener> topologyListeners = new ArrayList<>();
    List<MovementListener> movementListeners = new ArrayList<>();
    List<MessageListener> messageListeners = new ArrayList<>();
    List<SelectionListener> selectionListeners = new ArrayList<>();
    List<StartListener> startListeners = new ArrayList<>();
    MessageEngine messageEngine = null;
    Scheduler scheduler;
    List<Node> nodes = new ArrayList<>();
    List<Link> arcs = new ArrayList<>();
    List<Link> edges = new ArrayList<>();
    HashMap<String, Class<? extends Node>> nodeModels = new HashMap<String, Class<? extends Node>>();
    boolean isWirelessEnabled = true;
    double communicationRange = DEFAULT_COMMUNICATION_RANGE;
    double sensingRange = DEFAULT_SENSING_RANGE;
    int width;
    int height;
    LinkResolver linkResolver = new LinkResolver();
    Node selectedNode = null;
    ArrayList<Node> toBeUpdated = new ArrayList<>();
    private boolean step = false;
    private boolean isStarted = false;
    private int nextID = 0;
    private FileManager fileManager = new FileManager();
    private TopologySerializer topologySerializer = new PlainTopologySerializer();


    public enum RefreshMode {CLOCKBASED, EVENTBASED}

    RefreshMode refreshMode = RefreshMode.EVENTBASED;

    /**
     * Creates a topology.
     */
    public Topology() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Creates a topology of given dimensions.
     * @param width the {@link Topology}'s width, as an integer.
     * @param height the {@link Topology}'s height, as an integer.
     */
    public Topology(int width, int height) {
        setMessageEngine(new MessageEngine(this));
        setScheduler(new Scheduler());
        setDimensions(width, height);
        clockManager = new ClockManager(this);
    }

    /**
     * Returns the node class corresponding to that name.
     * @param modelName a {@link String} identifying the node model.
     * @return the node model registered for the provided modelName.
     */
    public Class<? extends Node> getNodeModel(String modelName) {
        if(nodeModels == null || nodeModels.isEmpty())
            return Node.class;

        return nodeModels.get(modelName);
    }

    /**
     * Returns the default node model,
     * all properties assigned to this virtual node will be given to further nodes created
     * without explicit model name.
     * @return the default node model.
     */
    public Class<? extends Node> getDefaultNodeModel() {
        return getNodeModel(DEFAULT_NODE_MODEL_NAME);
    }

    /**
     * Adds the given node instance as a model.
     *
     * @param modelName a {@link String} identifying the node model.
     * @param nodeClass the node model: a {@link Class} object a class extending the {@link Node} class.
     */
    public void setNodeModel(String modelName, Class<? extends Node> nodeClass) {
        nodeModels.put(modelName, nodeClass);
    }

    /**
     * Sets the default node model to the given node instance.
     *
     * @param nodeClass the default node model: a {@link Class} object a class extending the {@link Node} class.
     */
    public void setDefaultNodeModel(Class<? extends Node> nodeClass) {
        setNodeModel(DEFAULT_NODE_MODEL_NAME, nodeClass);
    }

    /**
     * Returns the set registered node classes.
     * @return the {@link Set} of known model names.
     */
    public Set<String> getModelsNames() {
        return nodeModels.keySet();
    }

    /**
     * Create a new instance of this type of node.
     *
     * @param modelName a {@link String} identifying the node model.
     * @return a new instance of this type of node
     */
    public Node newInstanceOfModel(String modelName) {
        try {
            return getNodeModel(modelName).newInstance();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            System.err.println("(is your class of node public?)");
        } catch (NullPointerException e) {
            e.printStackTrace();
            System.err.println("(does your Node belong to a Topology?)");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Node();
    }

    public boolean isStarted() { // FIXME Ambiguous for the user
        return isStarted;
    }

    /**
     * Sets the updates (links, sensed objects, etc.) to be instantaneous (EVENTBASED),
     * or periodic after each round (CLOCKBASED).
     * @param refreshMode the {@link RefreshMode} to use.
     */
    public void setRefreshMode(RefreshMode refreshMode) {
        this.refreshMode = refreshMode;
    }

    /**
     * Returns the current refresh mode (CLOCKBASED or EVENTBASED).
     * @return the current {@link RefreshMode}.
     */
    public RefreshMode getRefreshMode() {
        return refreshMode;
    }

    /**
     * Enables this node's wireless capabilities.
     */
    public void enableWireless() {
        setWirelessStatus(true);
    }

    /**
     * Disables this node's wireless capabilities.
     */
    public void disableWireless() {
        setWirelessStatus(false);
    }

    /**
     * Set wireless capabilities status
     * @param enabled the new wireless status: <code>true</code> to enable,
     *         <code>false</code> otherwise.
     */
    public void setWirelessStatus(boolean enabled) {
        if (enabled == isWirelessEnabled)
            return;
        isWirelessEnabled = enabled;
        for (Node node : nodes)
            node.setWirelessStatus(enabled);
    }

    /**
     * Returns true if wireless links are enabled.
     * @return <code>true</code> if the wireless links are enabled,
     *         <code>false</code> otherwise.
     */
    public boolean getWirelessStatus() {
        return isWirelessEnabled;
    }

    /**
     * Returns the default communication range.
     *
     * @return the default communication range
     */
    public double getCommunicationRange() {
        return communicationRange;
    }

    /**
     * Sets the default communication range.
     * If the topology already has some nodes, their range is changed.
     *
     * @param communicationRange The communication range
     */
    public void setCommunicationRange(double communicationRange) {
        this.communicationRange = communicationRange;
        for (Node node : nodes)
            node.setCommunicationRange(communicationRange);
        setProperty("communicationRange", communicationRange); // for notification purpose
    }

    /**
     * Returns the default sensing range,
     *
     * @return the default sensing range
     */
    public double getSensingRange() {
        return sensingRange;
    }

    /**
     * Sets the default sensing range.
     * If the topology already has some nodes, their range is changed.
     *
     * @param sensingRange The sensing range
     */
    public void setSensingRange(double sensingRange) {
        this.sensingRange = sensingRange;
        for (Node node : nodes)
            node.setSensingRange(sensingRange);
        setProperty("sensingRange", sensingRange); // for notification purpose
    }

    /**
     * Gets a reference on the message engine of this topology.
     * @return the current {@link MessageEngine}.
     */
    public MessageEngine getMessageEngine() {
        return messageEngine;
    }

    /**
     * Sets the message engine of this topology.
     * @param messageEngine the new {@link MessageEngine}.
     */
    public void setMessageEngine(MessageEngine messageEngine) {
        this.messageEngine = messageEngine;
    }

    /**
     * Gets a reference on the scheduler.
     * @return the current {@link Scheduler}.
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Sets the scheduler of this topology.
     * @param scheduler the new {@link Scheduler}.
     */
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Returns the global duration of a round in this topology (in millisecond).
     *
     * @return The duration
     */
    public int getTimeUnit() {
        return clockManager.getTimeUnit();
    }

    /**
     * Sets the global duration of a round in this topology (in millisecond).
     *
     * @param period The desired duration
     */
    public void setTimeUnit(int period) {
        clockManager.setTimeUnit(period);
    }

    /**
     * Returns the clock model currently in use.
     * @return the current clock model.
     */
    public Class<? extends Clock> getClockModel() {
        return clockManager.getClockModel();
    }

    /**
     * Sets the clock model (to be instantiated automatically).
     *
     * @param clockModel A class that extends JBotSim's abstract Clock
     */
    public void setClockModel(Class<? extends Clock> clockModel) {
        clockManager.setClockModel(clockModel);
    }

    /**
     * Returns the current time (current round number)
     * @return the current time.
     */
    public int getTime() {
        return clockManager.currentTime();
    }

    /**
     * Indicates whether the internal clock is currently running or in pause.
     *
     * @return <code>true</code> if running, <code>false</code> if paused.
     */
    public boolean isRunning() {
        return clockManager.isRunning();
    }

    /**
     * Pauses the clock (or increments the pause counter).
     */
    public void pause() {
        clockManager.pause();
    }

    /**
     * Resumes the clock (or decrements the pause counter).
     */
    public void resume() {
        clockManager.resume();
    }

    /**
     * Reset the round number to 0.
     */
    public void resetTime() {
        clockManager.reset();
    }

    /**
     * Sets the topology dimensions as indicated.
     * @param width the {@link Topology}'s width, as an integer.
     * @param height the {@link Topology}'s height, as an integer.
     */
    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the width of this topology.
     * @return the width, as an integer.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of this topology.
     * @return the height, as an integer.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Initializes the clock.
     */
    public void start() {
        clockManager.start();
        isStarted = true;
        restart();
    }

    /**
     * (Re)init the nodes through their onStart() method (and notifies StartListeners as well)
     */
    public void restart() {
        pause();
        resetTime();
        clearMessages();
        for (Node n : nodes)
            n.onStart();
        for (StartListener listener : startListeners)
            listener.onStart();
        resume();
    }

    /**
     * Removes all the nodes (and links) of this topology.
     */
    public void clear() {
        while (!nodes.isEmpty())
            removeNode(nodes.get(nodes.size() - 1));
        nextID = 0;
    }

    /**
     * Removes all the links of this topology.
     */
    public void clearLinks() {
        while (!edges.isEmpty())
            removeLink(edges.get(edges.size() - 1));
    }

    /**
     * Removes all the ongoing messages in this topology.
     */
    public void clearMessages() {
        for (Node n : nodes) {
            n.sendQueue.clear();
            n.mailBox.clear();
        }
    }

    /**
     * Performs a single round, then switch to pause state.
     */
    public void step() {
        resume();
        step = true;

        if(!isStarted())
            start();
    }

    /**
     * Adds the specified node to this topology. The location of the node
     * in the topology will be its current inherent location (or <code>(0,0)</code>
     * if no location was prealably given to it).
     *
     * @param n The node to be added.
     */
    public void addNode(Node n) {
        addNode(n.getX(), n.getY(), n);
    }

    /**
     * Adds a new node to this topology at the specified location.
     *
     * @param x The abscissa of the location.
     * @param y The ordinate of the location.
     */
    public void addNode(double x, double y) {
        addNode(x, y, newInstanceOfModel(DEFAULT_NODE_MODEL_NAME));
    }

    /**
     * Adds the specified node to this topology at the specified location.
     *
     * @param x The abscissa of the location.
     * @param y The ordinate of the location.
     * @param n The node to be added.
     */
    public void addNode(double x, double y, Node n) {
        pause();
        if (x == -1)
            x = Math.random() * width;
        if (y == -1)
            y = Math.random() * height;
        if (n.getX() == 0 && n.getY() == 0)
            n.setLocation(x, y);

        if (n.communicationRange == null)
            n.setCommunicationRange(communicationRange);
        if (n.sensingRange == null)
            n.setSensingRange(sensingRange);
        if (isWirelessEnabled == false)
            n.disableWireless();
        if (n.getID() == -1)
            n.setID(nextID++);
        nodes.add(n);
        n.topo = this;
        notifyNodeAdded(n);
        if (isStarted)
            n.onStart();
        touch(n);
        resume();
    }

    /**
     * Removes the specified node from this topology. All adjacent links will
     * be automatically removed.
     *
     * @param n The node to be removed.
     */
    public void removeNode(Node n) {
        pause();
        n.onStop();
        for (Link l : n.getLinks(true))
            removeLink(l);
        notifyNodeRemoved(n);
        nodes.remove(n);
        for (Node n2 : nodes) {
            if (n2.sensedNodes.contains(n)) {
                n2.sensedNodes.remove(n);
                n2.onSensingOut(n);
            }
        }
        n.topo = null;
        resume();
    }

    /**
     * Selects the specified {@link Node} in this {@link Topology}.
     *
     * @param n The {@link Node} to be selected.
     */
    public void selectNode(Node n) {
        selectedNode = n;
        n.onSelection();
        notifyNodeSelected(n);
    }

    /**
     * Adds the specified link to this topology. Calling this method makes
     * sense only for wired links, since wireless links are automatically
     * managed as per the nodes' communication ranges.
     *
     * @param l The link to be added.
     */
    public void addLink(Link l) {
        addLink(l, false);
    }

    /**
     * Adds the specified link to this topology without notifying the listeners
     * (if silent is true). Calling this method makes sense only for wired
     * links, since wireless links are automatically managed as per the nodes'
     * communication ranges.
     *
     * @param l The link to be added.
     * @param silent <code>true</code> to disable notifications of this adding.
     */
    public void addLink(Link l, boolean silent) {
        if (l.type == Type.DIRECTED) {
            arcs.add(l);
            l.source.outLinks.put(l.destination, l);
            if (l.destination.outLinks.containsKey(l.source)) {
                Link edge = new Link(l.source, l.destination, Type.UNDIRECTED, l.mode);
                edges.add(edge);
                if (!silent)
                    notifyLinkAdded(edge);
            }
        } else { // UNDIRECTED
            Link arc1 = l.source.outLinks.get(l.destination);
            Link arc2 = l.destination.outLinks.get(l.source);
            if (arc1 == null) {
                arc1 = new Link(l.source, l.destination, Type.DIRECTED);
                arcs.add(arc1);
                arc1.source.outLinks.put(arc1.destination, arc1);
                if (!silent)
                    notifyLinkAdded(arc1);
            } else {
                arc1.mode = l.mode;
            }
            if (arc2 == null) {
                arc2 = new Link(l.destination, l.source, Type.DIRECTED);
                arcs.add(arc2);
                arc2.source.outLinks.put(arc2.destination, arc2);
                if (!silent)
                    notifyLinkAdded(arc2);
            } else {
                arc2.mode = l.mode;
            }
            edges.add(l);
        }
        if (!silent)
            notifyLinkAdded(l);
    }

    /**
     * Removes the specified link from this topology. Calling this method makes
     * sense only for wired links, since wireless links are automatically
     * managed as per the nodes' communication ranges.
     *
     * @param l The link to be removed.
     */
    public void removeLink(Link l) {
        if (l.type == Type.DIRECTED) {
            arcs.remove(l);
            l.source.outLinks.remove(l.destination);
            Link edge = getLink(l.source, l.destination, false);
            if (edge != null) {
                edges.remove(edge);
                notifyLinkRemoved(edge);
            }
        } else {
            Link arc1 = getLink(l.source, l.destination, true);
            Link arc2 = getLink(l.destination, l.source, true);
            arcs.remove(arc1);
            arc1.source.outLinks.remove(arc1.destination);
            notifyLinkRemoved(arc1);
            arcs.remove(arc2);
            arc2.source.outLinks.remove(arc2.destination);
            notifyLinkRemoved(arc2);
            edges.remove(l);
        }
        notifyLinkRemoved(l);
    }

    /**
     * Returns true if this topology has at least one directed link.
     * @return <code>true</code> if the {@link Topology} has at least one directed link, <code>false</code> otherwise.
     */
    public boolean hasDirectedLinks() {
        return arcs.size() > 2 * edges.size();
    }

    /**
     * Returns a list containing all the nodes in this topology. The returned
     * ArrayList can be subsequently modified without effect on the topology.
     * @return the {@link List} of {@link Node}s.
     */
    public List<Node> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * Returns the first node found with this ID.
     * @param id an integer identifying the {@link Node}.
     * @return the corresponding {@link Node}, null if not found.
     */
    public Node findNodeById(int id) {
        for (Node node : nodes)
            if (node.getID() == id)
                return node;
        return null;
    }

    /**
     * Shuffles the IDs of the nodes in this topology.
     */
    public void shuffleNodeIds() {
        List<Integer> Ids = new ArrayList<>();
        for (Node node : nodes)
            Ids.add(node.getID());
        Collections.shuffle(Ids);
        for (int i = 0; i < nodes.size(); i++)
            nodes.get(i).setID(Ids.get(i));
    }

    /**
     * Returns a list containing all undirected links in this topology. The
     * returned ArrayList can be subsequently modified without effect on the
     * topology.
     * @return the {@link List} of {@link Link}s.
     */
    public List<Link> getLinks() {
        return getLinks(false);
    }

    /**
     * Returns a list containing all links of the specified type in this
     * topology. The returned ArrayList can be subsequently modified without
     * effect on the topology.
     *
     * @param directed <code>true</code> for directed links, <code>false</code> for
     *                 undirected links.
     * @return the {@link List} of {@link Link}s.
     */
    public List<Link> getLinks(boolean directed) {
        return new ArrayList<>(directed ? arcs : edges);
    }

    List<Link> getLinks(boolean directed, Node n, int pos) {
        List<Link> result = new ArrayList<>();
        List<Link> allLinks = (directed) ? arcs : edges;
        for (Link l : allLinks)
            switch (pos) {
                case 0:
                    if (l.source == n || l.destination == n)
                        result.add(l);
                    break;
                case 1:
                    if (l.source == n)
                        result.add(l);
                    break;
                case 2:
                    if (l.destination == n)
                        result.add(l);
                    break;
            }
        return result;
    }

    /**
     * Returns the undirected link shared the specified nodes, if any.
     *
     * @param n1 the first {@link Node}.
     * @param n2 the second {@link Node}.
     * @return The requested link, if such a link exists, <code>null</code>
     * otherwise.
     */
    public Link getLink(Node n1, Node n2) {
        return getLink(n1, n2, false);
    }

    /**
     * Returns the link of the specified type between the specified nodes, if
     * any.
     * @param from the source {@link Node}.
     * @param to the destination {@link Node}.
     * @param directed <code>true</code> if the searched {@link Link} is directed, <code>false</code> otherwise.
     *
     * @return The requested link, if such a link exists, <code>null</code>
     * otherwise.
     */
    public Link getLink(Node from, Node to, boolean directed) {
        if (directed) {
            return from.outLinks.get(to);
            //Link l=new Link(from, to,Link.Type.DIRECTED);
            //int pos=arcs.indexOf(l);
            //return (pos != -1)?arcs.get(pos):null;
        } else {
            Link l = new Link(from, to, Type.UNDIRECTED);
            int pos = edges.indexOf(l);
            return (pos != -1) ? edges.get(pos) : null;
        }
    }

    /**
     * Replaces the default Wireless Link Resolver by a custom one.
     *
     * @param linkResolver An object that implements LinkResolver.
     */
    public void setLinkResolver(LinkResolver linkResolver) {
        this.linkResolver = linkResolver;
    }

    /**
     * Return the current LinkResolver.
     * @return the current {@link LinkResolver}.
     */
    public LinkResolver getLinkResolver() {
        return linkResolver;
    }

    /**
     * Registers the specified topology listener to this topology. The listener
     * will be notified whenever an undirected link is added or removed.
     *
     * @param listener The listener to add.
     */
    public void addConnectivityListener(ConnectivityListener listener) {
        addConnectivityListener(listener, false);
    }

    /**
     * Registers the specified connectivity listener to this topology. The
     * listener will be notified whenever a link of the specified type is
     * added or removed.
     *
     * @param listener The listener to register.
     * @param directed The type of links to be listened (<code>true</code> for
     *                 directed, <code>false</code> for undirected).
     */
    public void addConnectivityListener(ConnectivityListener listener, boolean directed) {
        if (directed)
            cxDirectedListeners.add(listener);
        else
            cxUndirectedListeners.add(listener);
    }

    /**
     * Unregisters the specified connectivity listener from the 'undirected'
     * listeners.
     *
     * @param listener The listener to unregister.
     */
    public void removeConnectivityListener(ConnectivityListener listener) {
        removeConnectivityListener(listener, false);
    }

    /**
     * Unregisters the specified connectivity listener from the listeners
     * of the specified type.
     *
     * @param listener The listener to unregister.
     * @param directed The type of links that this listener was listening
     *                 (<code>true</code> for directed, <code>false</code> for undirected).
     */
    public void removeConnectivityListener(ConnectivityListener listener, boolean directed) {
        if (directed)
            cxDirectedListeners.remove(listener);
        else
            cxUndirectedListeners.remove(listener);
    }

    /**
     * Registers the specified movement listener to this topology. The
     * listener will be notified every time the location of a node changes.
     *
     * @param listener The movement listener.
     */
    public void addMovementListener(MovementListener listener) {
        movementListeners.add(listener);
    }

    /**
     * Unregisters the specified movement listener for this topology.
     *
     * @param listener The movement listener.
     */
    public void removeMovementListener(MovementListener listener) {
        movementListeners.remove(listener);
    }

    /**
     * Registers the specified topology listener to this topology. The listener
     * will be notified whenever a node is added or removed.
     *
     * @param listener The listener to register.
     */
    public void addTopologyListener(TopologyListener listener) {
        topologyListeners.add(listener);
    }

    /**
     * Unregisters the specified topology listener.
     *
     * @param listener The listener to unregister.
     */
    public void removeTopologyListener(TopologyListener listener) {
        topologyListeners.remove(listener);
    }

    /**
     * Registers the specified message listener to this topology. The listener
     * will be notified every time a message is received at any node.
     *
     * @param listener The message listener.
     */
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    /**
     * Unregisters the specified message listener for this topology.
     *
     * @param listener The message listener.
     */
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    /**
     * Registers the specified selection listener to this topology. The listener
     * will be notified every time a node is selected.
     *
     * @param listener The selection listener.
     */
    public void addSelectionListener(SelectionListener listener) {
        selectionListeners.add(listener);
    }

    /**
     * Unregisters the specified selection listener for this topology.
     *
     * @param listener The selection listener.
     */
    public void removeSelectionListener(SelectionListener listener) {
        selectionListeners.remove(listener);
    }

    /**
     * Registers the specified start listener to this topology. The listener
     * will be notified every time a (re)start is requested on the topology.
     *
     * @param listener The start listener.
     */
    public void addStartListener(StartListener listener) {
        startListeners.add(listener);
    }

    /**
     * Unregisters the specified selection listener for this topology.
     *
     * @param listener The start listener.
     */
    public void removeStartListener(StartListener listener) {
        startListeners.remove(listener);
    }

    /**
     * Registers the specified listener to the events of the clock.
     *
     * @param listener The listener to register.
     * @param period   The number of rounds between consecutive onClock() events,
     *                 in time units.
     */
    public void addClockListener(ClockListener listener, int period) {
        clockManager.addClockListener(listener, period);
    }

    /**
     * Registers the specified listener to the events of the clock.
     *
     * @param listener The listener to register.
     */
    public void addClockListener(ClockListener listener) {
        clockManager.addClockListener(listener);
    }

    /**
     * Unregisters the specified listener. (The <code>onClock()</code> method of this
     * listener will not longer be called.)
     *
     * @param listener The listener to unregister.
     */
    public void removeClockListener(ClockListener listener) {
        clockManager.removeClockListener(listener);
    }

    public void setFileManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    /**
     * Provides a {@link TopologySerializer}.
     * @return a {@link TopologySerializer}.
     */
    public TopologySerializer getSerializer() {
        return topologySerializer;
    }

    /**
     *  Sets the new {@link TopologySerializer} to use.
     * @param topologySerializer the new {@link TopologySerializer} to use.
     */
    public void setSerializer(TopologySerializer topologySerializer) {
        this.topologySerializer = topologySerializer;
    }

    protected void notifyLinkAdded(Link l) {
        List<ConnectivityListener> listeners;
        if (l.type == Type.DIRECTED) {
            l.endpoint(0).onDirectedLinkAdded(l);
            l.endpoint(1).onDirectedLinkAdded(l);
            listeners = cxDirectedListeners;
        } else {
            l.endpoint(0).onLinkAdded(l);
            l.endpoint(1).onLinkAdded(l);
            listeners = cxUndirectedListeners;
        }
        for (ConnectivityListener cl : listeners)
            cl.onLinkAdded(l);
    }

    protected void notifyLinkRemoved(Link l) {
        List<ConnectivityListener> listeners;
        if (l.type == Type.DIRECTED) {
            l.endpoint(0).onDirectedLinkRemoved(l);
            l.endpoint(1).onDirectedLinkRemoved(l);
            listeners = cxDirectedListeners;
        } else {
            l.endpoint(0).onLinkRemoved(l);
            l.endpoint(1).onLinkRemoved(l);
            listeners = cxUndirectedListeners;
        }
        for (ConnectivityListener cl : listeners)
            cl.onLinkRemoved(l);
    }

    protected void notifyNodeAdded(Node node) {
        for (TopologyListener tl : new ArrayList<>(topologyListeners))
            tl.onNodeAdded(node);
    }

    protected void notifyNodeRemoved(Node node) {
        for (TopologyListener tl : new ArrayList<>(topologyListeners))
            tl.onNodeRemoved(node);
    }

    protected void notifyNodeSelected(Node node) {
        for (SelectionListener tl : new ArrayList<>(selectionListeners))
            tl.onSelection(node);
    }

    @Override
    public void onClock() {
        if (step) {
            pause();
            step = false;
        }
        if (refreshMode == RefreshMode.CLOCKBASED) {
            for (Node node : toBeUpdated)
                update(node);
            toBeUpdated.clear();
        }

        removeDyingNodes();
    }

    private void removeDyingNodes() {
        List<Node> dyingNodes = new ArrayList<>();
        for (Node node : nodes)
            if(node.isDying())
                dyingNodes.add(node);

        for (Node node : dyingNodes)
            removeNode(node);

    }

    void touch(Node n) {
        if (refreshMode == RefreshMode.CLOCKBASED)
            toBeUpdated.add(n);
        else
            update(n);
    }

    void update(Node n) {
        for (Node n2 : nodes)
            if (n2 != n) {
                updateWirelessLink(n, n2);
                updateWirelessLink(n2, n);
            }
        for (Node n2 : new ArrayList<>(nodes)) {
            if (n2 != n) {
                updateSensedNodes(n, n2);
                updateSensedNodes(n2, n);
            }
        }
    }

    void updateWirelessLink(Node n1, Node n2) {
        Link l = n1.getOutLinkTo(n2);
        boolean linkExisted = (l == null) ? false : true;
        boolean linkExists = linkResolver.isHeardBy(n1, n2);
        if (!linkExisted && linkExists)
            addLink(new Link(n1, n2, Type.DIRECTED, Mode.WIRELESS));
        else if (linkExisted && l.isWireless() && !linkExists)
            removeLink(l);
    }

    void updateSensedNodes(Node from, Node to) {
        if (from.distance(to) < from.sensingRange) {
            if (!from.sensedNodes.contains(to)) {
                from.sensedNodes.add(to);
                from.onSensingIn(to);
            }
        } else if (from.sensedNodes.contains(to)) {
            from.sensedNodes.remove(to);
            from.onSensingOut(to);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }

    // region Command management

    protected ArrayList<CommandListener> commandListeners = new ArrayList<>();
    protected ArrayList<String> commands = new ArrayList<String>();
    protected boolean defaultCommandsEnabled = true;

    /**
     * Registers the specified action listener to this {@link Topology}.
     *
     * @param al The listener to add.
     */
    public void addCommandListener(CommandListener al) {
        commandListeners.add(al);
    }

    /**
     * Unregisters the specified action listener to this {@link Topology}.
     *
     * @param al The listener to remove.
     */
    public void removeCommandListener(CommandListener al) {
        commandListeners.remove(al);
    }

    /**
     * Adds the specified action command to this {@link Topology}.
     *
     * @param command The command name to add.
     */
    public void addCommand(String command) {
        ((List<String>) commands).add(command);
    }

    /**
     * Removes the specified action command from this {@link Topology}.
     *
     * @param command The command name to remove.
     */
    public void removeCommand(String command) {
        commands.remove(command);
    }

    /**
     * Disables the set of default commands provided by the {@link Topology} when using {@link #getCommands()}.
     */
    public void disableDefaultCommands() {
        defaultCommandsEnabled = false;
    }

    /**
     * Enables the set of default commands provided by the {@link Topology} when using {@link #getCommands()}.
     */
    public void enableDefaultCommands() {
        defaultCommandsEnabled = true;
    }

    /**
     * <p>Recompute the current list of commands.</p>
     * <p>This list contains:</p>
     * <ul>
     *     <li>The list of default commands managed by the {@link Topology}.
     *     Please use {@link #disableDefaultCommands()} to if you dont want them.</li>
     *     <li>The list of commands which have been added by {@link #addCommand(String)}.</li>
     * </ul>
     * @return the current list of commands
     */
    public Iterable<String> getCommands() {
        List<String> retList = new ArrayList<>();
        retList = addDefaultCommands(retList);
        retList.addAll(commands);
        return retList;
    }

    private List<String> addDefaultCommands(List<String> commands) {
        if(!defaultCommandsEnabled)
            return commands;

        if(!isStarted()) {
            commands.add(DefaultCommands.START_EXECUTION);
            commands.add(DefaultCommands.EXECUTE_A_SINGLE_STEP);
        } else {
            if (isRunning())
                commands.add(DefaultCommands.PAUSE_EXECUTION);
            else {
                commands.add(DefaultCommands.RESUME_EXECUTION);
                commands.add(DefaultCommands.EXECUTE_A_SINGLE_STEP);
            }
            commands.add(DefaultCommands.RESTART_NODES);
        }

        commands.add(COMMAND_SEPARATOR);
        return commands;
    }

    /**
     * The character used as command separator.
     */
    public static final String COMMAND_SEPARATOR = "-";

    public class DefaultCommands {
        public static final String START_EXECUTION = "Start execution";
        public static final String PAUSE_EXECUTION = "Pause execution";
        public static final String RESUME_EXECUTION = "Resume execution";
        public static final String EXECUTE_A_SINGLE_STEP = "Execute a single step";
        public static final String RESTART_NODES = "Restart nodes";
    }

    /**
     * Removes all commands from this {@link Topology}.
     */
    public void removeAllCommands() {
        commands.clear();
    }

    /**
     * Executes a command.
     *
     * @param command the command to be executed
     */
    public void executeCommand(String command) {

        if(defaultCommandsEnabled) {
            if (command.equals(DefaultCommands.START_EXECUTION)) {
                if (!isStarted())
                    start();
            } else if (command.equals(DefaultCommands.PAUSE_EXECUTION)) {
                if (isStarted() && isRunning())
                    pause();
            } else if (command.equals(DefaultCommands.RESUME_EXECUTION)) {
                if (isStarted() && !isRunning())
                    resume();
            } else if (command.equals(DefaultCommands.RESTART_NODES)) {
                if (isStarted())
                    restart();
            } else if (command.equals(DefaultCommands.EXECUTE_A_SINGLE_STEP)) {
                if (!isStarted() || (isStarted() && !isRunning()))
                    step();
            }
        }

        for (CommandListener cl : commandListeners)
            cl.onCommand(command);
    }

    // endregion
}
