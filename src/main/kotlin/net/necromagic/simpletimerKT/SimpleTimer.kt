package net.necromagic.simpletimerKT

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.necromagic.simpletimerKT.bcdice.BCDiceManager
import net.necromagic.simpletimerKT.command.CommandManager
import net.necromagic.simpletimerKT.listener.GenericMessageReaction
import net.necromagic.simpletimerKT.listener.GuildMessageReceived
import net.necromagic.simpletimerKT.listener.MessageDelete
import net.necromagic.simpletimerKT.listener.SlashCommand
import net.necromagic.simpletimerKT.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.URL
import javax.security.auth.login.LoginException

// v1.0.0 リリース
// v1.0.1 リアクションによる操作の実装
// v1.0.2 メッセージ消去時のエラーを修正
// v1.0.3 権限がないときにDMへの通知を行うように
// v1.0.4 並列処理の見直し
// v1.1.0 メインメッセージ消去時の反応を調整
// v1.1.1 リアクションに1分延長を追加
// v1.2.0 メンションの方式の変更
// v1.2.1 エラーメッセージのバグを修正。誤字の修正。
// v1.2.2 vcにいるbotへのメンションをしないように変更。
// v1.2.3 メンションのデフォルトをHere->VC
// v1.2.4 1番タイマーのデザインを変更
// v1.2.5 !!timerでも反応をするように変更
// v1.2.6 すべてのコードをJava->Kotlinに書き直し
// v1.2.7 無駄な改行が入っていたのを修正
// v1.2.8 隠し機能：ダイスの実装
// v1.2.9 Prefixの変更機能を実装
// v1.2.10 一部コードの見直し
// v1.2.11 並列処理の見直しその2
// v1.3.0 APIの更新 通知の送信を返信で行うように変更
// v1.3.1 メッセージのメンションの位置を下に移動
// v1.3.2 一部負荷をかけることができる動作を制限
// v1.3.3 スポイラーされたダイスをシークレットダイスに
// v1.3.3 ダイスボットをBCDiceに仮対応
// v1.4.0 スラッシュコマンドに対応
// v1.4.1 BCDiceの使用時は、スペース後を無視するように
// v1.4.2 プレフィックスが変更できなくなるバグの修正
// v1.4.3 タイマーのTTS方式を変更
// v1.4.4 誤字の修正
// v1.4.5 JDAのバージョンを更新
// v1.4.6 スラッシュコマンドの正式実装
// v1.4.7 時間をより正確に調整
// v1.4.8 一時停止が一部動いていなかったのを修正
// v1.4.9 ダイスのInfoの文字数が多いと表示されなかったのを修正
// v1.4.10 tokenを外部ファイル(server_config.yml)にて記述するように変更
// v1,4,11

/**
 * メインクラス
 *
 */
class SimpleTimer {
    companion object {
        lateinit var instance: SimpleTimer

        /**
         * すべての始まり
         *
         * @param args [Array] 引数（未使用）
         */
        @JvmStatic
        fun main(args: Array<String>) {
            SimpleTimer()
        }
    }

    //バージョン
    val version = "v1.4.11"

    //多重起動防止
    private val lockPort = 918
    private lateinit var socket: ServerSocket

    //ShardManager
    private lateinit var shardManager: ShardManager

    //データ保存用
    lateinit var config: ServerConfig

    //コマンド管理
    lateinit var commandManager: CommandManager

    init {
        init()
    }

    /**
     * 起動時処理
     *
     */
    private fun init() {
        //インターネットの接続の確認
        try {
            val url = URL("https://google.com")
            val connection = url.openConnection()
            connection.getInputStream().close()
        } catch (ioException: IOException) {
            try {
                Thread.sleep(5000)
                init()
            } catch (interruptedException: InterruptedException) {
                interruptedException.printStackTrace()
            }
            return
        }

        //多重起動の防止
        var check = true
        try {
            socket = ServerSocket(lockPort)
        } catch (e: Exception) {
            check = false
        }
        if (!check) {
            return
        }

        //インスタンスを代入
        instance = this

        //コンフィグを生成
        config = ServerConfig()
        config.save()

        //コマンドのクラス
        commandManager = CommandManager()

        //Tokenを取得
        val token = config.getString("TOKEN", "TOKEN IS HERE")

        //トークンがないときに終了する
        if (token.equals("TOKEN IS HERE", ignoreCase = true)) {
            //初期値を保存
            config.set("TOKEN", "TOKEN IS HERE")
            config.save()
            //コンソールに出力
            println("SETUP: Write the token in the \"TOKEN\" field of server_config.yml")
            return
        }

        //JDAを作成
        val jda: JDA?
        val jdaBuilder = JDABuilder.createDefault(token)
        val shardManagerBuilder = DefaultShardManagerBuilder.createDefault(token)
        try {
            jda = jdaBuilder.build()
            shardManager = shardManagerBuilder.build()
        } catch (e: LoginException) {
            e.printStackTrace()
            return
        }

        //リスナーの登録
        shardManager.addEventListener(GuildMessageReceived())
        shardManager.addEventListener(GenericMessageReaction())
        shardManager.addEventListener(MessageDelete())
        shardManager.addEventListener(SlashCommand())

        //コマンドの登録
        val commands = jda.updateCommands()
        commands.addCommands(commandManager.commands)
        commands.complete()

        //ステータスの変更
        val presence = jda.presence
        presence.setStatus(OnlineStatus.ONLINE)
        presence.activity = Activity.of(Activity.ActivityType.DEFAULT, "!timerでヘルプ表示")

        //BCDiceのマネージャーを開始
        BCDiceManager()

        //ログ
        val section = config.getConfigurationSection("LoggingServer")
        section?.getKeys(false)?.forEach { guildID ->
            val guild = jda.getGuildById(guildID)
            val channel = guild?.getTextChannelById(config.getString("LoggingServer.${guildID}"))
            if (channel != null) {
                Log.logChannels.add(channel)
            }
        }
    }
}