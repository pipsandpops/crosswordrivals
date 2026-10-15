package com.wordrivals.game;

/** Picks the real store when Google's libraries are in the build, otherwise the demo store. */
final class MonetizerFactory {
    private MonetizerFactory() { }

    static Monetizer create() {
        try {
            return (Monetizer) Class.forName("com.wordrivals.game.PlayMonetizer").newInstance();
        } catch (ClassNotFoundException e) {
            return new DemoMonetizer();              // only in test builds made without Google's libraries
        } catch (Throwable t) {
            return new Monetizer.None();             // the real store is in the build but cannot start on this device
        }
    }
}
