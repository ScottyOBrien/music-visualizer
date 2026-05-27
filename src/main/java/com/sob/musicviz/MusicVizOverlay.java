package com.sob.musicviz;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class MusicVizOverlay extends Overlay
{
    private final Client client;
    private final MusicVizConfig config;
    private final FlashStore flashes;

    @Inject
    MusicVizOverlay(Client client, MusicVizConfig config, FlashStore flashes)
    {
        this.client = client;
        this.config = config;
        this.flashes = flashes;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        long now = System.currentTimeMillis();
        int decay = config.flashDecayMs();
        int peakAlpha = config.colorAlpha();

        Stroke prev = g.getStroke();
        g.setStroke(new BasicStroke(2f));

        flashes.forEachActive(now, decay, flash -> drawFlash(g, flash, now, decay, peakAlpha));

        g.setStroke(prev);
        return null;
    }

    private void drawFlash(Graphics2D g, FlashState flash, long now, int decay, int peakAlpha)
    {
        TileObject obj = flash.target;
        Shape hull = obj.getConvexHull();
        if (hull == null) return;

        float p = flash.progress(now, decay);
        int alpha = (int) (peakAlpha * (1f - p));
        if (alpha <= 0) return;

        Color base = flash.color;
        Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        Color stroke = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, alpha + 60));

        g.setColor(fill);
        g.fill(hull);
        g.setColor(stroke);
        g.draw(hull);
    }
}
