package io.jbotsim.ui.painting;

import io.jbotsim.core.Link;

public interface LinkPainter {
    /**
     * Paints the Links.
     *
     * @param g2d  The graphics object
     * @param link The link to be drawn
     */
    void paintLink(UIComponent g2d, Link link);
}
