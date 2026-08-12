package com.hyperionsoftware.balls.ui

// A pool of plausible-looking usernames so bots read as real opponents in the elimination
// feed instead of "Bot 7".
object BotNames {
    private val POOL = listOf(
        "Marco92", "xXShadowXx", "LucaSpeedy", "GiuliaP", "NightWolf", "ProBaller",
        "Sara_K", "DarkPhoenix", "Tommy2000", "IceQueen", "MaxPower", "AnnaBanana",
        "RedDragon99", "ZorroIT", "MiniPanda", "TheRock21", "FedericaG", "BlazeFire",
        "KevinX", "LunaStar", "AlexWolf", "Giorgio77", "PixelNinja", "SofiaRun",
        "ThunderCat", "DavideM", "CyberFox", "MartinaB", "NoScope99", "RobbyG",
        "SkyWalker22", "ChiaraB", "GhostRider", "EmmaFast", "ViperX", "Leonardo99",
        "StormBreaker", "GiadaK", "BlackHawk", "SimoneT", "FireFly22", "ValentinaR",
        "ShadowClaw", "NicoPrime", "MoonLight7", "ElenaS", "TitanRush", "FabioZ",
        "CrazyDiamond", "BiancaWolf", "JackFlash", "RiccardoM", "SilverArrow", "NoemiV",
        "TurboKid", "AndreaQ", "WildRose", "MatteoX", "FrostByte", "Ilaria99"
    )

    // Shuffled fresh per match so the same bot index doesn't always get the same name, then
    // assigned in order; if there are more bots than pool entries it cycles through again
    // with a numeric suffix rather than repeating a name outright.
    fun generate(botCount: Int): List<String> {
        val shuffled = POOL.shuffled()
        return (0 until botCount).map { index ->
            val base = shuffled[index % shuffled.size]
            val cycle = index / shuffled.size
            if (cycle == 0) base else "$base$cycle"
        }
    }
}
