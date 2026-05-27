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
import net.runelite.api.coords.LocalPoint;

@Singleton
class ObjectScanner
{
    private final Client client;

    private volatile List<GameObject> snapshot = Collections.emptyList();

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

        LocalPoint playerLoc = local.getLocalLocation();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        Tile[][] planeTiles = tiles[plane];

        int radiusUnits = radius * 128;
        long radSq = (long) radiusUnits * radiusUnits;

        List<GameObject> found = new ArrayList<>();
        for (int sx = 0; sx < planeTiles.length; sx++)
        {
            Tile[] row = planeTiles[sx];
            for (int sy = 0; sy < row.length; sy++)
            {
                Tile tile = row[sy];
                if (tile == null) continue;

                GameObject[] objs = tile.getGameObjects();
                if (objs == null) continue;

                for (GameObject obj : objs)
                {
                    if (obj == null) continue;
                    LocalPoint p = obj.getLocalLocation();
                    if (p == null) continue;
                    long dx = p.getX() - playerLoc.getX();
                    long dy = p.getY() - playerLoc.getY();
                    if (dx * dx + dy * dy > radSq) continue;
                    found.add(obj);
                }
            }
        }

        snapshot = found;
    }

    List<GameObject> get()
    {
        return snapshot;
    }
}
