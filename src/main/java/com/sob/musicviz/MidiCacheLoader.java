package com.sob.musicviz;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;

/**
 * Reads raw OSRS music bytes by archive id, using the running RuneLite client's
 * own cache accessor (Client.getIndex). The bytes returned are in Jagex's
 * packed format and must be passed through {@link com.sob.musicviz.vendored.TrackLoader}
 * to be converted into standard SMF.
 *
 * Index numbers come from the OSRS cache layout: 6 = music tracks, 11 = jingles.
 */
@Slf4j
@Singleton
final class MidiCacheLoader
{
    private static final int INDEX_MUSIC_TRACKS = 6;
    private static final int INDEX_MUSIC_JINGLES = 11;

    private final Client client;

    @Inject
    MidiCacheLoader(Client client)
    {
        this.client = client;
    }

    byte[] load(int archiveId, boolean jingle)
    {
        try
        {
            int indexId = jingle ? INDEX_MUSIC_JINGLES : INDEX_MUSIC_TRACKS;
            IndexDataBase index = client.getIndex(indexId);
            if (index == null)
            {
                log.warn("musicviz: client returned null for index {}", indexId);
                return null;
            }
            byte[] bytes = index.loadData(archiveId, 0);
            if (bytes == null || bytes.length == 0)
            {
                log.debug("musicviz: index {} archive {} returned no bytes", indexId, archiveId);
                return null;
            }
            return bytes;
        }
        catch (Throwable t)
        {
            log.warn("musicviz: failed to load archive {} (jingle={})", archiveId, jingle, t);
            return null;
        }
    }
}
