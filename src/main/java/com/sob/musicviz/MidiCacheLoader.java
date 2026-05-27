package com.sob.musicviz;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

@Slf4j
final class MidiCacheLoader
{
    private static volatile Store cachedStore;
    private static final Object LOCK = new Object();
    private static volatile boolean cacheDirReportFailed = false;

    private MidiCacheLoader() {}

    static byte[] load(int archiveId, boolean jingle)
    {
        try
        {
            Store store = ensureStore();
            if (store == null) return null;
            IndexType type = jingle ? IndexType.MUSIC_JINGLES : IndexType.MUSIC_TRACKS;
            Index music = store.getIndex(type);
            if (music == null)
            {
                log.warn("musicviz: cache has no {} index", type);
                return null;
            }
            Archive archive = music.getArchive(archiveId);
            if (archive == null)
            {
                log.debug("musicviz: archive {} not present in MUSIC index", archiveId);
                return null;
            }
            byte[] raw = store.getStorage().loadArchive(archive);
            if (raw == null) return null;
            return archive.decompress(raw);
        }
        catch (Throwable t)
        {
            log.warn("musicviz: cache load failed for archive {}", archiveId, t);
            return null;
        }
    }

    private static Store ensureStore() throws Exception
    {
        Store s = cachedStore;
        if (s != null) return s;
        synchronized (LOCK)
        {
            if (cachedStore != null) return cachedStore;
            File dir = findCacheDir();
            if (dir == null)
            {
                if (!cacheDirReportFailed)
                {
                    cacheDirReportFailed = true;
                    log.warn("musicviz: could not locate OSRS cache directory");
                }
                return null;
            }
            log.info("musicviz: opening OSRS cache at {}", dir.getAbsolutePath());
            Store created = new Store(dir);
            created.load();
            cachedStore = created;
            return created;
        }
    }

    private static File findCacheDir()
    {
        File home = new File(System.getProperty("user.home"));
        File[] candidates = {
            new File(home, ".runelite/jagexcache/oldschool/LIVE"),
            new File(home, "jagexcache/oldschool/LIVE"),
            new File(home, ".runelite/jagexcache/oldschool/BETA"),
        };
        for (File c : candidates)
        {
            if (new File(c, "main_file_cache.dat2").exists()) return c;
        }
        return null;
    }
}
