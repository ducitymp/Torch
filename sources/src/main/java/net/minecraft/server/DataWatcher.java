package net.minecraft.server;

import com.google.common.collect.Lists;
import com.koloboke.collect.map.hash.HashObjIntMaps;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap; // Paper
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataWatcher {

    private static final Logger a = LogManager.getLogger();
    private static final Map<Class<? extends Entity>, Integer> b = HashObjIntMaps.newMutableMap();
    private final Entity c;
    private final Map<Integer, DataWatcher.Item<?>> d = new Int2ObjectOpenHashMap<>(); // Paper
    private final ReadWriteLock e = new ReentrantReadWriteLock();
    private boolean f = true;
    private boolean g;

    public DataWatcher(Entity entity) {
        this.c = entity;
    }

    public static <T> DataWatcherObject<T> a(Class<? extends Entity> oclass, DataWatcherSerializer<T> datawatcherserializer) {
        if (DataWatcher.a.isDebugEnabled()) {
            try {
                Class oclass1 = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());

                if (!oclass1.equals(oclass)) {
                    DataWatcher.a.debug("defineId called for: {} from {}", new Object[] { oclass, oclass1, new RuntimeException()});
                }
            } catch (ClassNotFoundException classnotfoundexception) {
                ;
            }
        }

        int i;

        if (DataWatcher.b.containsKey(oclass)) {
            i = DataWatcher.b.get(oclass).intValue() + 1;
        } else {
            int j = 0;
            Class oclass2 = oclass;

            while (oclass2 != Entity.class) {
                oclass2 = oclass2.getSuperclass();
                if (DataWatcher.b.containsKey(oclass2)) {
                    j = DataWatcher.b.get(oclass2).intValue() + 1;
                    break;
                }
            }

            i = j;
        }

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is " + 254 + ")");
        } else {
            DataWatcher.b.put(oclass, Integer.valueOf(i));
            return datawatcherserializer.a(i);
        }
    }

    public <T> void register(DataWatcherObject<T> datawatcherobject, Object t0) { // CraftBukkit T -> Object
        int i = datawatcherobject.a();

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is " + 254 + ")");
        } else if (this.d.containsKey(Integer.valueOf(i))) {
            throw new IllegalArgumentException("Duplicate id value for " + i + "!");
        } else if (DataWatcherRegistry.b(datawatcherobject.b()) < 0) {
            throw new IllegalArgumentException("Unregistered serializer " + datawatcherobject.b() + " for " + i + "!");
        } else {
            this.registerObject(datawatcherobject, t0);
        }
    }

    private <T> void registerObject(DataWatcherObject<T> datawatcherobject, Object t0) { // CraftBukkit Object
        DataWatcher.Item datawatcher_item = new DataWatcher.Item(datawatcherobject, t0);

        this.e.writeLock().lock();
        this.d.put(Integer.valueOf(datawatcherobject.a()), datawatcher_item);
        this.f = false;
        this.e.writeLock().unlock();
    }

    private <T> DataWatcher.Item<T> c(DataWatcherObject<T> datawatcherobject) {
        this.e.readLock().lock();

        DataWatcher.Item datawatcher_item;

        try {
            datawatcher_item = this.d.get(Integer.valueOf(datawatcherobject.a()));
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.a(throwable, "Getting synched entity data");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Synched entity data");

            crashreportsystemdetails.a("Data ID", datawatcherobject);
            throw new ReportedException(crashreport);
        }

        this.e.readLock().unlock();
        return datawatcher_item;
    }

    public <T> T get(DataWatcherObject<T> datawatcherobject) {
        return this.c(datawatcherobject).b();
    }

    public <T> void set(DataWatcherObject<T> datawatcherobject, T t0) {
        DataWatcher.Item datawatcher_item = this.c(datawatcherobject);

        if (ObjectUtils.notEqual(t0, datawatcher_item.b())) {
            datawatcher_item.a(t0);
            this.c.a(datawatcherobject);
            datawatcher_item.a(true);
            this.g = true;
        }

    }

    public <T> void markDirty(DataWatcherObject<T> datawatcherobject) {
        this.c(datawatcherobject).c = true;
        this.g = true;
    }

    public boolean a() {
        return this.g;
    }

    public static void a(List<DataWatcher.Item<?>> list, PacketDataSerializer packetdataserializer) throws IOException {
        if (list != null) {
            int i = 0;

            for (int j = list.size(); i < j; ++i) {
                DataWatcher.Item datawatcher_item = list.get(i);

                a(packetdataserializer, datawatcher_item);
            }
        }

        packetdataserializer.writeByte(255);
    }

    @Nullable
    public List<DataWatcher.Item<?>> b() {
        ArrayList arraylist = null;

        if (this.g) {
            this.e.readLock().lock();
            Iterator iterator = this.d.values().iterator();

            while (iterator.hasNext()) {
                DataWatcher.Item datawatcher_item = (DataWatcher.Item) iterator.next();

                if (datawatcher_item.c()) {
                    datawatcher_item.a(false);
                    if (arraylist == null) {
                        arraylist = Lists.newArrayList();
                    }

                    arraylist.add(datawatcher_item);
                }
            }

            this.e.readLock().unlock();
        }

        this.g = false;
        return arraylist;
    }

    public void a(PacketDataSerializer packetdataserializer) throws IOException {
        this.e.readLock().lock();
        Iterator iterator = this.d.values().iterator();

        while (iterator.hasNext()) {
            DataWatcher.Item datawatcher_item = (DataWatcher.Item) iterator.next();

            a(packetdataserializer, datawatcher_item);
        }

        this.e.readLock().unlock();
        packetdataserializer.writeByte(255);
    }

    @Nullable
    public List<DataWatcher.Item<?>> c() {
        ArrayList arraylist = null;

        this.e.readLock().lock();

        DataWatcher.Item datawatcher_item;

        for (Iterator iterator = this.d.values().iterator(); iterator.hasNext(); arraylist.add(datawatcher_item)) {
            datawatcher_item = (DataWatcher.Item) iterator.next();
            if (arraylist == null) {
                arraylist = Lists.newArrayList();
            }
        }

        this.e.readLock().unlock();
        return arraylist;
    }

    private static <T> void a(PacketDataSerializer packetdataserializer, DataWatcher.Item<T> datawatcher_item) throws IOException {
        DataWatcherObject datawatcherobject = datawatcher_item.a();
        int i = DataWatcherRegistry.b(datawatcherobject.b());

        if (i < 0) {
            throw new EncoderException("Unknown serializer type " + datawatcherobject.b());
        } else {
            packetdataserializer.writeByte(datawatcherobject.a());
            packetdataserializer.d(i);
            datawatcherobject.b().a(packetdataserializer, datawatcher_item.b());
        }
    }

    @Nullable
    public static List<DataWatcher.Item<?>> b(PacketDataSerializer packetdataserializer) throws IOException {
        ArrayList arraylist = null;

        short short0;

        while ((short0 = packetdataserializer.readUnsignedByte()) != 255) {
            if (arraylist == null) {
                arraylist = Lists.newArrayList();
            }

            int i = packetdataserializer.g();
            DataWatcherSerializer datawatcherserializer = DataWatcherRegistry.a(i);

            if (datawatcherserializer == null) {
                throw new DecoderException("Unknown serializer type " + i);
            }

            arraylist.add(new DataWatcher.Item(datawatcherserializer.a(short0), datawatcherserializer.a(packetdataserializer)));
        }

        return arraylist;
    }

    public boolean d() {
        return this.f;
    }

    public void e() {
        this.g = false;
        this.e.readLock().lock();
        Iterator iterator = this.d.values().iterator();

        while (iterator.hasNext()) {
            DataWatcher.Item datawatcher_item = (DataWatcher.Item) iterator.next();

            datawatcher_item.a(false);
        }

        this.e.readLock().unlock();
    }

    public static class Item<T> {

        private final DataWatcherObject<T> a;
        private T b;
        private boolean c;

        public Item(DataWatcherObject<T> datawatcherobject, T t0) {
            this.a = datawatcherobject;
            this.b = t0;
            this.c = true;
        }

        public DataWatcherObject<T> a() {
            return this.a;
        }

        public void a(T t0) {
            this.b = t0;
        }

        public T b() {
            return this.b;
        }

        public boolean c() {
            return this.c;
        }

        public void a(boolean flag) {
            this.c = flag;
        }
    }
}
