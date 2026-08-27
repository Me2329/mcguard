package com.mcserver.mcguard.check;

/**
 * The detection categories McGuard ships with.
 *
 * `weight` scales how much a single flag contributes to a player's violation
 * level. Cheap-to-fake, easy-to-false-positive checks (SPEED) carry less weight
 * than checks that are hard to trigger accidentally (NOFALL, NUKER).
 */
public enum CheckType {

    SPEED       ("Speed",       1.0, "horizontal movement beyond the permitted envelope"),
    FLIGHT      ("Flight",      2.0, "sustained airtime without a legitimate cause"),
    NOFALL      ("NoFall",      3.0, "fall damage avoided by spoofing ground contact"),
    INVALID_MOVE("InvalidMove", 2.0, "teleport-like position delta in a single tick"),
    REACH       ("Reach",       2.0, "attack landed beyond vanilla reach"),
    AUTOCLICKER ("AutoClicker", 1.5, "click rate or rhythm inconsistent with a human"),
    FASTBREAK   ("FastBreak",   2.0, "block broken faster than hardness allows"),
    NUKER       ("Nuker",       3.0, "many blocks broken per second across a wide arc"),
    XRAY        ("XRay",        2.5, "valuable-ore mining ratio far above what natural mining produces"),
    KILLAURA    ("KillAura",    2.5, "attacks landed at an angle no legitimate aim could reach"),
    FASTPLACE   ("FastPlace",   1.5, "blocks placed faster than a human can click"),
    NOSLOW      ("NoSlow",      2.0, "moving at full speed while eating, drinking or drawing a bow"),
    TIMER       ("Timer",       2.5, "sending more actions per second than the tick rate allows");

    private final String display;
    private final double weight;
    private final String description;

    CheckType(String display, double weight, String description) {
        this.display = display;
        this.weight = weight;
        this.description = description;
    }

    public String display()     { return display; }
    public double weight()      { return weight; }
    public String description() { return description; }
}
