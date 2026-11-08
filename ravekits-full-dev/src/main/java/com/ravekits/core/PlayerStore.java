package com.ravekits.core;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

final class PlayerStore implements AutoCloseable {
    static final class Profile {
        final UUID uuid;
        int kills, deaths, bestStreak, streak, level=1;
        long xp, coins;
        int pendingRewards, kitKeys, cosmeticKeys;
        final Set<String> cosmetics = new HashSet<>();
        String equippedTag = "";
        final Set<String> ownedKits = new HashSet<>();
        String activeKit = "";
        Profile(UUID uuid){this.uuid=uuid;}
    }
    private final RaveKitsCore plugin;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"RaveKits-SQLite");t.setDaemon(false);return t;});
    private final Map<UUID,Profile> profiles=new ConcurrentHashMap<>();
    private Connection connection;
    PlayerStore(RaveKitsCore plugin){this.plugin=plugin;}
    void open(String file) throws Exception {
        File target=new File(plugin.getDataFolder(),file);
        connection=DriverManager.getConnection("jdbc:sqlite:"+target.getAbsolutePath());
        try(Statement st=connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS profiles (uuid TEXT PRIMARY KEY, kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0, streak INTEGER NOT NULL DEFAULT 0, best_streak INTEGER NOT NULL DEFAULT 0, level INTEGER NOT NULL DEFAULT 1, xp INTEGER NOT NULL DEFAULT 0, coins INTEGER NOT NULL DEFAULT 0)");
            for(String declaration:new String[]{"pending_rewards INTEGER NOT NULL DEFAULT 0","kit_keys INTEGER NOT NULL DEFAULT 0","cosmetic_keys INTEGER NOT NULL DEFAULT 0","owned_kits TEXT NOT NULL DEFAULT ''","cosmetics TEXT NOT NULL DEFAULT ''","equipped_tag TEXT NOT NULL DEFAULT ''"}){
                try {st.execute("ALTER TABLE profiles ADD COLUMN "+declaration);} catch(SQLException ignored) { /* existing column */ }
            }
        }
    }
    Profile get(UUID uuid){return profiles.get(uuid);}
    // Called by the asynchronous login event, not the server main thread.
    void load(UUID uuid) throws Exception {
        Future<Profile> f=worker.submit(()->{
            Profile p=new Profile(uuid);
            try(PreparedStatement st=connection.prepareStatement("SELECT kills,deaths,streak,best_streak,level,xp,coins,pending_rewards,kit_keys,cosmetic_keys,owned_kits,cosmetics,equipped_tag FROM profiles WHERE uuid=?")) {
                st.setString(1,uuid.toString());
                try(ResultSet r=st.executeQuery()) { if(r.next()) {
                    p.kills=r.getInt(1);p.deaths=r.getInt(2);p.streak=r.getInt(3);p.bestStreak=r.getInt(4);
                    p.level=r.getInt(5);p.xp=r.getLong(6);p.coins=r.getLong(7);
                    p.pendingRewards=r.getInt(8);p.kitKeys=r.getInt(9);p.cosmeticKeys=r.getInt(10);
                    String kits=r.getString(11);if(kits!=null&&!kits.isBlank())p.ownedKits.addAll(Arrays.asList(kits.split(",")));
                    String cos=r.getString(12);if(cos!=null&&!cos.isBlank())p.cosmetics.addAll(Arrays.asList(cos.split(",")));p.equippedTag=r.getString(13);
                } }
            }
            profiles.put(uuid,p);return p;
        });
        try { f.get(15,TimeUnit.SECONDS); }
        catch (ExecutionException e) { throw new Exception(e.getCause()); }
    }
    void remove(UUID uuid) { Profile p=profiles.remove(uuid);if(p!=null) queue(p); }
    void saveAll() { for(Profile p:profiles.values()) queue(p); }
    private void queue(Profile p) {
        Snapshot s=new Snapshot(p.uuid,p.kills,p.deaths,p.streak,p.bestStreak,p.level,p.xp,p.coins,p.pendingRewards,p.kitKeys,p.cosmeticKeys,String.join(",",p.ownedKits),String.join(",",p.cosmetics),p.equippedTag);
        worker.submit(()->write(s));
    }
    private record Snapshot(UUID uuid,int kills,int deaths,int streak,int best,int level,long xp,long coins,int pending,int kitKeys,int cosmeticKeys,String kits,String cosmetics,String tag) {}
    private void write(Snapshot s) {
        try(PreparedStatement st=connection.prepareStatement("INSERT INTO profiles(uuid,kills,deaths,streak,best_streak,level,xp,coins,pending_rewards,kit_keys,cosmetic_keys,owned_kits,cosmetics,equipped_tag) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET kills=excluded.kills,deaths=excluded.deaths,streak=excluded.streak,best_streak=excluded.best_streak,level=excluded.level,xp=excluded.xp,coins=excluded.coins,pending_rewards=excluded.pending_rewards,kit_keys=excluded.kit_keys,cosmetic_keys=excluded.cosmetic_keys,owned_kits=excluded.owned_kits,cosmetics=excluded.cosmetics,equipped_tag=excluded.equipped_tag")) {
            st.setString(1,s.uuid().toString());st.setInt(2,s.kills());st.setInt(3,s.deaths());st.setInt(4,s.streak());
            st.setInt(5,s.best());st.setInt(6,s.level());st.setLong(7,s.xp());st.setLong(8,s.coins());st.setInt(9,s.pending());st.setInt(10,s.kitKeys());st.setInt(11,s.cosmeticKeys());st.setString(12,s.kits());st.setString(13,s.cosmetics());st.setString(14,s.tag());st.executeUpdate();
        }catch(SQLException e){plugin.getLogger().log(Level.SEVERE,"Cannot save profile "+s.uuid(),e);}
    }
    @Override public void close() {
        saveAll();worker.shutdown();
        try { if(!worker.awaitTermination(20,TimeUnit.SECONDS)) plugin.getLogger().severe("Timed out flushing SQLite writes"); }
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        try{if(connection!=null)connection.close();}catch(SQLException e){plugin.getLogger().log(Level.SEVERE,"Cannot close SQLite",e);}
    }
}
