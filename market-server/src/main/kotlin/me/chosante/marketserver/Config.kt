package me.chosante.marketserver

object Config {
    val httpPort: Int = System.getenv("MARKET_SERVER_PORT")?.toIntOrNull() ?: 8085

    val dbPath: String =
        System.getenv("MARKET_SERVER_DB_PATH")
            ?: "${System.getenv("LOCALAPPDATA")}\\WakfuMarket\\wakfu.db"
}
