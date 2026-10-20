package com.julz.rendererbenchmark.core;

/**
 * The scenes the camera flies through, in order. Scenes that share a place follow each other, so
 * the terrain is already loaded and only a short warm-up is needed between them.
 */
public enum Scenario {
    PLAINS("plains", "Plains", Place.PLAINS, Weather.CLEAR, Time.NOON, false),
    MOBS("mobs", "Lots of mobs", Place.PLAINS, Weather.CLEAR, Time.NOON, true),
    NIGHT("night", "Night", Place.PLAINS, Weather.CLEAR, Time.MIDNIGHT, true),
    FOREST("forest", "Dense forest", Place.FOREST, Weather.CLEAR, Time.NOON, false),
    RAIN("rain", "Rain", Place.FOREST, Weather.RAIN, Time.NOON, false),
    OCEAN("ocean", "Ocean", Place.OCEAN, Weather.CLEAR, Time.NOON, false),
    SNOW("snow", "Snowfall", Place.SNOW, Weather.RAIN, Time.NOON, false);

    /** Where in the world a scene is flown; each is the nearest spot of a kind of biome. */
    public enum Place { PLAINS, FOREST, OCEAN, SNOW }

    /** Rain falls as snow in snowy biomes. */
    public enum Weather { CLEAR, RAIN }

    public enum Time { NOON, MIDNIGHT }

    private final String id;
    private final String englishName;
    private final Place place;
    private final Weather weather;
    private final Time time;
    private final boolean mobs;

    Scenario(String id, String englishName, Place place, Weather weather, Time time, boolean mobs) {
        this.id = id;
        this.englishName = englishName;
        this.place = place;
        this.weather = weather;
        this.time = time;
        this.mobs = mobs;
    }

    public String id() {
        return id;
    }

    /** For the share text, which is always in English. */
    public String englishName() {
        return englishName;
    }

    public Place place() {
        return place;
    }

    public Weather weather() {
        return weather;
    }

    public Time time() {
        return time;
    }

    /** Whether the herd of animals spawned for {@link #MOBS} is around. */
    public boolean hasMobs() {
        return mobs;
    }

    /** True for the first scene at a place, which needs a longer warm-up while the terrain loads. */
    public boolean movesToNewPlace() {
        return ordinal() == 0 || values()[ordinal() - 1].place != place;
    }

    public static Scenario byId(String id) {
        for (Scenario scenario : values()) {
            if (scenario.id.equals(id)) {
                return scenario;
            }
        }
        return null;
    }
}
