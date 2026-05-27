package com.sob.musicviz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

@Singleton
class ObjectScanner
{
    private final Client client;

    private volatile List<TileObject> snapshot = Collections.emptyList();

    @Inject
    ObjectScanner(Client client)
    {
        this.client = client;
    }

    void refresh(int radius)
    {
        Player local = client.getLocalPlayer();
        if (local == null)
        {
            snapshot = Collections.emptyList();
            return;
        }

        WorldPoint center = local.getWorldLocation();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();

        List<TileObject> found = new ArrayList<>();
        Tile[][] planeTiles = tiles[plane];

        int radSq = radius * radius;
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();

        for (int sx = 0; sx < planeTiles.length; sx++)
        {
            Tile[] row = planeTiles[sx];
            for (int sy = 0; sy < row.length; sy++)
            {
                Tile tile = row[sy];
                if (tile == null) continue;

                int worldX = baseX + sx;
                int worldY = baseY + sy;
                int dx = worldX - center.getX();
                int dy = worldY - center.getY();
                if (dx * dx + dy * dy > radSq) continue;

                GameObject[] objs = tile.getGameObjects();
                if (objs == null) continue;
                for (GameObject obj : objs)
                {
                    if (obj == null) continue;
                    found.add(obj);
                }
            }
        }

        snapshot = found;
    }

    List<TileObject> get()
    {
        return snapshot;
    }
}
